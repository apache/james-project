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
import org.apache.james.jmap.api.model.PushSubscriptionId
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Ids, Invocation, PushSubscriptionDTO, PushSubscriptionGetRequest, PushSubscriptionGetResponse, UnparsedPushSubscriptionId}
import org.apache.james.jmap.json.{PushSubscriptionSerializer, ResponseSerializer}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

case class PushSubscriptionGetResults(results: Seq[PushSubscriptionDTO], notFound: Set[UnparsedPushSubscriptionId]) {
  def merge(other: PushSubscriptionGetResults): PushSubscriptionGetResults =
    PushSubscriptionGetResults(this.results ++ other.results, this.notFound ++ other.notFound)

  def asResponse(): PushSubscriptionGetResponse = PushSubscriptionGetResponse(
    list = results.toList,
    notFound = Some(notFound).filter(_.nonEmpty).map(_.toList).map(Ids))
}

class PushSubscriptionGetMethod @Inject()(pushSubscriptionSerializer: PushSubscriptionSerializer,
                                          pushSubscriptionRepository: PushSubscriptionRepository,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[PushSubscriptionGetRequest] with Startable {
  override val methodName: Invocation.MethodName = MethodName("PushSubscription/get")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)

  override def getRequest(invocation: Invocation): Either[Exception, PushSubscriptionGetRequest] =
    pushSubscriptionSerializer.deserializePushSubscriptionGetRequest(invocation.arguments.value) match {
      case JsSuccess(getRequest, _) => Right(getRequest)
      case errors: JsError => Left(new IllegalArgumentException(ResponseSerializer.serialize(errors).toString))
    }

  override def doProcess(invocation: InvocationWithContext, session: MailboxSession, request: PushSubscriptionGetRequest): SMono[InvocationWithContext] =
    request.validateProperties
      .fold(e => SMono.error(e),
        properties => retrieveRecords(request, session)
          .map(_.asResponse())
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(pushSubscriptionSerializer.serialize(response, properties).as[JsObject]),
            methodCallId = invocation.invocation.methodCallId))
          .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext)))

  private def retrieveRecords(request: PushSubscriptionGetRequest, session: MailboxSession): SMono[PushSubscriptionGetResults] =
    request.ids
      .map(retrieveRecords(_, session))
      .getOrElse(retrieveAllRecords(session))

  private def retrieveAllRecords(session: MailboxSession): SMono[PushSubscriptionGetResults] =
    SFlux(pushSubscriptionRepository.list(session.getUser))
      .map(PushSubscriptionDTO.from)
      .collectSeq
      .map(dtos => PushSubscriptionGetResults(dtos, Set()))

  private def retrieveRecords(unparsedIds: Ids, session: MailboxSession): SMono[PushSubscriptionGetResults] = {
    val ids: Set[PushSubscriptionId] = unparsedIds.value
      .flatMap(unparsedId => PushSubscriptionId.parse(unparsedId.serialise).toOption)
      .toSet

    SFlux(pushSubscriptionRepository.get(session.getUser, ids.asJava))
      .map(PushSubscriptionDTO.from)
      .collectSeq()
      .map(dtos => PushSubscriptionGetResults(dtos, unparsedIds.value.toSet -- dtos.map(dto => UnparsedPushSubscriptionId.of(dto.id))))
  }
}
