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

import java.net.URL

import org.apache.james.core.Username
import org.apache.james.jmap.json.Serializer._
import org.apache.james.jmap.model
import org.apache.james.jmap.model.CreatedIds.{ClientId, ServerId}
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.{Account, CapabilityIdentifier, Invocation, Session, _}
import play.api.libs.functional.syntax._
import play.api.libs.json._

object Serializer {
  // CreateIds
  implicit val clientIdFormat: Format[ClientId] = Json.valueFormat[ClientId]
  implicit val serverIdFormat: Format[ServerId] = Json.valueFormat[ServerId]
  implicit val createdIdsFormat: Format[CreatedIds] = Json.valueFormat[CreatedIds]

  implicit def createdIdsIdWrites(implicit serverIdWriter: Writes[ServerId]): Writes[Map[ClientId, ServerId]] =
    (ids: Map[ClientId, ServerId]) => {
      JsObject(ids.map {
        case (clientId, serverId) => (clientId.value.value, serverIdWriter.writes(serverId))
      }.toSeq)
    }

  implicit def createdIdsIdRead(implicit serverIdReader: Reads[ServerId]): Reads[Map[ClientId, ServerId]] =
    Reads.mapReads[ClientId, ServerId] {
      clientIdString => Json.fromJson[ClientId](JsString(clientIdString))
    }

  // Invocation
  implicit val methodNameFormat: Format[MethodName] = Json.valueFormat[MethodName]
  implicit val argumentFormat: Format[Arguments] = Json.valueFormat[Arguments]
  implicit val methodCallIdFormat: Format[MethodCallId] = Json.valueFormat[MethodCallId]
  implicit val invocationRead: Reads[Invocation] = (
    (JsPath \ model.Invocation.METHOD_NAME).read[MethodName] and
      (JsPath \ model.Invocation.ARGUMENTS).read[Arguments] and
      (JsPath \ model.Invocation.METHOD_CALL).read[MethodCallId]
    ) (model.Invocation.apply _)

  implicit val invocationWrite: Writes[Invocation] = (invocation: Invocation) =>
    Json.arr(invocation.methodName, invocation.arguments, invocation.methodCallId)

  // RequestObject
  implicit val capabilityIdentifierWrites: Format[CapabilityIdentifier] = Json.valueFormat[CapabilityIdentifier]
  implicit val requestObjectRead: Format[RequestObject] = Json.format[RequestObject]

  // ResponseObject
  implicit val responseObjectFormat: Format[ResponseObject] = Json.format[ResponseObject]

  implicit val maxSizeUploadWrites: Writes[MaxSizeUpload] = Json.valueWrites[MaxSizeUpload]
  implicit val maxConcurrentUploadWrites: Writes[MaxConcurrentUpload] = Json.valueWrites[MaxConcurrentUpload]
  implicit val maxSizeRequestWrites: Writes[MaxSizeRequest] = Json.valueWrites[MaxSizeRequest]
  implicit val maxConcurrentRequestsWrites: Writes[MaxConcurrentRequests] = Json.valueWrites[MaxConcurrentRequests]
  implicit val maxCallsInRequestWrites: Writes[MaxCallsInRequest] = Json.valueWrites[MaxCallsInRequest]
  implicit val maxObjectsInGetWrites: Writes[MaxObjectsInGet] = Json.valueWrites[MaxObjectsInGet]
  implicit val maxObjectsInSetWrites: Writes[MaxObjectsInSet] = Json.valueWrites[MaxObjectsInSet]
  implicit val collationAlgorithmsWrites: Writes[CollationAlgorithms] = Json.valueWrites[CollationAlgorithms]

  implicit val maxMailboxesPerEmailWrites: Writes[MaxMailboxesPerEmail] = Json.valueWrites[MaxMailboxesPerEmail]
  implicit val maxMailboxDepthWrites: Writes[MaxMailboxDepth] = Json.valueWrites[MaxMailboxDepth]
  implicit val maxSizeMailboxNameWrites: Writes[MaxSizeMailboxName] = Json.valueWrites[MaxSizeMailboxName]
  implicit val maxSizeAttachmentsPerEmailWrites: Writes[MaxSizeAttachmentsPerEmail] = Json.valueWrites[MaxSizeAttachmentsPerEmail]
  implicit val emailQuerySortOptionsWrites: Writes[EmailQuerySortOptions] = Json.valueWrites[EmailQuerySortOptions]
  implicit val mayCreateTopLevelMailboxWrites: Writes[MayCreateTopLevelMailbox] = Json.valueWrites[MayCreateTopLevelMailbox]

  implicit val usernameWrites: Writes[Username] = username => JsString(username.asString)
  implicit val urlWrites: Writes[URL] = url => JsString(url.toString)
  implicit val coreCapabilityWrites: Writes[CoreCapabilityProperties] = Json.writes[CoreCapabilityProperties]
  implicit val mailCapabilityWrites: Writes[MailCapabilityProperties] = Json.writes[MailCapabilityProperties]

  implicit def setCapabilityWrites(implicit corePropertiesWriter: Writes[CoreCapabilityProperties],
                                   mailCapabilityWrites: Writes[MailCapabilityProperties]): Writes[Set[_ <: Capability]] =
    (set: Set[_ <: Capability]) => {
      JsObject(set.map {
        case capability: CoreCapability => (
          capability.identifier.asString, corePropertiesWriter.writes(capability.properties))
        case capability: MailCapability => (
          capability.identifier.asString, mailCapabilityWrites.writes(capability.properties))
      }.toSeq)
    }

  implicit val capabilitiesWrites: Writes[Capabilities] = capabilities => setCapabilityWrites.writes(Set(capabilities.coreCapability, capabilities.mailCapability))

  implicit def identifierMapWrite[Any](implicit idWriter: Writes[Id]): Writes[Map[CapabilityIdentifier, Any]] =
    (m: Map[CapabilityIdentifier, Any]) => {
      JsObject(
        m.map {
          case (identifier, id: Id) => (identifier.asString, idWriter.writes(id))
          case _ => throw new RuntimeException("non supported serializer")
        }.toSeq
      )
    }



  implicit val accountWrites: Writes[Account] = Json.writes[Account]
  implicit val sessionWrites: Writes[Session] = Json.writes[Session]
}

class Serializer {
  def serialize(session: Session): String = {
    Json.stringify(Json.toJson(session))
  }

  def serialize(requestObject: RequestObject): String = {
    Json.stringify(Json.toJson(requestObject))
  }

  def serialize(responseObject: ResponseObject): String = {
    Json.stringify(Json.toJson(responseObject))
  }

  def deserializeRequestObject(input: String): JsResult[RequestObject] = {
    Json.parse(input).validate[RequestObject]
  }

  def deserializeResponseObject(input: String): JsResult[ResponseObject] = {
    Json.parse(input).validate[ResponseObject]
  }
}
