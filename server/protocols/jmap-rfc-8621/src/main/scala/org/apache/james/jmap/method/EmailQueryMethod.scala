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
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{EmailQuerySerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{Comparator, EmailQueryRequest, EmailQueryResponse, UnsupportedRequestParameterException}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.DefaultCapabilities.{CORE_CAPABILITY, MAIL_CAPABILITY}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.Limit.Limit
import org.apache.james.jmap.model.Position.Position
import org.apache.james.jmap.model.{CanCalculateChanges, Capabilities, Invocation, Limit, Position, QueryState}
import org.apache.james.jmap.utils.search.MailboxFilter
import org.apache.james.jmap.utils.search.MailboxFilter.QueryFilter
import org.apache.james.mailbox.model.MultimailboxesSearchQuery
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class EmailQueryMethod @Inject() (serializer: EmailQuerySerializer,
                                  mailboxManager: MailboxManager,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[EmailQueryRequest] {
  override val methodName: MethodName = MethodName("Email/query")
  override val requiredCapabilities: Capabilities = Capabilities(CORE_CAPABILITY, MAIL_CAPABILITY)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailQueryRequest): SMono[InvocationWithContext] = {
    processRequest(mailboxSession, invocation.invocation, request, capabilities)
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))
  }

  private def processRequest(mailboxSession: MailboxSession,
                             invocation: Invocation,
                             request: EmailQueryRequest,
                             capabilities: Set[CapabilityIdentifier]): SMono[Invocation] = {
    searchQueryFromRequest(request, capabilities, mailboxSession) match {
      case Left(error) => SMono.raiseError(error)
      case Right(searchQuery) =>  for {
        positionToUse <- Position.validateRequestPosition(request.position)
        limitToUse <- Limit.validateRequestLimit(request.limit)
        response <- executeQuery(mailboxSession, request, searchQuery, positionToUse, limitToUse)
      } yield Invocation(methodName = methodName, arguments = Arguments(serializer.serialize(response)), methodCallId = invocation.methodCallId)
    }
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[EmailQueryRequest] = asEmailQueryRequest(invocation.arguments)

  private def executeQuery(mailboxSession: MailboxSession, request: EmailQueryRequest, searchQuery: MultimailboxesSearchQuery, position: Position, limitToUse: Limit): SMono[EmailQueryResponse] = {
    SFlux.fromPublisher(mailboxManager.search(searchQuery, mailboxSession, position.value + limitToUse))
      .drop(position.value)
      .collectSeq()
      .map(ids => EmailQueryResponse(accountId = request.accountId,
        queryState = QueryState.forIds(ids),
        canCalculateChanges = CanCalculateChanges.CANNOT,
        ids = ids,
        position = position,
        limit = Some(limitToUse).filterNot(used => request.limit.map(_.value).contains(used.value))))
  }

  private def searchQueryFromRequest(request: EmailQueryRequest, capabilities: Set[CapabilityIdentifier], session: MailboxSession): Either[UnsupportedOperationException, MultimailboxesSearchQuery] = {
    val comparators: List[Comparator] = request.comparator.getOrElse(Set(Comparator.default)).toList

    comparators.map(_.toSort)
      .sequence
      .flatMap(sorts => for {
        queryFilter <- QueryFilter.buildQuery(request)
      } yield queryFilter
        .sorts(sorts.asJava)
        .build())
      .map(MailboxFilter.buildQuery(request, _, capabilities, session))
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