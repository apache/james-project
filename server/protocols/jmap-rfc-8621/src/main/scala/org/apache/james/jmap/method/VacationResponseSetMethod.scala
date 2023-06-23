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
import javax.inject.{Inject, Named}
import org.apache.james.events.Event.EventId
import org.apache.james.events.EventBus
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeEvent, VacationResponseTypeName}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_VACATION_RESPONSE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.json.VacationSerializer
import org.apache.james.jmap.method.VacationResponseSetMethod.VACATION_RESPONSE_PATCH_OBJECT_KEY
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.jmap.vacation.{VacationResponseSetError, VacationResponseSetRequest, VacationResponseSetResponse, VacationResponseUpdateResponse}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.vacation.api.{VacationPatch, VacationService, AccountId => VacationAccountId}
import play.api.libs.json.JsObject
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
case object VacationResponseUpdateSuccess extends VacationResponseUpdateResult {
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

class VacationResponseSetMethod @Inject()(@Named(InjectionKeys.JMAP) eventBus: EventBus,
                                          vacationService: VacationService,
                                          val metricFactory: MetricFactory,
                                          val sessionSupplier: SessionSupplier,
                                          val sessionTranslator: SessionTranslator) extends MethodRequiringAccountId[VacationResponseSetRequest] {
  override val methodName: MethodName = MethodName("VacationResponse/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_VACATION_RESPONSE)

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: VacationResponseSetRequest): SMono[InvocationWithContext] = {
    update(mailboxSession, request)
      .map(updateResult => createResponse(invocation.invocation, request, updateResult))
      .flatMap(next => {
        val event = StateChangeEvent(eventId = EventId.random(),
          username = mailboxSession.getUser,
          map = Map(VacationResponseTypeName -> UuidState.fromGenerateUuid()))
        val accountId = AccountId.fromUsername(mailboxSession.getUser)
        SMono(eventBus.dispatch(event, AccountIdRegistrationKey(accountId)))
          .`then`(SMono.just(next))
      })
      .map(InvocationWithContext(_, invocation.processingContext))
  }

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[IllegalArgumentException, VacationResponseSetRequest] =
    VacationSerializer.deserializeVacationResponseSetRequest(invocation.arguments.value).asEitherRequest

  private def update(mailboxSession: MailboxSession, vacationResponseSetRequest: VacationResponseSetRequest): SMono[VacationResponseUpdateResults] =
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
      .reduce[VacationResponseUpdateResults](VacationResponseUpdateResults.empty())(VacationResponseUpdateResults.merge)

  private def update(validatedPatch: VacationPatch, mailboxSession: MailboxSession): SMono[VacationResponseUpdateResult] =
    SMono.fromPublisher(
      vacationService.modifyVacation(toVacationAccountId(mailboxSession), validatedPatch))
      .`then`(SMono.just(VacationResponseUpdateSuccess))

  private def toVacationAccountId(mailboxSession: MailboxSession): VacationAccountId = VacationAccountId.fromUsername(mailboxSession.getUser)

  private def createResponse(invocation: Invocation,
                             vacationResponseSetRequest: VacationResponseSetRequest,
                             updateResult: VacationResponseUpdateResults): Invocation = {
    val response = VacationResponseSetResponse(
      accountId = vacationResponseSetRequest.accountId,
      newState = UuidState.INSTANCE,
      updated = Some(updateResult.updateSuccess).filter(_.nonEmpty),
      notUpdated = Some(updateResult.updateFailures).filter(_.nonEmpty),
      notCreated = validateNoCreate(vacationResponseSetRequest),
      notDestroyed = validateNoDestroy(vacationResponseSetRequest))

    Invocation(methodName,
      Arguments(VacationSerializer.serialize(response).as[JsObject]),
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