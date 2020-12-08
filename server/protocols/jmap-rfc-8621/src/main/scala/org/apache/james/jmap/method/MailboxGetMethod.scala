/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.method

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.State.INSTANCE
import org.apache.james.jmap.core.{AccountId, CapabilityIdentifier, ErrorCode, Invocation, Properties}
import org.apache.james.jmap.http.MailboxesProvisioner
import org.apache.james.jmap.json.{MailboxSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.MailboxGet.UnparsedMailboxId
import org.apache.james.jmap.mail.{Mailbox, MailboxFactory, MailboxGet, MailboxGetRequest, MailboxGetResponse, NotFound, PersonalNamespace, Subscriptions}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.jmap.utils.quotas.{QuotaLoaderWithPreloadedDefault, QuotaLoaderWithPreloadedDefaultFactory}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxId, MailboxMetaData}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._
import scala.util.Try

object MailboxGetResults {
  def merge(result1: MailboxGetResults, result2: MailboxGetResults): MailboxGetResults = result1.merge(result2)
  def empty(): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set.empty))
  def found(mailbox: Mailbox): MailboxGetResults = MailboxGetResults(Set(mailbox), NotFound(Set.empty))
  def notFound(mailboxId: UnparsedMailboxId): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set(mailboxId)))
  def notFound(mailboxId: MailboxId): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set(MailboxGet.asUnparsed(mailboxId))))
}

case class MailboxGetResults(mailboxes: Set[Mailbox], notFound: NotFound) {
  def merge(other: MailboxGetResults): MailboxGetResults = MailboxGetResults(this.mailboxes ++ other.mailboxes, this.notFound.merge(other.notFound))

  def asResponse(accountId: AccountId): MailboxGetResponse = MailboxGetResponse(
    accountId = accountId,
    state = INSTANCE,
    list = mailboxes.toList.sortBy(_.sortOrder),
    notFound = notFound)
}

class MailboxGetMethod @Inject() (serializer: MailboxSerializer,
                                  mailboxManager: MailboxManager,
                                  subscriptionManager: SubscriptionManager,
                                  quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                  mailboxIdFactory: MailboxId.Factory,
                                  mailboxFactory: MailboxFactory,
                                  provisioner: MailboxesProvisioner,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[MailboxGetRequest] {
  override val methodName: MethodName = MethodName("Mailbox/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxGetRequest): SMono[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(Mailbox.allProperties)
    (requestedProperties -- Mailbox.allProperties match {
      case invalidProperties if invalidProperties.isEmpty() => getMailboxes(capabilities, request, mailboxSession)
        .reduce(MailboxGetResults.empty(), MailboxGetResults.merge)
        .map(mailboxes => mailboxes.asResponse(request.accountId))
        .map(mailboxGetResponse => Invocation(
          methodName = methodName,
          arguments = Arguments(serializer.serialize(mailboxGetResponse, requestedProperties, capabilities).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId))
      case invalidProperties: Properties =>
        SMono.just(Invocation.error(errorCode = ErrorCode.InvalidArguments,
          description = s"The following properties [${invalidProperties.format()}] do not exist.",
          methodCallId = invocation.invocation.methodCallId))
    }).map(InvocationWithContext(_, invocation.processingContext))

  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, MailboxGetRequest] =
    serializer.deserializeMailboxGetRequest(invocation.arguments.value) match {
    case JsSuccess(mailboxGetRequest, _) => Right(mailboxGetRequest)
    case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
  }

  private def getMailboxes(capabilities: Set[CapabilityIdentifier],
                           mailboxGetRequest: MailboxGetRequest,
                           mailboxSession: MailboxSession): SFlux[MailboxGetResults] =
    provisioner.createMailboxesIfNeeded(mailboxSession)
      .thenMany(
        mailboxGetRequest.ids match {
          case None => getAllMailboxes(capabilities, mailboxSession)
            .map(MailboxGetResults.found)
          case Some(ids) => SFlux.fromIterable(ids.value)
            .flatMap(id => Try(mailboxIdFactory.fromString(id.value))
              .fold(e => SMono.just(MailboxGetResults.notFound(id)),
                mailboxId => getMailboxResultById(capabilities, mailboxId, mailboxSession)),
              maxConcurrency = 5)
        })

  private def getMailboxResultById(capabilities: Set[CapabilityIdentifier],
                                   mailboxId: MailboxId,
                                   mailboxSession: MailboxSession): SMono[MailboxGetResults] =
    quotaFactory.loadFor(mailboxSession)
      .flatMap(quotaLoader => mailboxFactory.create(mailboxId, mailboxSession, quotaLoader)
        .map(mailbox => filterShared(capabilities, mailbox))
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.just(MailboxGetResults.notFound(mailboxId))
          case error => SMono.raiseError(error)
        })
      .subscribeOn(Schedulers.elastic)

  private def filterShared(capabilities: Set[CapabilityIdentifier], mailbox: Mailbox): MailboxGetResults = {
    if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
      MailboxGetResults.found(mailbox)
    } else {
      mailbox.namespace match {
        case _: PersonalNamespace => MailboxGetResults.found(mailbox)
        case _ => MailboxGetResults.notFound(mailbox.id)
      }
    }
  }

  private def getAllMailboxes(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SFlux[Mailbox] = {
    val subscriptions: SMono[Subscriptions] = SMono.fromCallable(() =>
      Subscriptions(subscriptionManager.subscriptions(mailboxSession).asScala.toSet))

    SMono.zip(array => (array(0).asInstanceOf[Seq[MailboxMetaData]],
          array(1).asInstanceOf[QuotaLoaderWithPreloadedDefault],
          array(2).asInstanceOf[Subscriptions]),
        getAllMailboxesMetaData(capabilities, mailboxSession),
        quotaFactory.loadFor(mailboxSession),
        subscriptions)
      .subscribeOn(Schedulers.elastic)
      .flatMapMany {
        case (mailboxes, quotaLoader, subscriptions) => SFlux.fromIterable(mailboxes)
          .flatMap(mailbox => mailboxFactory.create(mailboxMetaData = mailbox,
            mailboxSession = mailboxSession,
            subscriptions = subscriptions,
            allMailboxesMetadata = mailboxes,
            quotaLoader = quotaLoader))
      }
  }

  private def getAllMailboxesMetaData(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[Seq[MailboxMetaData]] =
      SFlux.fromPublisher(mailboxManager.search(
          mailboxQuery(capabilities, mailboxSession),
          mailboxSession))
        .collectSeq()

  private def mailboxQuery(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession) =
    if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
      MailboxQuery.builder
        .matchesAllMailboxNames
        .build
    } else {
      MailboxQuery.builder
        .privateNamespace()
        .user(mailboxSession.getUser)
        .build
    }
}
