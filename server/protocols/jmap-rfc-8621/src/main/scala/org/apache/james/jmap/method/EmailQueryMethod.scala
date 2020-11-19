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

import java.time.ZonedDateTime

import cats.implicits._
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.JMAPConfiguration
import org.apache.james.jmap.api.projections.EmailQueryView
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.core.{CanCalculateChanges, Invocation, Limit, Position, QueryState}
import org.apache.james.jmap.json.{EmailQuerySerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{Comparator, EmailQueryRequest, EmailQueryResponse, FilterCondition, UnsupportedRequestParameterException}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.jmap.utils.search.MailboxFilter
import org.apache.james.jmap.utils.search.MailboxFilter.QueryFilter
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{MailboxId, MessageId, MultimailboxesSearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.streams.{Limit => JavaLimit}
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

class EmailQueryMethod @Inject() (serializer: EmailQuerySerializer,
                                  mailboxManager: MailboxManager,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier,
                                  val configuration: JMAPConfiguration,
                                  val emailQueryView: EmailQueryView) extends MethodRequiringAccountId[EmailQueryRequest] {
  override val methodName: MethodName = MethodName("Email/query")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailQueryRequest): SMono[InvocationWithContext] = {
    processRequest(mailboxSession, invocation.invocation, request, capabilities)
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))
  }

  private def processRequest(mailboxSession: MailboxSession,
                             invocation: Invocation,
                             request: EmailQueryRequest,
                             capabilities: Set[CapabilityIdentifier]): SMono[Invocation] = {
    def validation: Either[Throwable, SMono[Invocation]] = for {
        searchQuery <- searchQueryFromRequest(request, capabilities, mailboxSession)
        limit <- Limit.validateRequestLimit(request.limit)
        position <- Position.validateRequestPosition(request.position)
      } yield {
        executeQuery(mailboxSession, request, searchQuery, position, limit)
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(serializer.serialize(response)),
            methodCallId = invocation.methodCallId))
      }
    validation.fold(SMono.raiseError, res => res)
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, EmailQueryRequest] =
    serializer.deserializeEmailQueryRequest(invocation.arguments.value) match {
      case JsSuccess(emailQueryRequest, _) => validateRequestParameters(emailQueryRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def validateRequestParameters(request: EmailQueryRequest): Either[Exception, EmailQueryRequest] =
    (request.anchor, request.anchorOffset) match {
      case (Some(anchor), _) => Left(UnsupportedRequestParameterException("anchor"))
      case (_, Some(anchorOffset)) => Left(UnsupportedRequestParameterException("anchorOffset"))
      case _ => Right(request)
    }

  private def executeQuery(session: MailboxSession, request: EmailQueryRequest, searchQuery: MultimailboxesSearchQuery, position: Position, limit: Limit): SMono[EmailQueryResponse] = {
    val ids: SMono[Seq[MessageId]] = request match {
      case request: EmailQueryRequest if matchesInMailboxSortedBySentAt(request) =>
        queryViewForListingSortedBySentAt(session, position, limit, request)
      case request: EmailQueryRequest if matchesInMailboxAfterSortedBySentAt(request) =>
        queryViewForContentAfterSortedBySentAt(session, position, limit, request)
      case _ => executeQueryAgainstSearchIndex(session, searchQuery, position, limit)
    }

    ids.map(ids => toResponse(request, position, limit, ids))
  }

  private def queryViewForContentAfterSortedBySentAt(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: EmailQueryRequest): SMono[Seq[MessageId]] = {
    val condition: FilterCondition = request.filter.get.asInstanceOf[FilterCondition]
    val mailboxId: MailboxId = condition.inMailbox.get
    val after: ZonedDateTime = condition.after.get.asUTC
    SMono.fromCallable(() => mailboxManager.getMailbox(mailboxId, mailboxSession))
      .subscribeOn(Schedulers.elastic())
      .`then`(SFlux.fromPublisher(
        emailQueryView.listMailboxContentSinceReceivedAt(mailboxId, after, JavaLimit.from(limitToUse.value)))
        .drop(position.value)
        .take(limitToUse.value)
        .collectSeq())
      .onErrorResume({
        case _: MailboxNotFoundException => SMono.just[Seq[MessageId]](Seq())
        case e => SMono.raiseError[Seq[MessageId]](e)
      })
  }

  private def queryViewForListingSortedBySentAt(mailboxSession: MailboxSession, position: Position, limitToUse: Limit, request: EmailQueryRequest): SMono[Seq[MessageId]] = {
    val mailboxId: MailboxId = request.filter.get.asInstanceOf[FilterCondition].inMailbox.get
    SMono.fromCallable(() => mailboxManager.getMailbox(mailboxId, mailboxSession))
      .subscribeOn(Schedulers.elastic())
      .`then`(SFlux.fromPublisher(
        emailQueryView.listMailboxContent(mailboxId, JavaLimit.from(limitToUse.value)))
        .drop(position.value)
        .take(limitToUse.value)
        .collectSeq())
      .onErrorResume({
        case _: MailboxNotFoundException => SMono.just[Seq[MessageId]](Seq())
        case e => SMono.raiseError[Seq[MessageId]](e)
      })
  }

  private def matchesInMailboxSortedBySentAt(request: EmailQueryRequest): Boolean =
    configuration.isEmailQueryViewEnabled &&
      request.filter.exists(_.inMailboxFilterOnly) &&
      request.comparator.contains(Set(Comparator.SENT_AT_DESC))

  private def matchesInMailboxAfterSortedBySentAt(request: EmailQueryRequest): Boolean =
    configuration.isEmailQueryViewEnabled &&
      request.filter.exists(_.inMailboxAndAfterFilterOnly) &&
      request.comparator.contains(Set(Comparator.SENT_AT_DESC))

  private def toResponse(request: EmailQueryRequest, position: Position, limitToUse: Limit, ids: Seq[MessageId]): EmailQueryResponse =
    EmailQueryResponse(accountId = request.accountId,
      queryState = QueryState.forIds(ids),
      canCalculateChanges = CanCalculateChanges.CANNOT,
      ids = ids,
      position = position,
      limit = Some(limitToUse).filterNot(used => request.limit.map(_.value).contains(used.value)))

  private def executeQueryAgainstSearchIndex(mailboxSession: MailboxSession, searchQuery: MultimailboxesSearchQuery, position: Position, limitToUse: Limit): SMono[Seq[MessageId]] =
    SFlux.fromPublisher(mailboxManager.search(searchQuery, mailboxSession, position.value + limitToUse))
      .drop(position.value)
      .collectSeq()

  private def searchQueryFromRequest(request: EmailQueryRequest, capabilities: Set[CapabilityIdentifier], session: MailboxSession): Either[UnsupportedOperationException, MultimailboxesSearchQuery] = {
    val comparators: List[Comparator] = request.comparator.getOrElse(Set()).toList

    comparators.map(_.toSort)
      .sequence
      .flatMap(sorts => for {
        queryFilter <- QueryFilter.buildQuery(request)
      } yield {
        if (sorts.isEmpty) {
          queryFilter
            .build()
        } else {
          queryFilter
            .sorts(sorts.asJava)
            .build()
        }
      })
      .map(MailboxFilter.buildQuery(request, _, capabilities, session))
  }

}