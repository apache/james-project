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
import org.apache.james.core.Username
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{SetError, UuidState}
import org.apache.james.jmap.delegation.{DelegateCreationId, DelegateCreationRequest, DelegateCreationResponse, DelegateSetRequest, DelegateSetResponse, DelegationId}
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

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
  private implicit val delegateSetResponseWrites: OWrites[DelegateSetResponse] = Json.writes[DelegateSetResponse]

  private implicit val mapCreationRequestByDelegateCreationId: Reads[Map[DelegateCreationId, JsObject]] =
    Reads.mapReads[DelegateCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"delegate creationId needs to match id constraints: $e"),
        id => JsSuccess(DelegateCreationId(id)))
    }
  private implicit val delegateSetRequestReads: Reads[DelegateSetRequest] = Json.reads[DelegateSetRequest]
  private implicit val delegateCreationRequest: Reads[DelegateCreationRequest] = Json.reads[DelegateCreationRequest]

  def serializeDelegateSetResponse(response: DelegateSetResponse): JsObject = Json.toJsObject(response)
  def deserializeDelegateSetRequest(input: JsValue): JsResult[DelegateSetRequest] = Json.fromJson[DelegateSetRequest](input)
  def deserializeDelegateCreationRequest(input: JsValue): JsResult[DelegateCreationRequest] = Json.fromJson[DelegateCreationRequest](input)
}