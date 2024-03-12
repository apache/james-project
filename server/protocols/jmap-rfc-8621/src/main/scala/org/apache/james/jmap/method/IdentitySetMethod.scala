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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ClientId, Id, Invocation, JmapRfc8621Configuration, ServerId, SessionTranslator, UuidState}
import org.apache.james.jmap.json.IdentitySerializer
import org.apache.james.jmap.mail.{IdentitySetRequest, IdentitySetResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.SMono

class IdentitySetMethod @Inject()(createPerformer: IdentitySetCreatePerformer,
                                  updatePerformer: IdentitySetUpdatePerformer,
                                  deletePerformer: IdentitySetDeletePerformer,
                                  val configuration: JmapRfc8621Configuration,
                                  val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier,
                                  val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[IdentitySetRequest] {
  override val methodName: Invocation.MethodName = MethodName("Identity/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, EMAIL_SUBMISSION)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, IdentitySetRequest] =
    IdentitySerializer.deserializeIdentitySetRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: IdentitySetRequest): SMono[InvocationWithContext] =
    for {
      creationResults <- createPerformer.create(request, mailboxSession)
      updatedResults <- updatePerformer.update(request, mailboxSession)
      destroyResults <- deletePerformer.destroy(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(IdentitySerializer.serialize(IdentitySetResponse(
          accountId = request.accountId,
          oldState = None,
          newState = UuidState.INSTANCE,
          created = creationResults.created.filter(_.nonEmpty),
          notCreated = creationResults.notCreated.filter(_.nonEmpty),
          updated = Some(updatedResults.updated).filter(_.nonEmpty),
          notUpdated = Some(updatedResults.notUpdated).filter(_.nonEmpty),
          destroyed = Some(destroyResults.destroyed).filter(_.nonEmpty),
          notDestroyed = Some(destroyResults.retrieveErrors).filter(_.nonEmpty)))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = creationResults.created.getOrElse(Map())
        .foldLeft(invocation.processingContext)({
          case (processingContext, (clientId, response)) =>
            Id.validate(response.id.serialize)
              .fold(_ => processingContext,
                serverId => processingContext.recordCreatedId(ClientId(clientId.id), ServerId(serverId)))
        }))
}
