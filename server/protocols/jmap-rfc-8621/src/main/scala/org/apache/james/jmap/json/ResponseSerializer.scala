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

import eu.timepit.refined.refineV
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.james.core.Username
import org.apache.james.jmap.core
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Account, AccountId, Capabilities, Capability, ClientId, CoreCapabilityProperties, CreatedIds, EhloArg, EhloArgs, EhloName, Invocation, IsPersonal, IsReadOnly, MailCapabilityProperties, MaxCallsInRequest, MaxConcurrentRequests, MaxConcurrentUpload, MaxDelayedSend, MaxMailboxDepth, MaxMailboxesPerEmail, MaxObjectsInGet, MaxObjectsInSet, MaxSizeAttachmentsPerEmail, MaxSizeMailboxName, MaxSizeRequest, MaxSizeUpload, MayCreateTopLevelMailbox, ProblemDetails, Properties, RequestObject, ResponseObject, ServerId, Session, SetError, SubmissionProperties, SupportsPush, UuidState, WebSocketCapabilityProperties, URL}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.{Seq => LegacySeq}
import scala.language.implicitConversions

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
    JsArray(Seq(JsString(invocation.methodName.value.value), invocation.arguments.value, JsString(invocation.methodCallId.value.value)))
  private implicit val statusWrite: Writes[HttpResponseStatus] = status => JsNumber(status.code())

  // RequestObject
  private implicit val requestObjectRead: Format[RequestObject] = Json.format[RequestObject]

  // ResponseObject
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val responseObjectFormat: OFormat[ResponseObject] = Json.format[ResponseObject]

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
  private implicit val urlWrites: Writes[URL] = url => JsString(url.value)
  val coreCapabilityWrites: OWrites[CoreCapabilityProperties] = Json.writes[CoreCapabilityProperties]
  val mailCapabilityWrites: OWrites[MailCapabilityProperties] = Json.writes[MailCapabilityProperties]
  private implicit val maxDelayedSendWrites: Writes[MaxDelayedSend] = Json.valueWrites[MaxDelayedSend]
  private implicit val ehloNameWrites: Writes[EhloName] = Json.valueWrites[EhloName]
  private implicit val ehloArgWrites: Writes[EhloArg] = Json.valueWrites[EhloArg]
  private implicit val ehloArgsWrites: Writes[EhloArgs] = Json.valueWrites[EhloArgs]
  private implicit val ehloArgsMapWrite: Writes[Map[EhloName, EhloArgs]] =
    mapWrites[EhloName, EhloArgs](_.value, ehloArgsWrites)
  private implicit val supportsPushWrites: Writes[SupportsPush] = Json.valueWrites[SupportsPush]
  val submissionPropertiesWrites: OWrites[SubmissionProperties] = Json.writes[SubmissionProperties]
  val webSocketPropertiesWrites: OWrites[WebSocketCapabilityProperties] = Json.writes[WebSocketCapabilityProperties]

  private implicit val setCapabilityWrites: Writes[Set[_ <: Capability]] =
    (set: Set[_ <: Capability]) =>
      JsObject(set
        .map(capability => (capability.identifier().value, capability.properties().jsonify()))
        .toMap)

  private implicit val capabilitiesWrites: Writes[Capabilities] = capabilities => setCapabilityWrites.writes(capabilities.capabilities)

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

  private implicit val problemDetailsWrites: OWrites[ProblemDetails] = Json.writes[ProblemDetails]


  def serialize(session: Session): JsValue = Json.toJson(session)

  def serialize(requestObject: RequestObject): JsValue = Json.toJson(requestObject)

  def serialize(responseObject: ResponseObject): JsObject = Json.toJsObject(responseObject)

  def serialize(problemDetails: ProblemDetails): JsObject = Json.toJsObject(problemDetails)

  def serialize(errors: JsError): JsValue = Json.toJson(errors)

  def asException(errors: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])]): IllegalArgumentException = new IllegalArgumentException(serialize(JsError(errors)).toString())

  def deserializeRequestObject(input: String): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeRequestObject(input: InputStream): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeRequestObject(js: JsValue): JsResult[RequestObject] = js.validate[RequestObject]

  def deserializeResponseObject(input: String): JsResult[ResponseObject] = Json.parse(input).validate[ResponseObject]
}