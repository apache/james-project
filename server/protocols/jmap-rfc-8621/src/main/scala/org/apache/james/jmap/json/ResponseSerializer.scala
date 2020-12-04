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

package org.apache.james.jmap.json

import java.io.InputStream
import java.net.URL
import java.util.UUID

import eu.timepit.refined.refineV
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.james.core.Username
import org.apache.james.jmap.core
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Account, Invocation, Session, _}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.{Seq => LegacySeq}
import scala.language.implicitConversions
import scala.util.Try

object ResponseSerializer {
  // CreateIds
  private implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]
  private implicit val serverIdFormat: Format[ServerId] = Json.valueFormat[ServerId]

  private implicit val createdIdsIdWrites: Writes[Map[ClientId, ServerId]] =
    mapWrites[ClientId, ServerId](_.value.value, serverIdFormat)

  private implicit val createdIdsIdRead: Reads[Map[ClientId, ServerId]] =
    Reads.mapReads[ClientId, ServerId] { clientIdString => refineV[IdConstraint](clientIdString).fold(JsError(_), id => JsSuccess(ClientId(id)))}
  private implicit val createdIdsFormat: Format[CreatedIds] = Json.valueFormat[CreatedIds]

  // Invocation
  private implicit val methodNameFormat: Format[MethodName] = Json.valueFormat[MethodName]
  private implicit val argumentFormat: Format[Arguments] = Json.valueFormat[Arguments]
  private implicit val methodCallIdFormat: Format[MethodCallId] = Json.valueFormat[MethodCallId]
  private implicit val invocationRead: Reads[Invocation] = (
    (JsPath \ core.Invocation.METHOD_NAME).read[MethodName] and
      (JsPath \ core.Invocation.ARGUMENTS).read[Arguments] and
      (JsPath \ core.Invocation.METHOD_CALL).read[MethodCallId]
    ) (core.Invocation.apply _)

  private implicit val invocationWrite: Writes[Invocation] = (invocation: Invocation) =>
    Json.arr(invocation.methodName, invocation.arguments, invocation.methodCallId)
  private implicit val statusWrite: Writes[HttpResponseStatus] = status => JsNumber(status.code())

  // RequestObject
  private implicit val requestObjectRead: Format[RequestObject] = Json.format[RequestObject]

  private implicit val stateReads: Reads[State] = {
    case JsString(underlying) => Try(UUID.fromString(underlying))
      .fold(e => JsError(e.getMessage), value => JsSuccess(State(value)))
  }
  private implicit val stateWrites: Writes[State] = Json.valueWrites[State]
  // ResponseObject
  private implicit val responseObjectFormat: Format[ResponseObject] = Json.format[ResponseObject]

  private implicit val maxSizeUploadWrites: Writes[MaxSizeUpload] = Json.valueWrites[MaxSizeUpload]
  private implicit val maxConcurrentUploadWrites: Writes[MaxConcurrentUpload] = Json.valueWrites[MaxConcurrentUpload]
  private implicit val maxSizeRequestWrites: Writes[MaxSizeRequest] = Json.valueWrites[MaxSizeRequest]
  private implicit val maxConcurrentRequestsWrites: Writes[MaxConcurrentRequests] = Json.valueWrites[MaxConcurrentRequests]
  private implicit val maxCallsInRequestWrites: Writes[MaxCallsInRequest] = Json.valueWrites[MaxCallsInRequest]
  private implicit val maxObjectsInGetWrites: Writes[MaxObjectsInGet] = Json.valueWrites[MaxObjectsInGet]
  private implicit val maxObjectsInSetWrites: Writes[MaxObjectsInSet] = Json.valueWrites[MaxObjectsInSet]

  private implicit val maxMailboxesPerEmailWrites: Writes[MaxMailboxesPerEmail] = Json.valueWrites[MaxMailboxesPerEmail]
  private implicit val maxMailboxDepthWrites: Writes[MaxMailboxDepth] = Json.valueWrites[MaxMailboxDepth]
  private implicit val maxSizeMailboxNameWrites: Writes[MaxSizeMailboxName] = Json.valueWrites[MaxSizeMailboxName]
  private implicit val maxSizeAttachmentsPerEmailWrites: Writes[MaxSizeAttachmentsPerEmail] = Json.valueWrites[MaxSizeAttachmentsPerEmail]
  private implicit val mayCreateTopLevelMailboxWrites: Writes[MayCreateTopLevelMailbox] = Json.valueWrites[MayCreateTopLevelMailbox]

  private implicit val usernameWrites: Writes[Username] = username => JsString(username.asString)
  private implicit val urlWrites: Writes[URL] = url => JsString(url.toString)
  private implicit val coreCapabilityWrites: Writes[CoreCapabilityProperties] = Json.writes[CoreCapabilityProperties]
  private implicit val mailCapabilityWrites: Writes[MailCapabilityProperties] = Json.writes[MailCapabilityProperties]
  private implicit val maxDelayedSendWrites: Writes[MaxDelayedSend] = Json.valueWrites[MaxDelayedSend]
  private implicit val ehloNameWrites: Writes[EhloName] = Json.valueWrites[EhloName]
  private implicit val ehloArgsWrites: Writes[EhloArgs] = Json.valueWrites[EhloArgs]
  private implicit val submissionPropertiesWrites: Writes[SubmissionProperties] = Json.writes[SubmissionProperties]
  private implicit val quotaCapabilityWrites: Writes[QuotaCapabilityProperties] = OWrites[QuotaCapabilityProperties](_ => Json.obj())
  private implicit val sharesCapabilityWrites: Writes[SharesCapabilityProperties] = OWrites[SharesCapabilityProperties](_ => Json.obj())
  private implicit val vacationResponseCapabilityWrites: Writes[VacationResponseCapabilityProperties] = OWrites[VacationResponseCapabilityProperties](_ => Json.obj())
  private implicit val submissionCapabilityWrites: Writes[SubmissionCapability] = OWrites[SubmissionCapability](_ => Json.obj())

  private implicit val setCapabilityWrites: Writes[Set[_ <: Capability]] =
    (set: Set[_ <: Capability]) => {
      set.foldLeft(JsObject.empty)((jsObject, capability) => {
        capability match {
          case capability: CoreCapability =>
            jsObject.+(capability.identifier.value, coreCapabilityWrites.writes(capability.properties))
          case capability: MailCapability =>
            jsObject.+(capability.identifier.value, mailCapabilityWrites.writes(capability.properties))
          case capability: QuotaCapability =>
            jsObject.+(capability.identifier.value, quotaCapabilityWrites.writes(capability.properties))
          case capability: SharesCapability =>
            jsObject.+(capability.identifier.value, sharesCapabilityWrites.writes(capability.properties))
          case capability: VacationResponseCapability =>
            jsObject.+(capability.identifier.value, vacationResponseCapabilityWrites.writes(capability.properties))
          case capability: SubmissionCapability =>
            jsObject.+(capability.identifier.value, submissionPropertiesWrites.writes(capability.properties))
          case _ => jsObject
        }
      })
    }

  private implicit val capabilitiesWrites: Writes[Capabilities] = capabilities => setCapabilityWrites.writes(capabilities.toSet)

  private implicit val identifierMapWrite: Writes[Map[CapabilityIdentifier, AccountId]] =
    mapWrites[CapabilityIdentifier, AccountId](_.value, accountIdWrites)

  private implicit val isPersonalFormat: Format[IsPersonal] = Json.valueFormat[IsPersonal]
  private implicit val isReadOnlyFormat: Format[IsReadOnly] = Json.valueFormat[IsReadOnly]
  private implicit val accountWrites: Writes[Account] = (
      (JsPath \ Account.NAME).write[Username] and
      (JsPath \ Account.IS_PERSONAL).write[IsPersonal] and
      (JsPath \ Account.IS_READ_ONLY).write[IsReadOnly] and
      (JsPath \ Account.ACCOUNT_CAPABILITIES).write[Set[_ <: Capability]]
    ) (unlift(Account.unapplyIgnoreAccountId))

  private implicit val accountListWrites: Writes[List[Account]] =
    (list: List[Account]) => JsObject(list.map(account => (account.accountId.id.value, accountWrites.writes(account))))

  private implicit val sessionWrites: Writes[Session] = Json.writes[Session]


  private implicit val propertiesWrites: Writes[Properties] = Json.valueWrites[Properties]

  private implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]

  private implicit val mailboxSetErrorWrites: Writes[SetError] = Json.writes[SetError]

  private implicit val jsonValidationErrorWrites: Writes[JsonValidationError] = error => JsString(error.message)

  private implicit val jsonValidationErrorsWrites: Writes[LegacySeq[JsonValidationError]] =
    (errors: LegacySeq[JsonValidationError]) => {
      JsArray(errors.map(error => jsonValidationErrorWrites.writes(error)).toArray[JsValue])
    }

  private implicit val errorsWrites: Writes[LegacySeq[(JsPath, LegacySeq[JsonValidationError])]] =
    (errors: LegacySeq[(JsPath, LegacySeq[JsonValidationError])]) => {
      errors.foldLeft(JsArray.empty)((jsArray, jsError) => {
        val (path: JsPath, list: LegacySeq[JsonValidationError]) = jsError
        jsArray:+ JsObject(Seq(
          "path" -> JsString(path.toJsonString),
          "messages" -> jsonValidationErrorsWrites.writes(list)))
      })
    }

  private implicit val jsErrorWrites: Writes[JsError] = Json.writes[JsError]

  private implicit val problemDetailsWrites: Writes[ProblemDetails] = Json.writes[ProblemDetails]

  def serialize(session: Session): JsValue = Json.toJson(session)

  def serialize(requestObject: RequestObject): JsValue = Json.toJson(requestObject)

  def serialize(responseObject: ResponseObject): JsValue = Json.toJson(responseObject)

  def serialize(problemDetails: ProblemDetails): JsValue = Json.toJson(problemDetails)

  def serialize(errors: JsError): JsValue = Json.toJson(errors)

  def deserializeRequestObject(input: String): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeRequestObject(input: InputStream): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeResponseObject(input: String): JsResult[ResponseObject] = Json.parse(input).validate[ResponseObject]
}