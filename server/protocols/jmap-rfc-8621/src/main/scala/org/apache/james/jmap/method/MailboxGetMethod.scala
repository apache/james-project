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
import org.apache.james.jmap.mail._
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.State.INSTANCE
import org.apache.james.jmap.model.{Invocation, MailboxFactory}
import org.apache.james.jmap.utils.quotas.{QuotaLoader, QuotaLoaderWithPreloadedDefaultFactory}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxId, MailboxMetaData}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

class MailboxGetMethod @Inject() (serializer: Serializer,
                                  mailboxManager: MailboxManager,
                                  quotaFactory : QuotaLoaderWithPreloadedDefaultFactory,
                                  mailboxFactory: MailboxFactory,
                                  metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("Mailbox/get")

  object MailboxGetResults {
    def found(mailbox: Mailbox): MailboxGetResults = MailboxGetResults(Set(mailbox), NotFound(Set.empty))
    def notFound(mailboxId: MailboxId): MailboxGetResults = MailboxGetResults(Set.empty, NotFound(Set(mailboxId)))
  }

  case class MailboxGetResults(mailboxes: Set[Mailbox], notFound: NotFound) {
    def merge(other: MailboxGetResults): MailboxGetResults = MailboxGetResults(this.mailboxes ++ other.mailboxes, this.notFound.merge(other.notFound))
  }

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asMailboxGetRequest(invocation.arguments)
        .flatMap(mailboxGetRequest => getMailboxes(mailboxGetRequest, mailboxSession)
          .reduce(MailboxGetResults(Set.empty, NotFound(Set.empty)), (result1: MailboxGetResults, result2: MailboxGetResults) => result1.merge(result2))
          .map(mailboxes => MailboxGetResponse(
            accountId = mailboxGetRequest.accountId,
            state = INSTANCE,
            list = mailboxes.mailboxes.toList.sortBy(_.sortOrder),
            notFound = mailboxes.notFound))
          .map(mailboxGetResponse => Invocation(
            methodName = methodName,
            arguments = Arguments(serializer.serialize(mailboxGetResponse, capabilities).as[JsObject]),
            methodCallId = invocation.methodCallId))))
  }

  private def asMailboxGetRequest(arguments: Arguments): SMono[MailboxGetRequest] = {
    serializer.deserializeMailboxGetRequest(arguments.value) match {
      case JsSuccess(mailboxGetRequest, _) => SMono.just(mailboxGetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }

  private def getMailboxes(mailboxGetRequest: MailboxGetRequest,
                           mailboxSession: MailboxSession): SFlux[MailboxGetResults] =
    mailboxGetRequest.ids match {
      case None => getAllMailboxes(mailboxSession).map(MailboxGetResults.found)
      case Some(ids) => SFlux.fromIterable(ids.value)
        .flatMap(id => getMailboxResultById(id, mailboxSession))
    }

  private def getMailboxResultById(mailboxId: MailboxId, mailboxSession: MailboxSession): SMono[MailboxGetResults] =
    quotaFactory.loadFor(mailboxSession)
      .flatMap(quotaLoader => mailboxFactory.create(mailboxId, mailboxSession, quotaLoader)
        .map(MailboxGetResults.found)
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.just(MailboxGetResults.notFound(mailboxId))
          case error => SMono.raiseError(error)
        })
      .subscribeOn(Schedulers.elastic)

  private def getAllMailboxes(mailboxSession: MailboxSession): SFlux[Mailbox] = {
    quotaFactory.loadFor(mailboxSession)
      .subscribeOn(Schedulers.elastic)
      .flatMapMany(quotaLoader =>
        getAllMailboxesMetaData(mailboxSession).flatMapMany(mailboxesMetaData =>
          SFlux.fromIterable(mailboxesMetaData)
            .flatMap(mailboxMetaData =>
              getMailboxResult(mailboxMetaData = mailboxMetaData,
                mailboxSession = mailboxSession,
                allMailboxesMetadata = mailboxesMetaData,
                quotaLoader = quotaLoader))))
  }

  private def getAllMailboxesMetaData(mailboxSession: MailboxSession): SMono[Seq[MailboxMetaData]] =
    SFlux.fromPublisher(mailboxManager.search(MailboxQuery.builder.matchesAllMailboxNames.build, mailboxSession))
      .collectSeq()

  private def getMailboxResult(mailboxSession: MailboxSession,
                                allMailboxesMetadata: Seq[MailboxMetaData],
                                mailboxMetaData: MailboxMetaData,
                                quotaLoader: QuotaLoader): SMono[Mailbox] =
    mailboxFactory.create(mailboxMetaData = mailboxMetaData,
      mailboxSession = mailboxSession,
      allMailboxesMetadata = allMailboxesMetadata,
      quotaLoader = quotaLoader)
}
