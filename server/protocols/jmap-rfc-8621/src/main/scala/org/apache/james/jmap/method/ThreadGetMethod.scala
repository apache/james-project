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
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, State}
import org.apache.james.jmap.json.{ResponseSerializer, ThreadSerializer}
import org.apache.james.jmap.mail.{Thread, ThreadGetRequest, ThreadGetResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsSuccess}
import reactor.core.scala.publisher.SMono

class ThreadGetMethod @Inject()(val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[ThreadGetRequest] {
  override val methodName: MethodName = MethodName("Thread/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: ThreadGetRequest): SMono[InvocationWithContext] = {
    val response = ThreadGetResponse(accountId = request.accountId,
      state = State.INSTANCE,
      list = retrieveThreads(request.ids))
    SMono.just(InvocationWithContext(invocation = Invocation(
      methodName = methodName,
      arguments = Arguments(ThreadSerializer.serialize(response)),
      methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, ThreadGetRequest] =
    ThreadSerializer.deserialize(invocation.arguments.value) match {
      case JsSuccess(threadGetRequest, _) => Right(threadGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  // Naive implementation
  private def retrieveThreads(ids: List[Id]): List[Thread] =
    ids.map(id => Thread(id = id, emailIds = List(id)))
}
