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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, PushSubscriptionSetRequest, PushSubscriptionSetResponse}
import org.apache.james.jmap.json.{PushSubscriptionSerializer, ResponseSerializer}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.SMono

import javax.inject.Inject

class PushSubscriptionSetMethod @Inject()(createPerformer: PushSubscriptionSetCreatePerformer,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[PushSubscriptionSetRequest] with Startable {
  override val methodName: Invocation.MethodName = MethodName("PushSubscription/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

  override def getRequest(invocation: Invocation): Either[Exception, PushSubscriptionSetRequest] =
    PushSubscriptionSerializer.deserializePushSubscriptionSetRequest(invocation.arguments.value) match {
      case JsSuccess(emailSetRequest, _) => Right(emailSetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: PushSubscriptionSetRequest): SMono[InvocationWithContext] =
    for {
      created <- createPerformer.create(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(PushSubscriptionSerializer.serialize(PushSubscriptionSetResponse(
          created = created.created,
          notCreated = created.notCreated))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext)
}
