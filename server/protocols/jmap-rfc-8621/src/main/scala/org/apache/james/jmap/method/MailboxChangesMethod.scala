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
import org.apache.james.jmap.api.change.MailboxChange.{State => JavaState}
import org.apache.james.jmap.api.change.MailboxChangeRepository
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{CapabilityIdentifier, Invocation, Properties, State}
import org.apache.james.jmap.json.{MailboxSerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{HasMoreChanges, MailboxChangesRequest, MailboxChangesResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

class MailboxChangesMethod @Inject()(mailboxSerializer: MailboxSerializer,
                                     val metricFactory: MetricFactory,
                                     val sessionSupplier: SessionSupplier,
                                     val mailboxChangeRepository: MailboxChangeRepository) extends MethodRequiringAccountId[MailboxChangesRequest] {
  override val methodName: MethodName = MethodName("Mailbox/changes")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxChangesRequest): SMono[InvocationWithContext] =
    SMono.fromPublisher({
      val accountId: JavaAccountId = JavaAccountId.fromUsername(mailboxSession.getUser)
      val state: JavaState = JavaState.of(request.sinceState.value)
      if (capabilities.contains(CapabilityIdentifier.JAMES_SHARES)) {
        mailboxChangeRepository.getSinceStateWithDelegation(accountId, state, request.maxChanged.toJava)
      } else {
        mailboxChangeRepository.getSinceState(accountId, state, request.maxChanged.toJava)
      }})
      .map(mailboxChanges => MailboxChangesResponse(
        accountId = request.accountId,
        oldState = request.sinceState,
        newState = State.fromMailboxChanges(mailboxChanges),
        hasMoreChanges = HasMoreChanges.fromMailboxChanges(mailboxChanges),
        updatedProperties = Some(Properties()),
        created = mailboxChanges.getCreated.asScala.toSet,
        updated = mailboxChanges.getUpdated.asScala.toSet,
        destroyed = mailboxChanges.getDestroyed.asScala.toSet))
      .map(response => InvocationWithContext(
        invocation = Invocation(
          methodName = methodName,
          arguments = Arguments(mailboxSerializer.serializeChanges(response)),
          methodCallId = invocation.invocation.methodCallId),
        processingContext = invocation.processingContext))

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, MailboxChangesRequest] =
    mailboxSerializer.deserializeMailboxChangesRequest(invocation.arguments.value) match {
      case JsSuccess(mailboxGetRequest, _) => Right(mailboxGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }
}
