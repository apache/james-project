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

import javax.inject.Inject
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{State, TypeName}
import org.apache.james.jmap.change.TypeState
import org.apache.james.jmap.core.{AccountId, OutboundMessage, PingMessage, PushState, RequestId, StateChange, WebSocketError, WebSocketInboundMessage, WebSocketPushDisable, WebSocketPushEnable, WebSocketRequest, WebSocketResponse}
import org.apache.james.jmap.method.PushVerification
import play.api.libs.json.{Format, JsError, JsNull, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

import scala.util.Try

object PushSerializer {
  implicit val pushVerificationWrites: Writes[PushVerification] = (pushVerification: PushVerification) => Json.obj(
    "@type" -> "PushVerification",
    "pushSubscriptionId" -> pushVerification.pushSubscriptionId.value,
    "verificationCode" -> pushVerification.verificationCode.value)

  def serializePushVerification(pushVerification: PushVerification): JsValue =
    pushVerificationWrites.writes(pushVerification)
}

case class PushSerializer @Inject()(typeStateFactory: TypeStateFactory) {
  private implicit val stateWrites: Writes[State] = state => JsString(state.serialize)

  private implicit val requestIdFormat: Format[RequestId] = Json.valueFormat[RequestId]
  private implicit val webSocketRequestReads: Reads[WebSocketRequest] = {
    case jsObject: JsObject =>
      for {
        requestId <- jsObject.value.get("id")
          .map(requestIdJson => requestIdFormat.reads(requestIdJson).map(Some(_)))
          .getOrElse(JsSuccess(None))
        request <- ResponseSerializer.deserializeRequestObject(jsObject)
      } yield {
        WebSocketRequest(requestId, request)
      }
    case _ => JsError("Expecting a JsObject to represent a webSocket inbound request")
  }
  private implicit val typeNameReads: Reads[TypeName] = {
    case JsString(string) => typeStateFactory.parse(string)
      .fold(throwable => JsError(throwable.getMessage), JsSuccess(_))
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
  private val stateChangeWritesNoPushState: Writes[StateChange] = stateChange =>
    JsObject(Map(
      "@type" -> JsString("StateChange"),
      "changed" -> changeWrites.writes(stateChange.changes)))

  private implicit val webSocketResponseWrites: Writes[WebSocketResponse] = response => {
    val apiResponseJson: JsObject = ResponseSerializer.serialize(response.responseObject)
    JsObject(Map(
      "@type" -> JsString("Response"),
      "requestId" -> response.requestId.map(_.value).map(JsString).getOrElse(JsNull))
      ++ apiResponseJson.value)
  }
  private implicit val webSocketErrorWrites: Writes[WebSocketError] = error => {
    val errorJson: JsObject = ResponseSerializer.serialize(error.problemDetails)
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
  private val sseOutboundWrites: Writes[OutboundMessage] = {
    case pingMessage: PingMessage => pingWrites.writes(pingMessage)
    case stateChange: StateChange => stateChangeWritesNoPushState.writes(stateChange)
    case response: WebSocketResponse => webSocketResponseWrites.writes(response)
    case error: WebSocketError => webSocketErrorWrites.writes(error)
  }

  def serialize(outboundMessage: OutboundMessage): JsValue = Json.toJson(outboundMessage)

  def serializeSSE(outboundMessage: OutboundMessage): JsValue = sseOutboundWrites.writes(outboundMessage)

  def deserializeWebSocketInboundMessage(input: String): JsResult[WebSocketInboundMessage] = Try(Json.parse(input).validate[WebSocketInboundMessage])
    .fold(e => JsError(e.getMessage), result => result)
}
