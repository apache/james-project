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
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.{Identity, IdentityId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core._
import org.apache.james.jmap.json.{IdentitySerializer, ResponseSerializer}
import org.apache.james.jmap.mail.{IdentityGet, IdentityGetRequest, IdentityGetResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

class IdentityGetMethod @Inject() (identityRepository: IdentityRepository,
                                   val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[IdentityGetRequest] {
  override val methodName: MethodName = MethodName("Identity/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, EMAIL_SUBMISSION)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: IdentityGetRequest): SMono[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(IdentityGet.allProperties)
    (requestedProperties -- IdentityGet.allProperties match {
      case invalidProperties if invalidProperties.isEmpty() => getIdentities(request, mailboxSession)
        .subscribeOn(Schedulers.elastic())
        .map(identityGetResponse => Invocation(
          methodName = methodName,
          arguments = Arguments(IdentitySerializer.serialize(identityGetResponse, requestedProperties ++ IdentityGet.idProperty).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId))
      case invalidProperties: Properties =>
        SMono.just(Invocation.error(errorCode = ErrorCode.InvalidArguments,
          description = s"The following properties [${invalidProperties.format()}] do not exist.",
          methodCallId = invocation.invocation.methodCallId))
    }).map(InvocationWithContext(_, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, IdentityGetRequest] =
    IdentitySerializer.deserialize(invocation.arguments.value) match {
      case JsSuccess(request, _) => Right(request)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  private def getIdentities(request: IdentityGetRequest,
                            mailboxSession: MailboxSession): SMono[IdentityGetResponse] =
    SFlux(identityRepository.list(mailboxSession.getUser))
      .collectSeq()
      .map(_.toList)
      .map(request.computeResponse)
}

case class IdentityResolver @Inject()(identityRepository: IdentityRepository) {
  def resolveIdentityId(identityId: IdentityId, session: MailboxSession): SMono[Option[Identity]] =
    SFlux(identityRepository.list(session.getUser))
      .filter(identity => identity.id.equals(identityId))
      .map(Some(_))
      .next()
      .switchIfEmpty(SMono.just(None))
}