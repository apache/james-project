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
import javax.inject.Inject
import org.apache.james.jmap.api.vacation.{AccountId, VacationPatch, VacationRepository}
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.method.VacationResponseSetMethod.VACATION_RESPONSE_PATCH_OBJECT_KEY
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.SetError.SetErrorDescription
import org.apache.james.jmap.model.{Invocation, State}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.jmap.vacation.{VacationResponseSetError, VacationResponseSetRequest, VacationResponseSetResponse, VacationResponseUpdateResponse}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.date.ZonedDateTimeProvider
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.{SFlux, SMono}

object VacationResponseUpdateResults {
  def empty(): VacationResponseUpdateResults = VacationResponseUpdateResults(Map(), Map())

  def merge(a: VacationResponseUpdateResults, b: VacationResponseUpdateResults) = VacationResponseUpdateResults(a.updateSuccess ++ b.updateSuccess, a.updateFailures ++ b.updateFailures)
}

case class VacationResponseUpdateResults(updateSuccess: Map[String, VacationResponseUpdateResponse],
                                         updateFailures: Map[String, VacationResponseSetError])
sealed trait VacationResponseUpdateResult {
  def updated: Map[String, VacationResponseUpdateResponse]
  def notUpdated: Map[String, VacationResponseSetError]

  def asVacationResponseUpdateResults = VacationResponseUpdateResults(updated, notUpdated)
}
case class VacationResponseUpdateSuccess() extends VacationResponseUpdateResult {
  override def updated: Map[String, VacationResponseUpdateResponse] = Map(VACATION_RESPONSE_PATCH_OBJECT_KEY -> VacationResponseUpdateResponse(JsObject(Seq())))

  override def notUpdated: Map[String, VacationResponseSetError] = Map()
}
case class VacationResponseUpdateFailure(id: String, exception: Throwable) extends VacationResponseUpdateResult {
  override def updated: Map[String, VacationResponseUpdateResponse] = Map()

  override def notUpdated: Map[String, VacationResponseSetError] = Map(id -> asSetError(exception))

  def asSetError(exception: Throwable): VacationResponseSetError = exception match {
      case e: IllegalArgumentException => VacationResponseSetError.invalidArgument(Some(SetErrorDescription(e.getMessage)))
      case e: Throwable => VacationResponseSetError.serverFail(Some(SetErrorDescription(e.getMessage)))
    }
}

object VacationResponseSetMethod {
  val VACATION_RESPONSE_PATCH_OBJECT_KEY = "singleton"
}

class VacationResponseSetMethod @Inject() (serializer: Serializer,
                                           vacationRepository: VacationRepository,
                                           zonedDateTimeProvider: ZonedDateTimeProvider,
                                           metricFactory: MetricFactory) extends Method {
  override val methodName: MethodName = MethodName("VacationResponse/set")

  override def process(capabilities: Set[CapabilityIdentifier],
                       invocation: Invocation,
                       mailboxSession: MailboxSession,
                       processingContext: ProcessingContext): Publisher[(Invocation, ProcessingContext)] = {
      metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
        asVacationResponseSetRequest(invocation.arguments)
          .flatMap(vacationResponseSetRequest => update(mailboxSession, vacationResponseSetRequest)
            .map(updateResult => createResponse(invocation, vacationResponseSetRequest, updateResult)))
          .map((_, processingContext)))
  }

  private def update(mailboxSession: MailboxSession, vacationResponseSetRequest: VacationResponseSetRequest): SMono[VacationResponseUpdateResults] = {
    SFlux.fromIterable(vacationResponseSetRequest.parsePatch()
      .map[SMono[VacationResponseUpdateResult]]({
        case (id, Right(patch)) =>
          patch.asVacationPatch
            .fold(
              e => SMono.just(VacationResponseUpdateFailure(id, e)),
              validatedPatch => update(validatedPatch, mailboxSession))
        case (id, Left(e)) => SMono.just(VacationResponseUpdateFailure(id, e))
      }))
      .flatMap[VacationResponseUpdateResult](updateResultMono => updateResultMono)
      .map(updateResult => updateResult.asVacationResponseUpdateResults)
      .reduceWith(() => VacationResponseUpdateResults.empty(), VacationResponseUpdateResults.merge)
  }

  private def update(validatedPatch: VacationPatch, mailboxSession: MailboxSession): SMono[VacationResponseUpdateResult] =
    SMono.fromPublisher(
      vacationRepository.modifyVacation(toVacationAccountId(mailboxSession), validatedPatch))
      .`then`(SMono.just(VacationResponseUpdateSuccess()))

  private def toVacationAccountId(mailboxSession: MailboxSession): AccountId = {
    AccountId.fromUsername(mailboxSession.getUser)
  }

  private def asVacationResponseSetRequest(arguments: Arguments): SMono[VacationResponseSetRequest] = {
    serializer.deserializeVacationResponseSetRequest(arguments.value) match {
      case JsSuccess(vacationResponseSetRequest, _) => SMono.just(vacationResponseSetRequest)
      case errors: JsError => SMono.raiseError(new IllegalArgumentException(serializer.serialize(errors).toString))
    }
  }

  private def createResponse(invocation: Invocation,
                             vacationResponseSetRequest: VacationResponseSetRequest,
                             updateResult: VacationResponseUpdateResults): Invocation = {
    val response = VacationResponseSetResponse(
      accountId = vacationResponseSetRequest.accountId,
      newState = State.INSTANCE,
      updated = Some(updateResult.updateSuccess).filter(_.nonEmpty),
      notUpdated = Some(updateResult.updateFailures).filter(_.nonEmpty),
      notCreated = validateNoCreate(vacationResponseSetRequest),
      notDestroyed = validateNoDestroy(vacationResponseSetRequest))

    Invocation(methodName,
      Arguments(serializer.serialize(response).as[JsObject]),
      invocation.methodCallId)
  }

  private def validateNoCreate(vacationResponseSetRequest: VacationResponseSetRequest): Option[Map[String, VacationResponseSetError]] =
    vacationResponseSetRequest.create.map(aMap => aMap
      .view
      .mapValues(_ => VacationResponseSetError.invalidArgument(
        Some(SetErrorDescription("'create' is not supported on singleton objects"))))
      .toMap)

  private def validateNoDestroy(vacationResponseSetRequest: VacationResponseSetRequest): Option[Map[String, VacationResponseSetError]] =
    vacationResponseSetRequest.destroy.map(aSet =>
      aSet.map(id => (id, VacationResponseSetError.invalidArgument(
        Some(SetErrorDescription("'destroy' is not supported on singleton objects")))))
        .toMap)
}