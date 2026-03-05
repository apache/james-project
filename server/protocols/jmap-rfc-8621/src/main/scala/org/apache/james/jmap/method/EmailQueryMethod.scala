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

import cats.implicits._
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import jakarta.mail.Flags.Flag.DELETED
import org.apache.james.jmap.JMAPConfiguration
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.Limit.Limit
import org.apache.james.jmap.core.Position.Position
import org.apache.james.jmap.core.{CanCalculateChanges, Invocation, Limit, Position, QueryState, SessionTranslator}
import org.apache.james.jmap.json.EmailQuerySerializer
import org.apache.james.jmap.mail.{Comparator, EmailQueryRequest, EmailQueryResponse, UnsupportedRequestParameterException}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.jmap.utils.search.MailboxFilter
import org.apache.james.jmap.utils.search.MailboxFilter.QueryFilter
import org.apache.james.mailbox.model.{MessageId, MultimailboxesSearchQuery, SearchOptions, SearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.streams.{Offset, Limit => JavaLimit}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class EmailQueryMethod @Inject() (serializer: EmailQuerySerializer,
                                  mailboxManager: MailboxManager,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier,
                                  val sessionTranslator: SessionTranslator,
                                  val configuration: JMAPConfiguration,
                                  val javaEmailQueryOptimizers: java.util.Set[EmailQueryOptimizer]) extends MethodRequiringAccountId[EmailQueryRequest] {
  private val emailQueryOptimizers: Set[EmailQueryOptimizer] = javaEmailQueryOptimizers.asScala.toSet

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
    validation.fold(SMono.error, res => res)
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, EmailQueryRequest] =
    serializer.deserializeEmailQueryRequest(invocation.arguments.value)
      .asEitherRequest
      .flatMap(validateRequestParameters)

  private def validateRequestParameters(request: EmailQueryRequest): Either[Exception, EmailQueryRequest] =
    (request.anchor, request.anchorOffset) match {
      case (Some(anchor), _) => Left(UnsupportedRequestParameterException("anchor"))
      case (_, Some(anchorOffset)) => Left(UnsupportedRequestParameterException("anchorOffset"))
      case _ => Right(request)
    }

  private def executeQuery(session: MailboxSession, request: EmailQueryRequest, searchQuery: MultimailboxesSearchQuery, position: Position, limit: Limit): SMono[EmailQueryResponse] = {
    val ids: SMono[Seq[MessageId]] = executeQueryOptimizers(session, request, searchQuery, position, limit)
      .getOrElse(executeQueryAgainstSearchIndex(session, searchQuery, position, limit))
      .collectSeq()

    ids.map(ids => toResponse(request, position, limit, ids))
  }

  private def executeQueryOptimizers(session: MailboxSession, request: EmailQueryRequest, searchQuery: MultimailboxesSearchQuery, position: Position, limit: Limit): Option[SFlux[MessageId]] =
    if (configuration.isEmailQueryViewEnabled) {
      emailQueryOptimizers.iterator
        .map(_.apply(request, session, searchQuery, position, limit))
        .collectFirst { case Some(result) => result }
    } else {
      None
    }

  private def getCollapseThreads(request: EmailQueryRequest): Boolean =
    request.collapseThreads match {
      case Some(collapseThreads) => collapseThreads.value
      case None => false
    }

  private def toResponse(request: EmailQueryRequest, position: Position, limitToUse: Limit, ids: Seq[MessageId]): EmailQueryResponse =
    EmailQueryResponse(accountId = request.accountId,
      queryState = QueryState.forIds(ids),
      canCalculateChanges = CanCalculateChanges.CANNOT,
      ids = ids,
      position = position,
      limit = Some(limitToUse).filterNot(used => request.limit.map(_.value).contains(used.value)))

  private def executeQueryAgainstSearchIndex(mailboxSession: MailboxSession, searchQuery: MultimailboxesSearchQuery, position: Position, limitToUse: Limit): SFlux[MessageId] =
    SFlux.fromPublisher(mailboxManager.search(
        searchQuery.addCriterion(SearchQuery.flagIsUnSet(DELETED)),
        mailboxSession,
        SearchOptions.of(Offset.from(position.value), JavaLimit.limit(limitToUse.value))))

  private def searchQueryFromRequest(request: EmailQueryRequest, capabilities: Set[CapabilityIdentifier], session: MailboxSession): Either[UnsupportedOperationException, MultimailboxesSearchQuery] = {
    val comparators: List[Comparator] = request.sort.getOrElse(Set()).toList

    comparators.map(_.toSort)
      .sequence
      .flatMap(sorts => for {
        queryFilter <- QueryFilter.buildQuery(request)
      } yield {
        queryFilter.collapseThreads(getCollapseThreads(request))
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