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

import eu.timepit.refined
import eu.timepit.refined.refineV
import org.apache.james.core.Username
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, SetError, UuidState}
import org.apache.james.jmap.delegation.{Delegate, DelegateCreationId, DelegateCreationRequest, DelegateCreationResponse, DelegateGet, DelegateGetRequest, DelegateGetResponse, DelegateIds, DelegateNotFound, DelegateSetRequest, DelegateSetResponse, DelegatedAccountGet, DelegatedAccountGetRequest, DelegatedAccountGetResponse, DelegatedAccountNotFound, DelegatedAccountSetRequest, DelegatedAccountSetResponse, DelegationId, UnparsedDelegateId}
import play.api.libs.json.{Format, JsArray, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes, __}

object DelegationSerializer {
  private implicit val delegationIdFormat: Format[DelegationId] = Json.valueFormat[DelegationId]
  private implicit val userReads: Reads[Username] = {
    case JsString(userAsString) => JsSuccess(Username.of(userAsString))
    case _ => JsError("username needs to be represented by a JsString")
  }
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val delegateCreationIdWrites: Writes[DelegateCreationId] = Json.valueWrites[DelegateCreationId]
  private implicit val delegateCreationResponseWrites: Writes[DelegateCreationResponse] = Json.writes[DelegateCreationResponse]
  private implicit val delegateSetMapCreationResponseWrites: Writes[Map[DelegateCreationId, DelegateCreationResponse]] =
    mapWrites[DelegateCreationId, DelegateCreationResponse](_.serialize, delegateCreationResponseWrites)
  private implicit val delegateSetMapSetErrorForCreationWrites: Writes[Map[DelegateCreationId, SetError]] =
    mapWrites[DelegateCreationId, SetError](_.serialize, setErrorWrites)
  private implicit val delegationMapSetErrorWrites: Writes[Map[UnparsedDelegateId, SetError]] =
    mapWrites[UnparsedDelegateId, SetError](_.id.value, setErrorWrites)
  private implicit val delegateSetResponseWrites: OWrites[DelegateSetResponse] = Json.writes[DelegateSetResponse]
  private implicit val delegatedAccountSetResponseWrites: OWrites[DelegatedAccountSetResponse] = Json.writes[DelegatedAccountSetResponse]

  private implicit val mapCreationRequestByDelegateCreationId: Reads[Map[DelegateCreationId, JsObject]] =
    Reads.mapReads[DelegateCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"delegate creationId needs to match id constraints: $e"),
        id => JsSuccess(DelegateCreationId(id)))
    }
  private implicit val delegateCreationRequest: Reads[DelegateCreationRequest] = Json.reads[DelegateCreationRequest]
  private implicit val unparsedDelegateIdReads: Reads[UnparsedDelegateId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(e => JsError(s"delegate id does not match Id constraints: $e"),
        id => JsSuccess(UnparsedDelegateId(id)))
    case _ => JsError("delegate id needs to be represented by a JsString")
  }
  private implicit val delegateSetRequestReads: Reads[DelegateSetRequest] = Json.reads[DelegateSetRequest]
  private implicit val delegateIdsReads: Reads[DelegateIds] = Json.valueReads[DelegateIds]
  private implicit val delegateGetRequestReads: Reads[DelegateGetRequest] = Json.reads[DelegateGetRequest]
  private implicit val delegatedAccountGetRequestReads: Reads[DelegatedAccountGetRequest] = Json.reads[DelegatedAccountGetRequest]
  private implicit val delegatedAccountSetRequestReads: Reads[DelegatedAccountSetRequest] = Json.reads[DelegatedAccountSetRequest]
  private implicit val usernameWrites: Writes[Username] = username => JsString(username.asString)
  private implicit val delegateWrites: Writes[Delegate] = Json.writes[Delegate]
  private implicit val delegateNotFoundWrites: Writes[DelegateNotFound] =
    notFound => JsArray(notFound.value.toList.map(id => JsString(id.id.value)))
  private implicit val delegatedAccountNotFoundWrites: Writes[DelegatedAccountNotFound] =
    notFound => JsArray(notFound.value.toList.map(id => JsString(id.id.value)))
  private implicit val delegateGetResponseWrites: Writes[DelegateGetResponse] = Json.writes[DelegateGetResponse]
  private implicit val delegatedAccountGetResponseWrites: Writes[DelegatedAccountGetResponse] = Json.writes[DelegatedAccountGetResponse]

  def serializeDelegateSetResponse(response: DelegateSetResponse): JsObject = Json.toJsObject(response)
  def serializeDelegatedAccountSetResponse(response: DelegatedAccountSetResponse): JsObject = Json.toJsObject(response)
  def deserializeDelegateSetRequest(input: JsValue): JsResult[DelegateSetRequest] = Json.fromJson[DelegateSetRequest](input)
  def deserializeDelegateCreationRequest(input: JsValue): JsResult[DelegateCreationRequest] = Json.fromJson[DelegateCreationRequest](input)
  def deserializeDelegateGetRequest(input: JsValue): JsResult[DelegateGetRequest] = Json.fromJson[DelegateGetRequest](input)
  def deserializeDelegatedAccountGetRequest(input: JsValue): JsResult[DelegatedAccountGetRequest] = Json.fromJson[DelegatedAccountGetRequest](input)
  def deserializeDelegatedAccountSetRequest(input: JsValue): JsResult[DelegatedAccountSetRequest] = Json.fromJson[DelegatedAccountSetRequest](input)

  def serialize(delegateGetResponse: DelegateGetResponse, properties: Properties): JsValue =
    Json.toJson(delegateGetResponse)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject =>
            DelegateGet.propertiesFiltered(properties)
              .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def serialize(response: DelegatedAccountGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject =>
            DelegatedAccountGet.propertiesFiltered(properties)
              .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get
}