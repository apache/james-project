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
import javax.inject.Inject
import org.apache.james.jmap.json.{EmailQuerySerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{Comparator, EmailQueryRequest, EmailQueryResponse, UnsupportedFilterException, UnsupportedRequestParameterException, UnsupportedSortException}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.Limit.Limit
import org.apache.james.jmap.model.Position.Position
import org.apache.james.jmap.model.{CanCalculateChanges, Capabilities, ErrorCode, Invocation, Limit, Position, QueryState}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.jmap.utils.search.MailboxFilter
import org.apache.james.jmap.utils.search.MailboxFilter.QueryFilter
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MultimailboxesSearchQuery
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class EmailQueryMethod @Inject() (serializer: EmailQuerySerializer,
                                  mailboxManager: MailboxManager,
                                  metricFactory: MetricFactory) extends Method {
  override val methodName = MethodName("Email/query")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  override def process(capabilities: Set[CapabilityIdentifier], invocation: Invocation, mailboxSession: MailboxSession, processingContext: ProcessingContext): Publisher[(Invocation, ProcessingContext)] =
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      asEmailQueryRequest(invocation.arguments)
        .flatMap(processRequest(mailboxSession, invocation, _))
        .onErrorResume {
          case e: UnsupportedRequestParameterException => SMono.just(Invocation.error(
            ErrorCode.InvalidArguments,
            s"The following parameter ${e.unsupportedParam} is syntactically valid, but is not supported by the server.",
            invocation.methodCallId))
          case e: UnsupportedSortException => SMono.just(Invocation.error(
            ErrorCode.UnsupportedSort,
            s"The sort ${e.unsupportedSort} is syntactically valid, but it includes a property the server does not support sorting on or a collation method it does not recognise.",
            invocation.methodCallId))
          case e: UnsupportedFilterException => SMono.just(Invocation.error(
            ErrorCode.UnsupportedFilter,
            s"The filter ${e.unsupportedFilter} is syntactically valid, but the server cannot process it. If the filter was the result of a user’s search input, the client SHOULD suggest that the user simplify their search.",
            invocation.methodCallId))
          case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.methodCallId))
          case e: MailboxNotFoundException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, invocation.methodCallId))
          case e: Throwable => SMono.raiseError(e)
        }
        .map(invocationResult => (invocationResult, processingContext)))

  private def processRequest(mailboxSession: MailboxSession, invocation: Invocation, request: EmailQueryRequest): SMono[Invocation] = {
    searchQueryFromRequest(request) match {
      case Left(error) => SMono.raiseError(error)
      case Right(searchQuery) => for {
        positionToUse <- Position.validateRequestPosition(request.position)
        limitToUse <- Limit.validateRequestLimit(request.limit)
        response <- executeQuery(mailboxSession, request, searchQuery, positionToUse, limitToUse)
      } yield Invocation(methodName = methodName, arguments = Arguments(serializer.serialize(response)), methodCallId = invocation.methodCallId)
    }
  }

  private def executeQuery(mailboxSession: MailboxSession, request: EmailQueryRequest, searchQuery: MultimailboxesSearchQuery, position: Position, limitToUse: Limit): SMono[EmailQueryResponse] = {
    SFlux.fromPublisher(mailboxManager.search(searchQuery, mailboxSession, limitToUse))
      .drop(position.value)
      .collectSeq()
      .map(ids => EmailQueryResponse(accountId = request.accountId,
        queryState = QueryState.forIds(ids),
        canCalculateChanges = CanCalculateChanges.CANNOT,
        ids = ids,
        position = position,
        limit = Some(limitToUse).filterNot(used => request.limit.map(_.value).contains(used.value))))
  }

  private def searchQueryFromRequest(request: EmailQueryRequest): Either[UnsupportedOperationException, MultimailboxesSearchQuery] = {
    val comparators: List[Comparator] = request.comparator.getOrElse(Set(Comparator.default)).toList

    comparators.map(_.toSort)
      .sequence
      .flatMap(sorts => for {
        queryFilter <- QueryFilter.buildQuery(request)
      } yield queryFilter
        .sorts(sorts.asJava)
        .build())
      .map(MailboxFilter.buildQuery(request, _))
  }

  private def asEmailQueryRequest(arguments: Arguments): SMono[EmailQueryRequest] =
    serializer.deserializeEmailQueryRequest(arguments.value) match {
      case JsSuccess(emailQueryRequest, _) => validateRequestParameters(emailQueryRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def validateRequestParameters(request: EmailQueryRequest): SMono[EmailQueryRequest] =
    (request.anchor, request.anchorOffset) match {
      case (Some(anchor), _) => SMono.raiseError(UnsupportedRequestParameterException("anchor"))
      case (_, Some(anchorOffset)) => SMono.raiseError(UnsupportedRequestParameterException("anchorOffset"))
      case _ => SMono.just(request)
    }
}