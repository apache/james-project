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
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.MailboxSetRequest.UnparsedMailboxId
import org.apache.james.jmap.mail._
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.State.INSTANCE
import org.apache.james.jmap.model.{AccountId, Capabilities, CapabilityIdentifier, ErrorCode, Invocation, MailboxFactory, Properties, Subscriptions}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.jmap.utils.quotas.{QuotaLoader, QuotaLoaderWithPreloadedDefaultFactory}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxId, MailboxMetaData}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

class MailboxGetMethod @Inject() (serializer: Serializer,
                                  mailboxManager: MailboxManager,
                                  subscriptionManager: SubscriptionManager,
                                  quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                  mailboxIdFactory: MailboxId.Factory,
                                  mailboxFactory: MailboxFactory,
                                  metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("Mailbox/get")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  object MailboxGetResults {
    def merge(result1: MailboxGetResults, result2: MailboxGetResults): MailboxGetResults = result1.merge(result2)
    def empty(): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set.empty))
    def found(mailbox: Mailbox): MailboxGetResults = MailboxGetResults(Set(mailbox), NotFound(Set.empty))
    def notFound(mailboxId: UnparsedMailboxId): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set(mailboxId)))
    def notFound(mailboxId: MailboxId): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set(MailboxSetRequest.asUnparsed(mailboxId))))
  }

  case class MailboxGetResults(mailboxes: Set[Mailbox], notFound: NotFound) {
    def merge(other: MailboxGetResults): MailboxGetResults = MailboxGetResults(this.mailboxes ++ other.mailboxes, this.notFound.merge(other.notFound))

    def asResponse(accountId: AccountId): MailboxGetResponse = MailboxGetResponse(
      accountId = accountId,
      state = INSTANCE,
      list = mailboxes.toList.sortBy(_.sortOrder),
      notFound = notFound)
  }

  override def process(capabilities: Set[CapabilityIdentifier],
                       invocation: Invocation,
                       mailboxSession: MailboxSession,
                       processingContext: ProcessingContext): Publisher[(Invocation, ProcessingContext)] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asMailboxGetRequest(invocation.arguments)
        .flatMap(mailboxGetRequest => {
          val requestedProperties: Properties = mailboxGetRequest.properties.getOrElse(Mailbox.allProperties)
          requestedProperties -- Mailbox.allProperties match {
            case invalidProperties if invalidProperties.isEmpty() => getMailboxes(capabilities, mailboxGetRequest, processingContext, mailboxSession)
              .reduce(MailboxGetResults.empty(), MailboxGetResults.merge)
              .map(mailboxes => mailboxes.asResponse(mailboxGetRequest.accountId))
              .map(mailboxGetResponse => Invocation(
                methodName = methodName,
                arguments = Arguments(serializer.serialize(mailboxGetResponse, requestedProperties, capabilities).as[JsObject]),
                methodCallId = invocation.methodCallId))
            case invalidProperties: Properties =>
              SMono.just(Invocation.error(errorCode = ErrorCode.InvalidArguments,
                description = s"The following properties [${invalidProperties.format()}] do not exist.",
                methodCallId = invocation.methodCallId))
          }
        })
        .map((_, processingContext)))
  }

  private def asMailboxGetRequest(arguments: Arguments): SMono[MailboxGetRequest] = {
    serializer.deserializeMailboxGetRequest(arguments.value) match {
      case JsSuccess(mailboxGetRequest, _) => SMono.just(mailboxGetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }

  private def getMailboxes(capabilities: Set[CapabilityIdentifier],
                           mailboxGetRequest: MailboxGetRequest,
                           processingContext: ProcessingContext,
                           mailboxSession: MailboxSession): SFlux[MailboxGetResults] =

    mailboxGetRequest.ids match {
      case None => getAllMailboxes(capabilities, mailboxSession)
        .map(MailboxGetResults.found)
      case Some(ids) => SFlux.fromIterable(ids.value)
          .flatMap(id => processingContext.resolveMailboxId(id, mailboxIdFactory)
            .fold(e => SMono.just(MailboxGetResults.notFound(id)),
              mailboxId => getMailboxResultById(capabilities, mailboxId, mailboxSession)))
    }

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

    quotaFactory.loadFor(mailboxSession)
      .flatMap(quotaLoader => subscriptions.map[(QuotaLoader, Subscriptions)](subscriptions => (quotaLoader, subscriptions)))
      .subscribeOn(Schedulers.elastic)
      .flatMap {
        case (quotaLoader, subscriptions) => getAllMailboxesMetaData(capabilities, mailboxSession)
          .map((_, quotaLoader, subscriptions))
      }
      .flatMapMany {
        case (mailboxes, quotaLoader, subscriptions) => SFlux.fromIterable(mailboxes)
          .map(mailbox => (mailboxes, mailbox, quotaLoader, subscriptions))
      }
      .flatMap {
        case (mailboxes, mailbox, quotaLoader, subs) => mailboxFactory.create(mailboxMetaData = mailbox,
          mailboxSession = mailboxSession,
          subscriptions = subs,
          allMailboxesMetadata = mailboxes,
          quotaLoader = quotaLoader)
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
