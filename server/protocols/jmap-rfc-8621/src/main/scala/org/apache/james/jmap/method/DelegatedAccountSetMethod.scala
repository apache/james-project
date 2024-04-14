/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_DELEGATION, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, JmapRfc8621Configuration, SessionTranslator, UuidState}
import org.apache.james.jmap.delegation.{DelegatedAccountSetRequest, DelegatedAccountSetResponse, ForbiddenAccountManagementException}
import org.apache.james.jmap.json.DelegationSerializer
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.MailboxSession.isPrimaryAccount
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.SMono

class DelegatedAccountSetMethod @Inject()(deletePerformer: DelegatedAccountDeletePerformer,
                                          val configuration: JmapRfc8621Configuration,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier,
                                          val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[DelegatedAccountSetRequest] {
  override val methodName: Invocation.MethodName = MethodName("DelegatedAccount/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JAMES_DELEGATION)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, DelegatedAccountSetRequest] =
    DelegationSerializer.deserializeDelegatedAccountSetRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: DelegatedAccountSetRequest): SMono[InvocationWithContext] =
    if (isPrimaryAccount(mailboxSession)) {
      for {
        destroyResults <- deletePerformer.delete(request, mailboxSession)
      } yield InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(DelegationSerializer.serializeDelegatedAccountSetResponse(DelegatedAccountSetResponse(
            accountId = request.accountId,
            oldState = None,
            newState = UuidState.INSTANCE,
            destroyed = Some(destroyResults.destroyed).filter(_.nonEmpty),
            notDestroyed = Some(destroyResults.retrieveErrors).filter(_.nonEmpty)))),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext)
    } else {
      SMono.error(ForbiddenAccountManagementException())
    }
}

