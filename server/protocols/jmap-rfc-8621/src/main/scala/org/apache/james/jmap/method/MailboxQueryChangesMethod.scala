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
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.{ErrorCode, Invocation, SessionTranslator}
import org.apache.james.jmap.json.MailboxQuerySerializer
import org.apache.james.jmap.mail.MailboxQueryChangesRequest
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class MailboxQueryChangesMethod @Inject()(val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier,
                                          val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[MailboxQueryChangesRequest] {
  override val methodName: MethodName = MethodName("Mailbox/queryChanges")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext,
                         mailboxSession: MailboxSession, request: MailboxQueryChangesRequest): Publisher[InvocationWithContext] =
    SMono.just(InvocationWithContext(Invocation.error(ErrorCode.CannotCalculateChanges,
      "Naive implementation for Mailbox/queryChanges",
      invocation.invocation.methodCallId),
      invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, MailboxQueryChangesRequest] =
    MailboxQuerySerializer.deserializeMailboxQueryChanges(invocation.arguments.value).asEitherRequest
}
