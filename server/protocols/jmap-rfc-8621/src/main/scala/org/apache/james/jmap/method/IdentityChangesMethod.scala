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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.json.{IdentitySerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{HasMoreChanges, IdentityChangesRequest, IdentityChangesResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class IdentityChangesMethod @Inject()(val metricFactory: MetricFactory,
                                      val sessionSupplier: SessionSupplier,
                                      val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[IdentityChangesRequest] {
  override val methodName: MethodName = MethodName("Identity/changes")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(EMAIL_SUBMISSION)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: IdentityChangesRequest): Publisher[InvocationWithContext] =
    if (request.sinceState.equals(UuidState.INSTANCE)) {
      val response: IdentityChangesResponse = IdentityChangesResponse(
        accountId = request.accountId,
        oldState = UuidState.INSTANCE,
        newState = UuidState.INSTANCE,
        hasMoreChanges = HasMoreChanges(false),
        created = List(),
        updated = List(),
        destroyed = List())
      SMono.just(InvocationWithContext(invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(IdentitySerializer.serializeChanges(response)),
        methodCallId = invocation.invocation.methodCallId
      ), processingContext = invocation.processingContext))
    } else {
      SMono.just(InvocationWithContext(invocation = Invocation.error(ErrorCode.CannotCalculateChanges,
        "Naive implementation for Identity/changes",
        invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, IdentityChangesRequest] =
    IdentitySerializer.deserializeChanges(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
}
