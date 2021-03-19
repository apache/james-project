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

import eu.timepit.refined.refineV
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.james.core.Username
import org.apache.james.jmap.change.{TypeName, TypeState}
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

  private implicit val stateWrites: Writes[State] = Json.valueWrites[State]
  // ResponseObject
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
  private implicit val urlWrites: Writes[URL] = url => JsString(url.toString)
  val coreCapabilityWrites: OWrites[CoreCapabilityProperties] = Json.writes[CoreCapabilityProperties]
  val mailCapabilityWrites: OWrites[MailCapabilityProperties] = Json.writes[MailCapabilityProperties]
  private implicit val maxDelayedSendWrites: Writes[MaxDelayedSend] = Json.valueWrites[MaxDelayedSend]
  private implicit val ehloNameWrites: Writes[EhloName] = Json.valueWrites[EhloName]
  private implicit val ehloArgsWrites: Writes[EhloArgs] = Json.valueWrites[EhloArgs]
  private implicit val supportsPushWrites: Writes[SupportsPush] = Json.valueWrites[SupportsPush]
  val submissionPropertiesWrites: OWrites[SubmissionProperties] = Json.writes[SubmissionProperties]
  val webSocketPropertiesWrites: OWrites[WebSocketCapabilityProperties] = Json.writes[WebSocketCapabilityProperties]

  private implicit val setCapabilityWrites: Writes[Set[_ <: Capability]] =
    (set: Set[_ <: Capability]) => {
      set.foldLeft(JsObject.empty)((jsObject, capability) =>
        jsObject.+(capability.identifier.value, capability.properties.jsonify))
    }

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

  private implicit val requestIdFormat: Format[RequestId] = Json.valueFormat[RequestId]
  private implicit val webSocketRequestReads: Reads[WebSocketRequest] = {
    case jsObject: JsObject =>
      for {
        requestId <- jsObject.value.get("requestId")
          .map(requestIdJson => requestIdFormat.reads(requestIdJson).map(Some(_)))
          .getOrElse(JsSuccess(None))
        request <- requestObjectRead.reads(jsObject)
      } yield {
        WebSocketRequest(requestId, request)
      }
    case _ => JsError("Expecting a JsObject to represent a webSocket inbound request")
  }
  private implicit val typeNameReads: Reads[TypeName] = {
    case JsString(string) => TypeName.parse(string)
      .fold(errorMessage => JsError(errorMessage), JsSuccess(_))
    case _ => JsError("Expecting a JsString as typeName")
  }
  private implicit val pushStateReads: Reads[PushState] = Json.valueReads[PushState]
  private implicit val webSocketPushEnableReads: Reads[WebSocketPushEnable] = Json.reads[WebSocketPushEnable]
  private implicit val webSocketInboundReads: Reads[WebSocketInboundMessage] = {
    case json: JsObject =>
      json.value.get("@type") match {
        case Some(JsString("Request")) => webSocketRequestReads.reads(json)
        case Some(JsString("WebSocketPushEnable")) => webSocketPushEnableReads.reads(json)
        case Some(JsString("WebSocketPushDisable")) => JsSuccess(WebSocketPushDisable)
        case Some(JsString(unknownType)) => JsError(s"Unknown @type field on a webSocket inbound message: $unknownType")
        case Some(invalidType) => JsError(s"Invalid @type field on a webSocket inbound message: expecting a JsString, got $invalidType")
        case None => JsError(s"Missing @type field on a webSocket inbound message")
      }
    case _ => JsError("Expecting a JsObject to represent a webSocket inbound message")
  }

  private implicit val typeStateMapWrites: Writes[Map[TypeName, State]] = mapWrites[TypeName, State](_.asString(), stateWrites)
  private implicit val typeStateWrites: Writes[TypeState] = Json.valueWrites[TypeState]
  private implicit val changeWrites: OWrites[Map[AccountId, TypeState]] = mapWrites[AccountId, TypeState](_.id.value, typeStateWrites)
  private implicit val pushStateWrites: Writes[PushState] = Json.valueWrites[PushState]
  private implicit val stateChangeWrites: Writes[StateChange] = stateChange =>
    stateChange.pushState.map(pushState =>
      JsObject(Map(
        "@type" -> JsString("StateChange"),
        "changed" -> changeWrites.writes(stateChange.changes),
        "pushState" -> pushStateWrites.writes(pushState))))
      .getOrElse(
        JsObject(Map(
          "@type" -> JsString("StateChange"),
          "changed" -> changeWrites.writes(stateChange.changes))))

  private implicit val webSocketResponseWrites: Writes[WebSocketResponse] = response => {
    val apiResponseJson: JsObject = responseObjectFormat.writes(response.responseObject)
    JsObject(Map(
      "@type" -> JsString("Response"),
      "requestId" -> response.requestId.map(_.value).map(JsString).getOrElse(JsNull))
      ++ apiResponseJson.value)
  }
  private implicit val webSocketErrorWrites: Writes[WebSocketError] = error => {
    val errorJson: JsObject = problemDetailsWrites.writes(error.problemDetails)
    JsObject(Map(
      "@type" -> JsString("RequestError"),
      "requestId" -> error.requestId.map(_.value).map(JsString).getOrElse(JsNull))
      ++ errorJson.value)
  }
  private implicit val pingWrites: Writes[PingMessage] = Json.writes[PingMessage]
  private implicit val webSocketOutboundWrites: Writes[OutboundMessage] = {
    case pingMessage: PingMessage => pingWrites.writes(pingMessage)
    case stateChange: StateChange => stateChangeWrites.writes(stateChange)
    case response: WebSocketResponse => webSocketResponseWrites.writes(response)
    case error: WebSocketError => webSocketErrorWrites.writes(error)
  }

  def serialize(session: Session): JsValue = Json.toJson(session)

  def serialize(requestObject: RequestObject): JsValue = Json.toJson(requestObject)

  def serialize(responseObject: ResponseObject): JsValue = Json.toJson(responseObject)

  def serialize(problemDetails: ProblemDetails): JsValue = Json.toJson(problemDetails)

  def serialize(errors: JsError): JsValue = Json.toJson(errors)

  def serialize(outboundMessage: OutboundMessage): JsValue = Json.toJson(outboundMessage)

  def deserializeRequestObject(input: String): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeWebSocketInboundMessage(input: String): JsResult[WebSocketInboundMessage] = Try(Json.parse(input).validate[WebSocketInboundMessage])
    .fold(e => JsError(e.getMessage), result => result)

  def deserializeRequestObject(input: InputStream): JsResult[RequestObject] = Json.parse(input).validate[RequestObject]

  def deserializeResponseObject(input: String): JsResult[ResponseObject] = Json.parse(input).validate[ResponseObject]
}