/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.Email.UnparsedEmailId
import org.apache.james.jmap.mail.{Email, EmailGetRequest, EmailGetResponse, EmailIds, EmailNotFound}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.State.INSTANCE
import org.apache.james.jmap.model.{AccountId, ErrorCode, Invocation, Properties}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.model.{FetchGroup, MessageId}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object EmailGetResults {
  private val logger: Logger = LoggerFactory.getLogger(classOf[EmailGetResults])

  def merge(result1: EmailGetResults, result2: EmailGetResults): EmailGetResults = result1.merge(result2)
  def empty(): EmailGetResults = EmailGetResults(Set.empty, EmailNotFound(Set.empty))
  def found(email: Email): EmailGetResults = EmailGetResults(Set(email), EmailNotFound(Set.empty))
  def notFound(emailId: UnparsedEmailId): EmailGetResults = EmailGetResults(Set.empty, EmailNotFound(Set(emailId)))
  def notFound(messageId: MessageId): EmailGetResults = Email.asUnparsed(messageId)
    .fold(e => {
        logger.error("messageId is not a valid UnparsedEmailId", e)
        empty()
      },
      id => notFound(id))
}

case class EmailGetResults(emails: Set[Email], notFound: EmailNotFound) {
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

class EmailGetMethod @Inject() (serializer: Serializer,
                               messageIdManager: MessageIdManager,
                               messageIdFactory: MessageId.Factory,
                               metricFactory: MetricFactory) extends Method {
  override val methodName = MethodName("Email/get")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): Publisher[(Invocation, ProcessingContext)] =
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asEmailGetRequest(invocation.arguments)
        .flatMap(computeResponseInvocation(_, invocation, mailboxSession))
        .onErrorResume({
          case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.methodCallId))
          case e: Throwable => SMono.raiseError(e)
        })
        .map(invocationResult => (invocationResult, processingContext)))

  private def computeResponseInvocation(request: EmailGetRequest, invocation: Invocation, mailboxSession: MailboxSession): SMono[Invocation] =
    validateProperties(request)
        .fold(
          e => SMono.raiseError(e),
          properties => getEmails(request, mailboxSession)
            .map(response => Invocation(
              methodName = methodName,
              arguments = Arguments(serializer.serialize(response, properties).as[JsObject]),
              methodCallId = invocation.methodCallId)))


  private def validateProperties(request: EmailGetRequest): Either[IllegalArgumentException, Properties] =
    request.properties match {
      case None => Right(Email.defaultProperties)
      case Some(properties) =>
        val invalidProperties = properties -- Email.allowedProperties
        if (invalidProperties.isEmpty()) {
          Right(properties ++ Email.idProperty)
        } else {
          Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] do not exist."))
        }
    }

  private def asEmailGetRequest(arguments: Arguments): SMono[EmailGetRequest] =
    serializer.deserializeEmailGetRequest(arguments.value) match {
      case JsSuccess(emailGetRequest, _) => SMono.just(emailGetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }

  private def getEmails(request: EmailGetRequest, mailboxSession: MailboxSession): SMono[EmailGetResponse] =
    request.ids match {
      case None => SMono.raiseError(new IllegalArgumentException("ids can not be ommited for email/get"))
      case Some(ids) => getEmails(ids, mailboxSession)
        .map(result => EmailGetResponse(
          accountId = request.accountId,
          state = INSTANCE,
          list = result.emails.toList,
          notFound = result.notFound))
    }

  private def getEmails(ids: EmailIds, mailboxSession: MailboxSession): SMono[EmailGetResults] = {
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

    SFlux.merge(Seq(retrieveEmails(messagesIds, mailboxSession), parsingErrors))
      .reduce(EmailGetResults.empty(), EmailGetResults.merge)
  }

  private def asMessageId(id: UnparsedEmailId): Either[(UnparsedEmailId, IllegalArgumentException),  MessageId] =
    try {
      Right(messageIdFactory.fromString(id))
    } catch {
      case e: Exception => Left((id, new IllegalArgumentException(e)))
    }

  private def retrieveEmails(ids: Seq[MessageId], mailboxSession: MailboxSession): SFlux[EmailGetResults] = {
    val foundResultsMono: SMono[Map[MessageId, Email]] = SFlux.fromPublisher(messageIdManager.getMessagesReactive(ids.toList.asJava, FetchGroup.MINIMAL, mailboxSession))
      .groupBy(_.getMessageId)
      .flatMap(groupedFlux => groupedFlux.collectSeq().map(results => (groupedFlux.key(), results)))
      .map(Email.from)
      .collectMap(_.id)

    foundResultsMono.flatMapMany(foundResults => SFlux.fromIterable(ids)
      .map(id => foundResults.get(id)
        .map(EmailGetResults.found)
        .getOrElse(EmailGetResults.notFound(id))))
  }
}