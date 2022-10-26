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
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, UuidState}
import org.apache.james.jmap.mail.{DataType, JmapQuota, QuotaChangesRequest, QuotaChangesResponse, QuotaDescription, QuotaGetRequest, QuotaGetResponse, QuotaIds, QuotaName, QuotaNotFound, ResourceType, Scope, UnparsedQuotaId}
import play.api.libs.json._

object QuotaSerializer {

  private implicit val unparsedQuotaIdWrites: Writes[UnparsedQuotaId] = Json.valueWrites[UnparsedQuotaId]
  private implicit val unparsedQuotaIdReads: Reads[UnparsedQuotaId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"vacation response id does not match Id constraints: $e"),
        id => JsSuccess(UnparsedQuotaId(id)))
    case _ => JsError("vacation response id needs to be represented by a JsString")
  }
  private implicit val quotaIdsReads: Reads[QuotaIds] = Json.valueReads[QuotaIds]

  private implicit val quotaGetRequestReads: Reads[QuotaGetRequest] = Json.reads[QuotaGetRequest]

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]

  private implicit val resourceTypeWrite: Writes[ResourceType] = resourceType => JsString(resourceType.asString())
  private implicit val scopeWrites: Writes[Scope] = scope => JsString(scope.asString())
  private implicit val dataTypeWrites: Writes[DataType] = dataType => JsString(dataType.asString())
  private implicit val quotaNameWrites: Writes[QuotaName] = Json.valueWrites[QuotaName]
  private implicit val quotaDescriptionWrites: Writes[QuotaDescription] = Json.valueWrites[QuotaDescription]

  private implicit val jmapQuotaWrites: Writes[JmapQuota] = Json.writes[JmapQuota]

  private implicit val quotaNotFoundWrites: Writes[QuotaNotFound] =
    notFound => JsArray(notFound.value.toList.map(id => JsString(id.id.value)))
  private implicit val quotaGetResponseWrites: Writes[QuotaGetResponse] = Json.writes[QuotaGetResponse]

  private implicit val quotaChangesRequestReads: Reads[QuotaChangesRequest] = Json.reads[QuotaChangesRequest]

  private implicit val quotaChangesResponseWrites: Writes[QuotaChangesResponse] = response =>
    Json.obj(
      "accountId" -> response.accountId,
      "oldState" -> response.oldState,
      "newState" -> response.newState,
      "hasMoreChanges" -> response.hasMoreChanges,
      "updatedProperties" -> response.updatedProperties,
      "created" -> response.created,
      "updated" -> response.updated,
      "destroyed" -> response.destroyed)

  def deserializeQuotaGetRequest(input: String): JsResult[QuotaGetRequest] = Json.parse(input).validate[QuotaGetRequest]

  def deserializeQuotaGetRequest(input: JsValue): JsResult[QuotaGetRequest] = Json.fromJson[QuotaGetRequest](input)

  def deserializeQuotaChangesRequest(input: JsValue): JsResult[QuotaChangesRequest] = Json.fromJson[QuotaChangesRequest](input)

  def serialize(quotaGetResponse: QuotaGetResponse, properties: Properties): JsValue =
    Json.toJson(quotaGetResponse)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject =>
            JmapQuota.propertiesFiltered(properties)
              .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def serialize(response: QuotaGetResponse): JsValue = Json.toJson(response)

  def serializeChanges(changesResponse: QuotaChangesResponse): JsObject = Json.toJson(changesResponse).as[JsObject]

}
