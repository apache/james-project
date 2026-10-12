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
import org.apache.james.jmap.core.{CanCalculateChanges, QueryState, UuidState}
import org.apache.james.jmap.mail.{EmailSubmissionChangesRequest, EmailSubmissionChangesResponse, EmailSubmissionGetRequest, EmailSubmissionGetResponse, EmailSubmissionId, EmailSubmissionQueryRequest, EmailSubmissionQueryResponse, UnparsedEmailSubmissionId}
import play.api.libs.json.{JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

object EmailSubmissionSerializer {
  private implicit val unparsedEmailSubmissionIdReads: Reads[UnparsedEmailSubmissionId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"email submission id does not match Id constraints: $e"),
        id => JsSuccess(UnparsedEmailSubmissionId(id)))
    case _ => JsError("email submission id needs to be represented by a JsString")
  }
  private implicit val unparsedEmailSubmissionIdWrites: Writes[UnparsedEmailSubmissionId] = Json.valueWrites[UnparsedEmailSubmissionId]
  private implicit val emailSubmissionIdWrites: Writes[EmailSubmissionId] = Json.valueWrites[EmailSubmissionId]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val queryStateWrites: Writes[QueryState] = Json.valueWrites[QueryState]
  private implicit val canCalculateChangesWrites: Writes[CanCalculateChanges] = Json.valueWrites[CanCalculateChanges]

  private implicit val getRequestReads: Reads[EmailSubmissionGetRequest] = Json.reads[EmailSubmissionGetRequest]
  private implicit val changesRequestReads: Reads[EmailSubmissionChangesRequest] = Json.reads[EmailSubmissionChangesRequest]
  private implicit val queryRequestReads: Reads[EmailSubmissionQueryRequest] = Json.reads[EmailSubmissionQueryRequest]
  private implicit val getResponseWrites: OWrites[EmailSubmissionGetResponse] = Json.writes[EmailSubmissionGetResponse]
  private implicit val changesResponseWrites: OWrites[EmailSubmissionChangesResponse] = Json.writes[EmailSubmissionChangesResponse]
  private implicit val queryResponseWrites: OWrites[EmailSubmissionQueryResponse] = Json.writes[EmailSubmissionQueryResponse]

  def deserializeGetRequest(input: JsValue): JsResult[EmailSubmissionGetRequest] = Json.fromJson[EmailSubmissionGetRequest](input)
  def deserializeChangesRequest(input: JsValue): JsResult[EmailSubmissionChangesRequest] = Json.fromJson[EmailSubmissionChangesRequest](input)
  def deserializeQueryRequest(input: JsValue): JsResult[EmailSubmissionQueryRequest] = Json.fromJson[EmailSubmissionQueryRequest](input)

  def serialize(response: EmailSubmissionGetResponse): JsObject = Json.toJsObject(response)
  def serialize(response: EmailSubmissionChangesResponse): JsObject = Json.toJsObject(response)
  def serialize(response: EmailSubmissionQueryResponse): JsObject = Json.toJsObject(response)
}
