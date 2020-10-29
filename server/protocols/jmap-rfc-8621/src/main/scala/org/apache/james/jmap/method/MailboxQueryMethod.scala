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
import org.apache.james.jmap.http.SessionSupplier
import org.apache.james.jmap.json.{MailboxQuerySerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{MailboxQueryRequest, MailboxQueryResponse}
import org.apache.james.jmap.model.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model._
import org.apache.james.mailbox.{MailboxSession, SystemMailboxesProvider}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

class MailboxQueryMethod @Inject()(systemMailboxesProvider: SystemMailboxesProvider,
                                   val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[MailboxQueryRequest] {
  override val methodName = MethodName("Mailbox/query")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxQueryRequest): SMono[InvocationWithContext] = {
    processRequest(mailboxSession, invocation.invocation, request)
      .onErrorResume {
      case e: IllegalArgumentException => SMono.just(
        Invocation.error(
          errorCode = ErrorCode.InvalidArguments,
          description = e.getMessage,
          methodCallId = invocation.invocation.methodCallId))
      case e: Throwable => SMono.raiseError(e)
    }
      .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): SMono[MailboxQueryRequest] = asMailboxQueryRequest(invocation.arguments)

  private def processRequest(mailboxSession: MailboxSession, invocation: Invocation, request: MailboxQueryRequest): SMono[Invocation] = {
    SFlux.fromPublisher(systemMailboxesProvider.getMailboxByRole(request.filter.role, mailboxSession.getUser))
      .map(_.getId)
      .collectSeq()
      .map(ids => MailboxQueryResponse(accountId = request.accountId,
        queryState = QueryState.forMailboxIds(ids),
        canCalculateChanges = CanCalculateChanges(false),
        ids = ids,
        position = Position.zero,
        limit = Some(Limit.default)))
      .map(response => Invocation(methodName = methodName, arguments = Arguments(MailboxQuerySerializer.serialize(response)), methodCallId = invocation.methodCallId))
      .subscribeOn(Schedulers.elastic())
  }

  private def asMailboxQueryRequest(arguments: Arguments): SMono[MailboxQueryRequest] =
    MailboxQuerySerializer.deserialize(arguments.value) match {
      case JsSuccess(emailQueryRequest, _) => SMono.just(emailQueryRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

}