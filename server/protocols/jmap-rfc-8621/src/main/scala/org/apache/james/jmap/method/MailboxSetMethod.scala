/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *  http://www.apache.org/licenses/LICENSE-2.0                  *
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
import org.apache.james.jmap.api.change.MailboxChangeRepository
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_SHARES, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, JmapRfc8621Configuration, SessionTranslator, SetError, UuidState}
import org.apache.james.jmap.json.MailboxSerializer
import org.apache.james.jmap.mail.{MailboxSetRequest, MailboxSetResponse}
import org.apache.james.jmap.method.MailboxSetCreatePerformer.MailboxCreationResults
import org.apache.james.jmap.method.MailboxSetDeletePerformer.MailboxDeletionResults
import org.apache.james.jmap.method.MailboxSetUpdatePerformer.MailboxUpdateResults
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MailboxId
import org.apache.james.mailbox.{MailboxSession, MessageManager}
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.SMono

case class MailboxHasMailException(mailboxId: MailboxId) extends Exception
case class SystemMailboxChangeException(mailboxId: MailboxId) extends Exception
case class LoopInMailboxGraphException(mailboxId: MailboxId) extends Exception
case class MailboxHasChildException(mailboxId: MailboxId) extends Exception
case class MailboxCreationParseException(setError: SetError) extends Exception

object MailboxSetMethod {
  def assertCapabilityIfSharedMailbox(mailboxSession: MailboxSession, id: MailboxId, supportSharedMailbox: Boolean): MessageManager => SMono[Boolean] =
    mailbox => if (!mailbox.getMailboxPath.belongsTo(mailboxSession) && !supportSharedMailbox)
      SMono.error(new MailboxNotFoundException(id))
    else SMono.just(true)
}

class MailboxSetMethod @Inject()(serializer: MailboxSerializer,
                                 createPerformer: MailboxSetCreatePerformer,
                                 deletePerformer: MailboxSetDeletePerformer,
                                 updatePerformer: MailboxSetUpdatePerformer,
                                 configuration: JmapRfc8621Configuration,
                                 mailboxChangeRepository: MailboxChangeRepository,
                                 val metricFactory: MetricFactory,
                                 val sessionSupplier: SessionSupplier,
                                 val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[MailboxSetRequest] {
  override val methodName: MethodName = MethodName("Mailbox/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: MailboxSetRequest): SMono[InvocationWithContext] = for {
    oldState <- retrieveState(capabilities, mailboxSession)
    supportSharedMailbox = capabilities.contains(JAMES_SHARES)
    creationResults <- createPerformer.createMailboxes(mailboxSession, request, invocation.processingContext, supportSharedMailbox)
    updateResults <- updatePerformer.updateMailboxes(mailboxSession, request, capabilities)
    deletionResults <- deletePerformer.deleteMailboxes(mailboxSession, request, supportSharedMailbox)
    newState <- retrieveState(capabilities, mailboxSession)
    response = createResponse(capabilities, invocation.invocation, request, creationResults._1, deletionResults, updateResults, oldState, newState)
  } yield InvocationWithContext(response, creationResults._2)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, MailboxSetRequest] =
    serializer.deserializeMailboxSetRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  private def createResponse(capabilities: Set[CapabilityIdentifier],
                             invocation: Invocation,
                             mailboxSetRequest: MailboxSetRequest,
                             creationResults: MailboxCreationResults,
                             deletionResults: MailboxDeletionResults,
                             updateResults: MailboxUpdateResults,
                             oldState: UuidState,
                             newState: UuidState): Invocation = {
    val response = MailboxSetResponse(
      mailboxSetRequest.accountId,
      oldState = Some(oldState),
      newState = newState,
      destroyed = Some(deletionResults.destroyed).filter(_.nonEmpty),
      created = Some(creationResults.retrieveCreated).filter(_.nonEmpty),
      notCreated = Some(creationResults.retrieveErrors).filter(_.nonEmpty),
      updated = Some(updateResults.updated).filter(_.nonEmpty),
      notUpdated = Some(updateResults.notUpdated).filter(_.nonEmpty),
      notDestroyed = Some(deletionResults.retrieveErrors).filter(_.nonEmpty))

    Invocation(methodName,
      Arguments(serializer.serialize(response, capabilities).as[JsObject]),
      invocation.methodCallId)
  }

  private def retrieveState(capabilities: Set[CapabilityIdentifier], mailboxSession: MailboxSession): SMono[UuidState] =
    if (capabilities.contains(JAMES_SHARES)) {
      SMono(mailboxChangeRepository.getLatestStateWithDelegation(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    } else {
      SMono(mailboxChangeRepository.getLatestState(JavaAccountId.fromUsername(mailboxSession.getUser)))
        .map(UuidState.fromJava)
    }
}
