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
import jakarta.inject.Inject
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.{Identity, IdentityId}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, EMAIL_SUBMISSION, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core._
import org.apache.james.jmap.json.IdentitySerializer
import org.apache.james.jmap.mail.{IdentityGet, IdentityGetRequest, IdentityGetResponse}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.ReactorUtils
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

class IdentityGetMethod @Inject() (identityRepository: IdentityRepository,
                                   val configuration: JmapRfc8621Configuration,
                                   val metricFactory: MetricFactory,
                                   val sessionSupplier: SessionSupplier,
                                   val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[IdentityGetRequest] {
  override val methodName: MethodName = MethodName("Identity/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, EMAIL_SUBMISSION)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: IdentityGetRequest): SMono[InvocationWithContext] = {
    val requestedProperties: Properties = request.properties.getOrElse(IdentityGet.allProperties)
    (requestedProperties -- IdentityGet.allProperties match {
      case invalidProperties if invalidProperties.isEmpty() => getIdentities(request, mailboxSession)
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
        .map(identityGetResponse => Invocation(
          methodName = methodName,
          arguments = Arguments(IdentitySerializer.serialize(identityGetResponse, requestedProperties ++ IdentityGet.idProperty, capabilities).as[JsObject]),
          methodCallId = invocation.invocation.methodCallId))
      case invalidProperties: Properties =>
        SMono.just(Invocation.error(errorCode = ErrorCode.InvalidArguments,
          description = s"The following properties [${invalidProperties.format()}] do not exist.",
          methodCallId = invocation.invocation.methodCallId))
    }).map(InvocationWithContext(_, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, IdentityGetRequest] =
    IdentitySerializer.deserialize(invocation.arguments.value).asEitherRequest
      .flatMap(request => request.validate(configuration).map(_ => request))

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