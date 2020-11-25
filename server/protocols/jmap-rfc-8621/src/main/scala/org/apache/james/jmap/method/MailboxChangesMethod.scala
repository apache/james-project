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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, Properties, State}
import org.apache.james.jmap.json.{MailboxSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{HasMoreChanges, MailboxChangesRequest, MailboxChangesResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.SMono

class MailboxChangesMethod @Inject()(mailboxSerializer: MailboxSerializer,
                                   val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[MailboxChangesRequest] {
  override val methodName: MethodName = MethodName("Mailbox/changes")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxChangesRequest): SMono[InvocationWithContext] =
    if (request.sinceState.equals(State.INSTANCE)) {
      val response: MailboxChangesResponse = MailboxChangesResponse(
        accountId = request.accountId,
        oldState = State.INSTANCE,
        newState = State.INSTANCE,
        hasMoreChanges = HasMoreChanges(false),
        updatedProperties = Some(Properties()),
        created = List(),
        updated = List(),
        destroyed = List())
      SMono.just(InvocationWithContext(invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(mailboxSerializer.serializeChanges(response)),
        methodCallId = invocation.invocation.methodCallId
      ), processingContext = invocation.processingContext))
    } else {
      SMono.just(InvocationWithContext(invocation = Invocation.error(ErrorCode.CannotCalculateChanges,
        "Naive implementation for Mailbox/changes",
        invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, MailboxChangesRequest] =
    mailboxSerializer.deserializeMailboxChangesRequest(invocation.arguments.value) match {
      case JsSuccess(mailboxGetRequest, _) => Right(mailboxGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
}
