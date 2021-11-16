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

import eu.timepit.refined.refineV
import org.apache.james.jmap.api.identity.IdentityCreationRequest
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, SetError, UuidState}
import org.apache.james.jmap.mail._
import play.api.libs.json.{Format, JsArray, JsError, JsObject, JsResult, JsSuccess, JsValue, Json, OWrites, Reads, Writes, __}

object IdentitySerializer {
  private implicit val emailerNameReads: Format[EmailerName] = Json.valueFormat[EmailerName]
  private implicit val identityIdFormat: Format[IdentityId] = Json.valueFormat[IdentityId]
  private implicit val identityIdUnparsedFormat: Format[UnparsedIdentityId] = Json.valueFormat[UnparsedIdentityId]
  private implicit val identityIdsFormat: Format[IdentityIds] = Json.valueFormat[IdentityIds]
  private implicit val emailAddressReads: Format[EmailAddress] = Json.format[EmailAddress]
  private implicit val nameWrites: Format[IdentityName] = Json.valueFormat[IdentityName]
  private implicit val textSignatureWrites: Format[TextSignature] = Json.valueFormat[TextSignature]
  private implicit val htmlSignatureWrites: Format[HtmlSignature] = Json.valueFormat[HtmlSignature]
  private implicit val mayDeleteWrites: Writes[MayDeleteIdentity] = Json.valueWrites[MayDeleteIdentity]
  private implicit val identityWrites: Writes[Identity] = Json.writes[Identity]
  private implicit val identityGetRequestReads: Reads[IdentityGetRequest] = Json.reads[IdentityGetRequest]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val identityGetResponseWrites: OWrites[IdentityGetResponse] = Json.writes[IdentityGetResponse]

  private implicit val identityCreationIdWrites: Writes[IdentityCreationId] = Json.valueWrites[IdentityCreationId]
  private implicit val identityCreationResponseWrites: Writes[IdentityCreationResponse] = Json.writes[IdentityCreationResponse]
  private implicit val identityMapCreationResponseWrites: Writes[Map[IdentityCreationId, IdentityCreationResponse]] =
    mapWrites[IdentityCreationId, IdentityCreationResponse](id => identityCreationIdWrites.writes(id).as[String], identityCreationResponseWrites)
  private implicit val identityMapSetErrorForCreationWrites: Writes[Map[IdentityCreationId, SetError]] =
    mapWrites[IdentityCreationId, SetError](_.serialise, setErrorWrites)
  private implicit val identitySetResponseWrites: OWrites[IdentitySetResponse] = Json.writes[IdentitySetResponse]

  private implicit val mapCreationRequestByIdentityCreationId: Reads[Map[IdentityCreationId, JsObject]] =
    Reads.mapReads[IdentityCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"identity creationId needs to match id constraints: $e"),
        id => JsSuccess(IdentityCreationId(id)))
    }
  private implicit val identitySetRequestReads: Reads[IdentitySetRequest] = Json.reads[IdentitySetRequest]
  private implicit val identityCreationRequest: Reads[IdentityCreationRequest] = Json.reads[IdentityCreationRequest]

  def serialize(response: IdentityGetResponse, properties: Properties): JsObject = Json.toJsObject(response)
    .transform((__ \ "list").json.update {
      case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
        case jsonObject: JsObject => properties.filter(jsonObject)
        case jsValue => jsValue
      }))
    }).get
  def serialize(response: IdentitySetResponse): JsObject = Json.toJsObject(response)

  def deserialize(input: JsValue): JsResult[IdentityGetRequest] = Json.fromJson[IdentityGetRequest](input)
  def deserializeIdentitySetRequest(input: JsValue): JsResult[IdentitySetRequest] = Json.fromJson[IdentitySetRequest](input)
  def deserializeIdentityCreationRequest(input: JsValue): JsResult[IdentityCreationRequest] = Json.fromJson[IdentityCreationRequest](input)
}
