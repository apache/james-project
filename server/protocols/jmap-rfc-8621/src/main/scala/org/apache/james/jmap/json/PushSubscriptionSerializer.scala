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

import eu.timepit.refined.refineV
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscriptionCreationRequest, PushSubscriptionExpiredTime, PushSubscriptionId, PushSubscriptionKeys, PushSubscriptionServerURL, TypeName}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{PushSubscriptionCreationId, PushSubscriptionCreationResponse, PushSubscriptionSetRequest, PushSubscriptionSetResponse, SetError}
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

class PushSubscriptionSerializer {
  private implicit val pushSubscriptionIdWrites: Writes[PushSubscriptionId] = Json.valueWrites[PushSubscriptionId]

  private implicit val pushSubscriptionExpiredTimeFormat: Format[PushSubscriptionExpiredTime] = Json.valueFormat[PushSubscriptionExpiredTime]
  private implicit val deviceClientIdReads: Reads[DeviceClientId] = Json.valueReads[DeviceClientId]
  private implicit val pushSubscriptionServerURLReads: Reads[PushSubscriptionServerURL] = Json.valueReads[PushSubscriptionServerURL]
  private implicit val pushSubscriptionKeysReads: Reads[PushSubscriptionKeys] = Json.valueReads[PushSubscriptionKeys]
  private implicit val typeNameReads: Reads[TypeName] = Json.valueReads[TypeName]

  implicit val pushSubscriptionCreationRequest: Reads[PushSubscriptionCreationRequest] = Json.reads[PushSubscriptionCreationRequest]

  private implicit val mapCreationRequestByPushSubscriptionCreationId: Reads[Map[PushSubscriptionCreationId, JsObject]] =
    Reads.mapReads[PushSubscriptionCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"mailbox creationId needs to match id contraints: $e"),
        id => JsSuccess(PushSubscriptionCreationId(id))) }

  private implicit val pushSubscriptionSetRequestReads: Reads[PushSubscriptionSetRequest] = Json.reads[PushSubscriptionSetRequest]

  private implicit val pushSubscriptionCreationResponseWrites: Writes[PushSubscriptionCreationResponse] = Json.writes[PushSubscriptionCreationResponse]

  private implicit val pushSubscriptionMapSetErrorForCreationWrites: Writes[Map[PushSubscriptionCreationId, SetError]] =
    mapWrites[PushSubscriptionCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val pushSubscriptionMapCreationResponseWrites: Writes[Map[PushSubscriptionCreationId, PushSubscriptionCreationResponse]] =
    mapWrites[PushSubscriptionCreationId, PushSubscriptionCreationResponse](_.id.value, pushSubscriptionCreationResponseWrites)

  private implicit val emailResponseSetWrites: OWrites[PushSubscriptionSetResponse] = Json.writes[PushSubscriptionSetResponse]

  def deserializePushSubscriptionSetRequest(input: JsValue): JsResult[PushSubscriptionSetRequest] = Json.fromJson[PushSubscriptionSetRequest](input)

  def serialize(response: PushSubscriptionSetResponse): JsObject = Json.toJsObject(response)
}
