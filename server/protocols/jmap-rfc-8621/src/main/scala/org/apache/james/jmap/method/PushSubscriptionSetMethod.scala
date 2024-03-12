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
import javax.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, JmapRfc8621Configuration, PushSubscriptionSetRequest, PushSubscriptionSetResponse, SessionTranslator}
import org.apache.james.jmap.json.PushSubscriptionSerializer
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import reactor.core.scala.publisher.SMono

class PushSubscriptionSetMethod @Inject()(createPerformer: PushSubscriptionSetCreatePerformer,
                                          updatePerformer: PushSubscriptionUpdatePerformer,
                                          deletePerformer: PushSubscriptionSetDeletePerformer,
                                          configuration: JmapRfc8621Configuration,
                                          pushSubscriptionSerializer: PushSubscriptionSerializer,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier,
                                          val sessionTranslator: SessionTranslator) extends MethodWithoutAccountId[PushSubscriptionSetRequest] with Startable {
  override val methodName: Invocation.MethodName = MethodName("PushSubscription/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

  override def getRequest(invocation: Invocation): Either[Exception, PushSubscriptionSetRequest] =
    pushSubscriptionSerializer.deserializePushSubscriptionSetRequest(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: PushSubscriptionSetRequest): SMono[InvocationWithContext] =
    for {
      created <- createPerformer.create(request, mailboxSession)
      updated <- updatePerformer.update(request, mailboxSession)
      destroyed <- deletePerformer.deletePushSubscriptions(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(pushSubscriptionSerializer.serialize(PushSubscriptionSetResponse(
          created = created.created.filter(_.nonEmpty),
          notCreated = created.notCreated.filter(_.nonEmpty),
          updated = Some(updated.updated).filter(_.nonEmpty),
          notUpdated = Some(updated.notUpdated).filter(_.nonEmpty),
          destroyed = Some(destroyed.destroyed).filter(_.nonEmpty),
          notDestroyed = Some(destroyed.retrieveErrors).filter(_.nonEmpty)))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext)
}
