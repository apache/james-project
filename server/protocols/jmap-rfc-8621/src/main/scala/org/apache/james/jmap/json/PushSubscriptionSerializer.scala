/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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

import javax.inject.Inject
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscriptionCreationRequest, PushSubscriptionExpiredTime, PushSubscriptionId, PushSubscriptionKeys, PushSubscriptionServerURL, TypeName}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{PushSubscriptionCreationId, PushSubscriptionCreationResponse, PushSubscriptionPatchObject, PushSubscriptionSetRequest, PushSubscriptionSetResponse, PushSubscriptionUpdateResponse, SetError, UnparsedPushSubscriptionId}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsError, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

class PushSubscriptionSerializer @Inject()(typeStateFactory: TypeStateFactory) {
  private implicit val pushSubscriptionIdWrites: Writes[PushSubscriptionId] = Json.valueWrites[PushSubscriptionId]

  private implicit val pushSubscriptionExpiredTimeFormat: Format[PushSubscriptionExpiredTime] = Json.valueFormat[PushSubscriptionExpiredTime]
  private implicit val deviceClientIdReads: Reads[DeviceClientId] = Json.valueReads[DeviceClientId]
  private implicit val pushSubscriptionServerURLReads: Reads[PushSubscriptionServerURL] = {
    case JsString(serializeURL) => PushSubscriptionServerURL.from(serializeURL).map(JsSuccess(_)).getOrElse(JsError())
    case _ => JsError()
  }
  private implicit val patchObject: Reads[PushSubscriptionPatchObject] = Json.valueReads[PushSubscriptionPatchObject]

  private implicit val unparsedPushSubscriptionIdReads: Reads[UnparsedPushSubscriptionId] = {
    case JsString(string) =>
      refined.refineV[IdConstraint](string)
        .fold(
          e => JsError(s"pushSubscriptionId does not match Id constraints: $e"),
          id => JsSuccess(UnparsedPushSubscriptionId(id)))
    case _ => JsError("pushSubscriptionId needs to be represented by a JsString")
  }

  private implicit val pushSubscriptionKeysReads: Reads[PushSubscriptionKeys] = (
      (JsPath \ "p256dh").read[String] and
        (JsPath \ "auth").read[String]
      ) (PushSubscriptionKeys.apply _)

  private implicit val typeNameReads: Reads[TypeName] = {
    case JsString(serializeValue) => typeStateFactory.parse(serializeValue)
      .fold(e => JsError(e.getMessage), v => JsSuccess(v))
    case _ => JsError()
  }

  implicit val pushSubscriptionCreationRequest: Reads[PushSubscriptionCreationRequest] = Json.reads[PushSubscriptionCreationRequest]

  private implicit val mapCreationRequestByPushSubscriptionCreationId: Reads[Map[PushSubscriptionCreationId, JsObject]] =
    Reads.mapReads[PushSubscriptionCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"mailbox creationId needs to match id constraints: $e"),
        id => JsSuccess(PushSubscriptionCreationId(id)))
    }

  private implicit val mapUpdateRequestByPushSubscriptionCreationId: Reads[Map[UnparsedPushSubscriptionId, PushSubscriptionPatchObject]] =
    Reads.mapReads[UnparsedPushSubscriptionId, PushSubscriptionPatchObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"PushSubscription Id needs to match id constraints: $e"),
        id => JsSuccess(UnparsedPushSubscriptionId(id)))
    }

  private implicit val pushSubscriptionSetRequestReads: Reads[PushSubscriptionSetRequest] = Json.reads[PushSubscriptionSetRequest]

  private implicit val pushSubscriptionCreationResponseWrites: Writes[PushSubscriptionCreationResponse] = Json.writes[PushSubscriptionCreationResponse]
  private implicit val pushSubscriptionUpdateResponseWrites: Writes[PushSubscriptionUpdateResponse] = Json.valueWrites[PushSubscriptionUpdateResponse]

  private implicit val pushSubscriptionMapSetErrorForCreationWrites: Writes[Map[PushSubscriptionCreationId, SetError]] =
    mapWrites[PushSubscriptionCreationId, SetError](_.serialise, setErrorWrites)

  private implicit val pushSubscriptionMapSetErrorForUpdateWrites: Writes[Map[UnparsedPushSubscriptionId, SetError]] =
    mapWrites[UnparsedPushSubscriptionId, SetError](_.serialise, setErrorWrites)

  private implicit val pushSubscriptionMapCreationResponseWrites: Writes[Map[PushSubscriptionCreationId, PushSubscriptionCreationResponse]] =
    mapWrites[PushSubscriptionCreationId, PushSubscriptionCreationResponse](_.serialise, pushSubscriptionCreationResponseWrites)

  private implicit val pushSubscriptionMapUpdateResponseWrites: Writes[Map[PushSubscriptionId, PushSubscriptionUpdateResponse]] =
    mapWrites[PushSubscriptionId, PushSubscriptionUpdateResponse](_.serialise, pushSubscriptionUpdateResponseWrites)

  private implicit val pushSubscriptionResponseSetWrites: OWrites[PushSubscriptionSetResponse] = Json.writes[PushSubscriptionSetResponse]

  def deserializePushSubscriptionSetRequest(input: JsValue): JsResult[PushSubscriptionSetRequest] = Json.fromJson[PushSubscriptionSetRequest](input)

  def deserializePushSubscriptionCreationRequest(input: JsValue): JsResult[PushSubscriptionCreationRequest] = Json.fromJson[PushSubscriptionCreationRequest](input)

  def serialize(response: PushSubscriptionSetResponse): JsObject = Json.toJsObject(response)
}
