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
import org.apache.james.jmap.api.change.EmailChangeRepository
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, ServerId, SessionTranslator, UuidState}
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.{EmailSetRequest, EmailSetResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MessageId
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.SMono

case class MessageNotFoundException(messageId: MessageId) extends Exception

class EmailSetMethod @Inject()(serializer: EmailSetSerializer,
                               val metricFactory: MetricFactory,
                               val sessionSupplier: SessionSupplier,
                               val sessionTranslator: SessionTranslator,
                               createPerformer: EmailSetCreatePerformer,
                               deletePerformer: EmailSetDeletePerformer,
                               updatePerformer: EmailSetUpdatePerformer,
                               emailChangeRepository: EmailChangeRepository) extends MethodRequiringAccountId[EmailSetRequest] {
  override val methodName: MethodName = MethodName("Email/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: EmailSetRequest): SMono[InvocationWithContext] = {
    for {
      oldState <- retrieveState(capabilities, mailboxSession)
      destroyResults <- deletePerformer.destroy(request, mailboxSession)
      updateResults <- updatePerformer.update(request, mailboxSession)
      created <- createPerformer.create(request, mailboxSession)
      newState <- retrieveState(capabilities, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(serializer.serialize(EmailSetResponse(
          accountId = request.accountId,
          oldState = Some(oldState),
          newState = newState,
          created = created.created,
          notCreated = created.notCreated,
          updated = updateResults.updated,
          notUpdated = updateResults.notUpdated,
          destroyed = destroyResults.destroyed,
          notDestroyed = destroyResults.notDestroyed))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = created.created.getOrElse(Map())
        .foldLeft(invocation.processingContext)({
          case (processingContext, (clientId, response)) =>
            Id.validate(response.id.serialize)
              .fold(_ => processingContext,
                serverId => processingContext.recordCreatedId(ClientId(clientId.id), ServerId(serverId)))
        }))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, EmailSetRequest] =
    serializer.deserialize(invocation.arguments.value).asEitherRequest

  private def retrieveState(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[UuidState] =
    if (capabilities.contains(JAMES_SHARES)) {
      SMono(emailChangeRepository.getLatestStateWithDelegation(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    } else {
      SMono(emailChangeRepository.getLatestState(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    }
}