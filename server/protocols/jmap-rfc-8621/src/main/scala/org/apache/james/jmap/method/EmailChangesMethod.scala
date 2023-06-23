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
import org.apache.james.jmap.api.change.{CanNotCalculateChangesException, EmailChangeRepository, EmailChanges, State => JavaState}
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.json.EmailGetSerializer
import org.apache.james.jmap.mail.{EmailChangesRequest, EmailChangesResponse, HasMoreChanges}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

class EmailChangesMethod @Inject()(val metricFactory: MetricFactory,
                                   val emailChangeRepository: EmailChangeRepository,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[EmailChangesRequest] {
  override val methodName: MethodName = MethodName("Email/changes")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailChangesRequest): SMono[InvocationWithContext] =
    retrieveChanges(capabilities, mailboxSession, request)
      .map(emailChanges => EmailChangesResponse(
        accountId = request.accountId,
        oldState = request.sinceState,
        newState = UuidState.fromEmailChanges(emailChanges),
        hasMoreChanges = HasMoreChanges.fromEmailChanges(emailChanges),
        created = emailChanges.getCreated.asScala.toSet,
        updated = emailChanges.getUpdated.asScala.toSet,
        destroyed = emailChanges.getDestroyed.asScala.toSet))
      .map(response => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(EmailGetSerializer.serializeChanges(response)),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))
      .onErrorResume {
        case e: CanNotCalculateChangesException => SMono.just(InvocationWithContext(Invocation.error(ErrorCode.CannotCalculateChanges, e.getMessage, invocation.invocation.methodCallId), invocation.processingContext))
        case e => SMono.error(e)
      }

  private def retrieveChanges(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession, request: EmailChangesRequest): SMono[EmailChanges] = {
      val accountId: JavaAccountId = JavaAccountId.fromUsername(mailboxSession.getUser)
      if (capabilities.contains(JAMES_SHARES)) {
        SMono[EmailChanges](emailChangeRepository.getSinceStateWithDelegation(accountId, JavaState.of(request.sinceState.value), request.maxChanges.toJava))
      } else {
        SMono[EmailChanges](emailChangeRepository.getSinceState(accountId, JavaState.of(request.sinceState.value), request.maxChanges.toJava))
      }
    }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, EmailChangesRequest] =
    EmailGetSerializer.deserializeEmailChangesRequest(invocation.arguments.value).asEitherRequest
}
