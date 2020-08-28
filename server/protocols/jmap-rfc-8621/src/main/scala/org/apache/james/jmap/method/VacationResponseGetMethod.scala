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
import org.apache.james.jmap.api.vacation.{VacationRepository, AccountId => JavaAccountId}
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.VacationResponse.UnparsedVacationResponseId
import org.apache.james.jmap.mail.{VacationResponse, VacationResponseGetRequest, VacationResponseGetResponse, VacationResponseNotFound}
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.State.INSTANCE
import org.apache.james.jmap.model.{AccountId, CapabilityIdentifier, ErrorCode, Invocation, MissingCapabilityException}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

object VacationResponseGetResult {
  def empty: VacationResponseGetResult = VacationResponseGetResult(Set.empty, VacationResponseNotFound(Set.empty))
  def merge(result1: VacationResponseGetResult, result2: VacationResponseGetResult): VacationResponseGetResult = result1.merge(result2)
  def found(vacationResponse: VacationResponse): VacationResponseGetResult =
    VacationResponseGetResult(Set(vacationResponse), VacationResponseNotFound(Set.empty))
  def notFound(vacationResponseId: UnparsedVacationResponseId): VacationResponseGetResult =
    VacationResponseGetResult(Set.empty, VacationResponseNotFound(Set(vacationResponseId)))
}

case class VacationResponseGetResult(vacationResponses: Set[VacationResponse], notFound: VacationResponseNotFound) {
  def merge(other: VacationResponseGetResult): VacationResponseGetResult =
    VacationResponseGetResult(this.vacationResponses ++ other.vacationResponses, this.notFound.merge(other.notFound))
  def asResponse(accountId: AccountId): VacationResponseGetResponse =
    VacationResponseGetResponse(
      accountId = accountId,
      state = INSTANCE,
      list = vacationResponses.toList,
      notFound = notFound)
}

class VacationResponseGetMethod @Inject() (serializer: Serializer,
                                           vacationRepository: VacationRepository,
                                           metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("VacationResponse/get")

  override def process(capabilities: Set[CapabilityIdentifier],
                       invocation: Invocation,
                       mailboxSession: MailboxSession,
                       processingContext: ProcessingContext): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
      validate(capabilities)
        .flatMap(_ => asVacationResponseGetRequest(invocation.arguments))
        .fold(e => handleRequestValidationErrors(e, invocation.methodCallId),
          vacationResponseGetRequest =>
            getVacationResponse(vacationResponseGetRequest, processingContext, mailboxSession)
              .reduce(VacationResponseGetResult.empty, VacationResponseGetResult.merge)
              .map(vacationResult => vacationResult.asResponse(vacationResponseGetRequest.accountId))
              .map(vacationResponseGetResponse => Invocation(
                methodName = methodName,
                arguments = Arguments(serializer.serialize(vacationResponseGetResponse).as[JsObject]),
                methodCallId = invocation.methodCallId))))
  }

  private def validate(capabilities: Set[CapabilityIdentifier]): Either[MissingCapabilityException, Unit] =
    if (!capabilities.contains(CapabilityIdentifier.JMAP_VACATION_RESPONSE)) {
      Left(MissingCapabilityException(CapabilityIdentifier.JMAP_VACATION_RESPONSE))
    } else {
      Right()
    }

  private def asVacationResponseGetRequest(arguments: Arguments): Either[IllegalArgumentException, VacationResponseGetRequest] =
    serializer.deserializeVacationResponseGetRequest(arguments.value) match {
      case JsSuccess(vacationResponseGetRequest, _) => Right(vacationResponseGetRequest)
      case errors: JsError => Left(new IllegalArgumentException(serializer.serialize(errors).toString))
    }

  private def handleRequestValidationErrors(exception: Exception, methodCallId: MethodCallId): Publisher[Invocation] = exception match {
    case _: MissingCapabilityException => SMono.just(Invocation.error(ErrorCode.UnknownMethod, methodCallId))
    case e: IllegalArgumentException => SMono.just(Invocation.error(ErrorCode.InvalidArguments, e.getMessage, methodCallId))
  }

  private def getVacationResponse(vacationResponseGetRequest: VacationResponseGetRequest,
                                  processingContext: ProcessingContext,
                                  mailboxSession: MailboxSession): SFlux[VacationResponseGetResult] =
    vacationResponseGetRequest.ids match {
      case None => getVacationSingleton(mailboxSession)
        .map(VacationResponseGetResult.found)
        .flux()
      case Some(ids) => SFlux.fromIterable(ids.value)
        .flatMap(id => processingContext.resolveVacationResponseId(id)
          .fold(e => SMono.just(VacationResponseGetResult.notFound(id)),
            vacationResponseId => getVacationSingleton(mailboxSession).map(VacationResponseGetResult.found)))
    }

  private def getVacationSingleton(mailboxSession: MailboxSession): SMono[VacationResponse] = {
    val accountId: JavaAccountId = JavaAccountId.fromUsername(mailboxSession.getUser)
    SMono.fromPublisher(vacationRepository.retrieveVacation(accountId))
      .map(VacationResponse.asRfc8621)
  }
}
