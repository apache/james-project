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
import org.apache.james.jmap.mail.Subject
import org.apache.james.jmap.vacation.VacationResponse.VACATION_RESPONSE_ID
import org.apache.james.jmap.vacation.{FromDate, HtmlBody, IsEnabled, TextBody, ToDate, UnparsedVacationResponseId, VacationResponse, VacationResponseGetRequest, VacationResponseGetResponse, VacationResponseId, VacationResponseIds, VacationResponseNotFound, VacationResponsePatchObject, VacationResponseSetError, VacationResponseSetRequest, VacationResponseSetResponse, VacationResponseUpdateResponse}
import play.api.libs.json._

import scala.language.implicitConversions

object VacationSerializer {

  private implicit val unparsedMessageIdWrites: Writes[UnparsedVacationResponseId] = Json.valueWrites[UnparsedVacationResponseId]
  private implicit val unparsedMessageIdReads: Reads[UnparsedVacationResponseId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"vacation response id does not match Id constraints: $e"),
        id => JsSuccess(UnparsedVacationResponseId(id)))
    case _ => JsError("vacation response id needs to be represented by a JsString")
  }
  private implicit val isEnabledReads: Reads[IsEnabled] = Json.valueReads[IsEnabled]
  private implicit val vacationResponsePatchObjectReads: Reads[VacationResponsePatchObject] = {
    case jsObject: JsObject => JsSuccess(VacationResponsePatchObject(jsObject))
    case _ => JsError("VacationResponsePatchObject needs to be represented by a JsObject")
  }
  private implicit val vacationResponseSetRequestReads: Reads[VacationResponseSetRequest] = Json.reads[VacationResponseSetRequest]

  private implicit val vacationResponseSetUpdateResponseWrites: Writes[VacationResponseUpdateResponse] = Json.valueWrites[VacationResponseUpdateResponse]

  private implicit val vacationResponseSetErrorWrites: Writes[VacationResponseSetError] = Json.writes[VacationResponseSetError]

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val vacationResponseSetResponseWrites: Writes[VacationResponseSetResponse] = Json.writes[VacationResponseSetResponse]

  private implicit val vacationResponseIdWrites: Writes[VacationResponseId] = _ => JsString(VACATION_RESPONSE_ID.value)
  private implicit val vacationResponseIdReads: Reads[VacationResponseId] = {
    case JsString("singleton") => JsSuccess(VacationResponseId())
    case JsString(_) => JsError("Only singleton is supported as a VacationResponseId")
    case _ => JsError("Expecting JsString(singleton) to represent a VacationResponseId")
  }
  private implicit val isEnabledWrites: Writes[IsEnabled] = Json.valueWrites[IsEnabled]
  private implicit val fromDateWrites: Writes[FromDate] = Json.valueWrites[FromDate]
  private implicit val toDateWrites: Writes[ToDate] = Json.valueWrites[ToDate]
  private implicit val subjectWrites: Writes[Subject] = Json.valueWrites[Subject]
  private implicit val textBodyWrites: Writes[TextBody] = Json.valueWrites[TextBody]
  private implicit val htmlBodyWrites: Writes[HtmlBody] = Json.valueWrites[HtmlBody]

  private implicit val vacationResponseWrites: Writes[VacationResponse] = Json.writes[VacationResponse]

  private implicit val vacationResponseIdsReads: Reads[VacationResponseIds] = Json.valueReads[VacationResponseIds]

  private implicit val vacationResponseGetRequest: Reads[VacationResponseGetRequest] = Json.reads[VacationResponseGetRequest]

  private implicit val vacationResponseNotFoundWrites: Writes[VacationResponseNotFound] =
    notFound => JsArray(notFound.value.toList.map(id => JsString(id.id.value)))

  private implicit val vacationResponseGetResponseWrites: Writes[VacationResponseGetResponse] = Json.writes[VacationResponseGetResponse]

  def serialize(vacationResponse: VacationResponse): JsValue = Json.toJson(vacationResponse)

  def serialize(vacationResponseGetResponse: VacationResponseGetResponse)(implicit vacationResponseWrites: Writes[VacationResponse]): JsValue =
    serialize(vacationResponseGetResponse, VacationResponse.allProperties)

  def serialize(vacationResponseGetResponse: VacationResponseGetResponse, properties: Properties): JsValue =
    Json.toJson(vacationResponseGetResponse)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject =>
            VacationResponse.propertiesFiltered(properties)
              .filter(jsonObject)
          case jsValue => jsValue
        }))
        case jsValue => JsError(s"expected JsArray, got $jsValue")
      }).get


  def serialize(vacationResponseSetResponse: VacationResponseSetResponse): JsValue = Json.toJson(vacationResponseSetResponse)

  def deserializeVacationResponseGetRequest(input: String): JsResult[VacationResponseGetRequest] = Json.parse(input).validate[VacationResponseGetRequest]

  def deserializeVacationResponseGetRequest(input: JsValue): JsResult[VacationResponseGetRequest] = Json.fromJson[VacationResponseGetRequest](input)

  def deserializeVacationResponseSetRequest(input: JsValue): JsResult[VacationResponseSetRequest] = Json.fromJson[VacationResponseSetRequest](input)
}