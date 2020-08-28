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
import org.apache.james.jmap.api.vacation.{AccountId, VacationRepository}
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.mail.SetErrorDescription
import org.apache.james.jmap.method.VacationResponseSetMethod.VACATION_RESPONSE_PATCH_OBJECT_KEY
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.model.{Invocation, State}
import org.apache.james.jmap.routes.ProcessingContext
import org.apache.james.jmap.vacation.{VacationResponseSetError, VacationResponseSetRequest, VacationResponseSetResponse, VacationResponseUpdateResponse}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.util.date.ZonedDateTimeProvider
import org.reactivestreams.Publisher
import play.api.libs.json.{JsError, JsObject, JsSuccess}
import reactor.core.scala.publisher.SMono

sealed trait UpdateResult {
  def updated: Option[Map[String, VacationResponseUpdateResponse]]
  def notUpdated: Option[Map[String, VacationResponseSetError]]
}
case class UpdateSuccess() extends UpdateResult {
  override def updated: Option[Map[String, VacationResponseUpdateResponse]] = Some(Map(VACATION_RESPONSE_PATCH_OBJECT_KEY -> VacationResponseUpdateResponse(JsObject(Seq()))))

  override def notUpdated: Option[Map[String, VacationResponseSetError]] = None
}
case class UpdateFailure(exception: Throwable) extends UpdateResult {
  override def updated: Option[Map[String, VacationResponseUpdateResponse]] = None

  override def notUpdated: Option[Map[String, VacationResponseSetError]] = Some(Map(VACATION_RESPONSE_PATCH_OBJECT_KEY -> asSetError(exception)))

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
                       processingContext: ProcessingContext): Publisher[Invocation] = {
    metricFactory.decoratePublisherWithTimerMetricLogP99(JMAP_RFC8621_PREFIX + methodName.value,
    asVacationResponseSetRequest(invocation.arguments)
      .flatMap(vacationResponseSetRequest => update(mailboxSession, vacationResponseSetRequest)
        .map(updateResult => createResponse(invocation, vacationResponseSetRequest, updateResult))))
  }

  private def update(mailboxSession: MailboxSession, vacationResponseSetRequest: VacationResponseSetRequest): SMono[UpdateResult] = {
    vacationResponseSetRequest.parsePatch()
      .flatMap(patch => patch.asVacationPatch)
      .fold(e => SMono.just(UpdateFailure(e)),
        patch => SMono.fromPublisher(vacationRepository.modifyVacation(toVacationAccountId(mailboxSession), patch))
          .`then`(SMono.just(UpdateSuccess())))
  }

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
                             updateResult: UpdateResult): Invocation = {
    val response = VacationResponseSetResponse(
      accountId = vacationResponseSetRequest.accountId,
      newState = State.INSTANCE,
      updated = updateResult.updated,
      notUpdated = updateResult.notUpdated)

    Invocation(methodName,
      Arguments(serializer.serialize(response).as[JsObject]),
      invocation.methodCallId)
  }
}