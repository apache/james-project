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
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JAMES_DELEGATION, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{ErrorCode, Invocation, Properties, SessionTranslator}
import org.apache.james.jmap.delegation.{Delegate, DelegateGet, DelegateGetRequest, DelegateGetResult}
import org.apache.james.jmap.json.{DelegationSerializer, ResponseSerializer}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.user.api.DelegationStore
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject

class DelegateGetMethod @Inject()(val metricFactory: MetricFactory,
                                  val sessionSupplier: SessionSupplier,
                                  val sessionTranslator: SessionTranslator,
                                  val delegationStore: DelegationStore) extends MethodRequiringAccountId[DelegateGetRequest] {

  override val methodName: MethodName = MethodName("Delegate/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JAMES_DELEGATION)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, DelegateGetRequest] =
    DelegationSerializer.deserializeDelegateGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: DelegateGetRequest): Publisher[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(DelegateGet.allProperties)
    (requestedProperties -- DelegateGet.allProperties match {
      case invalidProperties if invalidProperties.isEmpty() => getDelegateGetResponse(request, mailboxSession.getUser)
      .map(result => result.asResponse(request.accountId))
        .map(response => Invocation(
          methodName = methodName,
          arguments = Arguments(DelegationSerializer.serialize(response, requestedProperties).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId))
      case invalidProperties: Properties => SMono.just(Invocation.error(
        errorCode = ErrorCode.InvalidArguments,
        description = s"The following properties [${invalidProperties.format()}] do not exist.",
        methodCallId = invocation.invocation.methodCallId))
    }).map(InvocationWithContext(_, invocation.processingContext))
  }

  private def getDelegateGetResponse(request: DelegateGetRequest, baseUser: Username) : SMono[DelegateGetResult] =
    SFlux(delegationStore.authorizedUsers(baseUser))
      .map(Delegate.from)
      .collectSeq()
      .map(delegates => DelegateGetResult.from(delegates, request.ids.map(_.value.map(_.id).toSet)))
}
