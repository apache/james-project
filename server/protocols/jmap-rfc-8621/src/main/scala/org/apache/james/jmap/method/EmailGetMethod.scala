/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.method

import java.time.ZoneId

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.api.change.{EmailChangeRepository, State => JavaState}
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.core.{AccountId, ErrorCode, Invocation, JmapRfc8621Configuration, SessionTranslator, UuidState}
import org.apache.james.jmap.json.EmailGetSerializer
import org.apache.james.jmap.mail.{Email, EmailGetRequest, EmailGetResponse, EmailIds, EmailNotFound, EmailView, EmailViewReaderFactory, FullReadLevel, MetadataReadLevel, ReadLevel, UnparsedEmailId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MessageId
import org.apache.james.metrics.api.MetricFactory
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

object EmailGetResults {
  private val logger: Logger = LoggerFactory.getLogger(classOf[EmailGetResults])

  def merge(result1: EmailGetResults, result2: EmailGetResults): EmailGetResults = result1.merge(result2)
  def empty(): EmailGetResults = EmailGetResults(Set.empty, EmailNotFound(Set.empty))
  def found(email: EmailView): EmailGetResults = EmailGetResults(Set(email), EmailNotFound(Set.empty))
  def notFound(emailId: UnparsedEmailId): EmailGetResults = EmailGetResults(Set.empty, EmailNotFound(Set(emailId)))
  def notFound(messageId: MessageId): EmailGetResults = Email.asUnparsed(messageId)
    .fold(e => {
        logger.error("messageId is not a valid UnparsedEmailId", e)
        empty()
      },
      id => notFound(id))
}

case class EmailGetResults(emails: Set[EmailView], notFound: EmailNotFound) {
  def merge(other: EmailGetResults): EmailGetResults = EmailGetResults(this.emails ++ other.emails, this.notFound.merge(other.notFound))

  def asResponse(accountId: AccountId): EmailGetResponse = EmailGetResponse(
    accountId = accountId,
    state = INSTANCE,
    list = emails.toList,
    notFound = notFound)
}

object EmailGetMethod {
  private val logger: Logger = LoggerFactory.getLogger(classOf[EmailGetMethod])
}

trait ZoneIdProvider {
  def get(): ZoneId
}

class SystemZoneIdProvider extends ZoneIdProvider {
  override def get(): ZoneId = ZoneId.systemDefault()
}

class EmailGetMethod @Inject() (readerFactory: EmailViewReaderFactory,
                                messageIdFactory: MessageId.Factory,
                                val metricFactory: MetricFactory,
                                val emailchangeRepository: EmailChangeRepository,
                                val sessionSupplier: SessionSupplier,
                                val configuration: JmapRfc8621Configuration,
                                val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[EmailGetRequest] {
  override val methodName: MethodName = MethodName("Email/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailGetRequest): SMono[InvocationWithContext] = {
    computeResponseInvocation(capabilities, request, invocation.invocation, mailboxSession).onErrorResume({
      case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.invocation.methodCallId))
      case e: Throwable => SMono.error(e)
    }).map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, EmailGetRequest] =
    EmailGetSerializer.deserializeEmailGetRequest(invocation.arguments.value).asEitherRequest

  private def computeResponseInvocation(capabilities: Set[CapabilityIdentifier], request: EmailGetRequest, invocation: Invocation, mailboxSession: MailboxSession): SMono[Invocation] =
    Email.validateProperties(request.properties)
      .flatMap(properties => Email.validateBodyProperties(request.bodyProperties)
        .flatMap(validatedBodyProperties => Email.validateIdsSize(request, configuration.jmapEmailGetFullMaxSize.asLong(), validatedBodyProperties))
        .map((properties, _)))
      .fold(
        e => SMono.error(e), {
          case (properties, bodyProperties) => getEmails(capabilities, request, mailboxSession)
            .map(response => Invocation(
              methodName = methodName,
              arguments = Arguments(EmailGetSerializer.serialize(response, properties, bodyProperties).as[JsObject]),
              methodCallId = invocation.methodCallId))
        })

  private def getEmails(capabilities: Set[CapabilityIdentifier], request: EmailGetRequest, mailboxSession: MailboxSession): SMono[EmailGetResponse] =
    request.ids match {
      case None => SMono.error(new IllegalArgumentException("ids can not be ommited for email/get"))
      case Some(ids) => getEmails(ids, mailboxSession, request)
        .flatMap(result => retrieveState(capabilities, mailboxSession)
          .map(state => EmailGetResponse(
            accountId = request.accountId,
            state = UuidState.fromJava(state),
            list = result.emails.toList,
            notFound = result.notFound)))
    }

  private def retrieveState(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[JavaState] = {
    val accountId: JavaAccountId = JavaAccountId.fromUsername(mailboxSession.getUser)
    if (capabilities.contains(JAMES_SHARES)) {
      SMono[JavaState](emailchangeRepository.getLatestStateWithDelegation(accountId))
    } else {
      SMono[JavaState](emailchangeRepository.getLatestState(accountId))
    }
  }

  private def getEmails(ids: EmailIds, mailboxSession: MailboxSession, request: EmailGetRequest): SMono[EmailGetResults] = {
    val parsedIds: List[Either[(UnparsedEmailId, IllegalArgumentException),  MessageId]] = ids.value
      .map(asMessageId)
    val messagesIds: List[MessageId] = parsedIds.flatMap({
      case Left(_) => None
      case Right(messageId) => Some(messageId)
    })
    val parsingErrors: SFlux[EmailGetResults] = SFlux.fromIterable(parsedIds.flatMap({
      case Left((id, error)) =>
        EmailGetMethod.logger.warn(s"id parsing failed", error)
        Some(EmailGetResults.notFound(id))
      case Right(_) => None
    }))

    SFlux.merge(Seq(retrieveEmails(messagesIds, mailboxSession, request), parsingErrors))
      .reduce(EmailGetResults.empty())(EmailGetResults.merge)
  }

  private def asMessageId(id: UnparsedEmailId): Either[(UnparsedEmailId, IllegalArgumentException),  MessageId] =
    try {
      Right(messageIdFactory.fromString(id.id))
    } catch {
      case e: Exception => Left((id, new IllegalArgumentException(e)))
    }

  private def retrieveEmails(ids: Seq[MessageId], mailboxSession: MailboxSession, request: EmailGetRequest): SFlux[EmailGetResults] = {
      val foundResultsMono: SMono[Map[MessageId, EmailView]] =
        readerFactory.selectReader(request)
          .read(ids, request, mailboxSession)
          .collectMap(_.metadata.id)

      foundResultsMono.flatMapIterable(foundResults => ids
        .map(id => foundResults.get(id)
          .map(EmailGetResults.found)
          .getOrElse(EmailGetResults.notFound(id))))
  }
}