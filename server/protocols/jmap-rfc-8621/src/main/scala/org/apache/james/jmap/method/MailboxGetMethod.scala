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
import org.apache.james.jmap.api.change.MailboxChangeRepository
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, CapabilityIdentifier, ErrorCode, Invocation, Properties, SessionTranslator, UuidState}
import org.apache.james.jmap.http.MailboxesProvisioner
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{Ids, Mailbox, MailboxFactory, MailboxGet, MailboxGetRequest, MailboxGetResponse, NotFound, PersonalNamespace, Subscriptions, UnparsedMailboxId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.jmap.utils.quotas.{QuotaLoaderWithPreloadedDefault, QuotaLoaderWithPreloadedDefaultFactory}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxId, MailboxMetaData, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

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

  def asResponse(accountId: AccountId, state: UuidState): MailboxGetResponse = MailboxGetResponse(
    accountId = accountId,
    state = state,
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
                                  mailboxChangeRepository: MailboxChangeRepository,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier,
                                  val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[MailboxGetRequest] {
  override val methodName: MethodName = MethodName("Mailbox/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxGetRequest): SMono[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(Mailbox.allProperties)
    (requestedProperties -- Mailbox.allProperties match {
      case invalidProperties if invalidProperties.isEmpty() => getMailboxes(capabilities, request, mailboxSession)
        .reduce(MailboxGetResults.empty())(MailboxGetResults.merge)
        .flatMap(mailboxes => retrieveState(capabilities, mailboxSession)
          .map(state => mailboxes.asResponse(request.accountId, state)))
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

  private def retrieveState(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[UuidState] =
    if (capabilities.contains(JAMES_SHARES)) {
      SMono(mailboxChangeRepository.getLatestStateWithDelegation(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    } else {
      SMono(mailboxChangeRepository.getLatestState(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, MailboxGetRequest] =
    serializer.deserializeMailboxGetRequest(invocation.arguments.value).asEitherRequest
  private def getMailboxes(capabilities: Set[CapabilityIdentifier],
                           mailboxGetRequest: MailboxGetRequest,
                           mailboxSession: MailboxSession): SFlux[MailboxGetResults] =
    mailboxGetRequest.ids match {
      case None => provisioner.createMailboxesIfNeeded(mailboxSession)
        .thenMany(getAllMailboxes(capabilities, mailboxSession))
        .map(MailboxGetResults.found)
      case Some(Ids.EMPTY) => SFlux.empty
      case Some(ids) =>
        SMono.zip(array => (array(0).asInstanceOf[QuotaLoaderWithPreloadedDefault],
          array(1).asInstanceOf[Subscriptions]),
          quotaFactory.loadFor(mailboxSession),
          retrieveSubscriptions(mailboxSession))
          .flatMapMany {
            case (quotaLoader, subscriptions) => SFlux.fromIterable(ids.value)
              .flatMap(id => Try(mailboxIdFactory.fromString(id.id))
                .fold(e => SMono.just(MailboxGetResults.notFound(id)),
                  mailboxId => getMailboxResultById(capabilities, mailboxId, subscriptions, quotaLoader, mailboxSession)),
                maxConcurrency = 5)
          }
    }

  private def retrieveSubscriptions(mailboxSession: MailboxSession): SMono[Subscriptions] =
    SFlux(subscriptionManager.subscriptionsReactive(mailboxSession))
      .collectSeq()
      .map(seq => Subscriptions(seq.toSet))

  private def getMailboxResultById(capabilities: Set[CapabilityIdentifier],
                                   mailboxId: MailboxId,
                                   subscriptions: Subscriptions,
                                   quotaLoader: QuotaLoaderWithPreloadedDefault,
                                   mailboxSession: MailboxSession): SMono[MailboxGetResults] =
    mailboxFactory.create(mailboxId, mailboxSession, quotaLoader, subscriptions)
      .map(mailbox => filterShared(capabilities, mailbox))
      .onErrorResume {
        case _: MailboxNotFoundException => SMono.just(MailboxGetResults.notFound(mailboxId))
        case error => SMono.error(error)
      }

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

  private def getAllMailboxes(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SFlux[Mailbox] =
    SMono.zip(array => (array(0).asInstanceOf[Map[MailboxPath, MailboxMetaData]],
          array(1).asInstanceOf[QuotaLoaderWithPreloadedDefault],
          array(2).asInstanceOf[Subscriptions]),
        getAllMailboxesMetaData(capabilities, mailboxSession),
        quotaFactory.loadFor(mailboxSession),
        retrieveSubscriptions(mailboxSession))
      .flatMapMany {
        case (mailboxes, quotaLoader, subscriptions) => SFlux.fromIterable(mailboxes.values)
          .flatMap(mailbox => mailboxFactory.create(mailboxMetaData = mailbox,
            mailboxSession = mailboxSession,
            subscriptions = subscriptions,
            allMailboxesMetadata = mailboxes,
            quotaLoader = quotaLoader))
      }

  private def getAllMailboxesMetaData(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[Map[MailboxPath, MailboxMetaData]] =
      SFlux.fromPublisher(mailboxManager.search(
          mailboxQuery(capabilities, mailboxSession),
          mailboxSession))
        .collectMap(_.getPath)

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
