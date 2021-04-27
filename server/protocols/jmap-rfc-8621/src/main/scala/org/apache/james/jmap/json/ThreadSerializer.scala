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

import org.apache.james.jmap.core.UuidState
import org.apache.james.jmap.mail.{Thread, ThreadChangesRequest, ThreadChangesResponse, ThreadGetRequest, ThreadGetResponse}
import play.api.libs.json.{JsObject, JsResult, JsValue, Json, OWrites, Reads, Writes}

import scala.language.implicitConversions

object ThreadSerializer {
  private implicit val threadGetReads: Reads[ThreadGetRequest] = Json.reads[ThreadGetRequest]
  private implicit val threadChangesReads: Reads[ThreadChangesRequest] = Json.reads[ThreadChangesRequest]
  private implicit val threadWrites: OWrites[Thread] = Json.writes[Thread]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val threadGetWrites: OWrites[ThreadGetResponse] = Json.writes[ThreadGetResponse]
  private implicit val changesResponseWrites: OWrites[ThreadChangesResponse] = Json.writes[ThreadChangesResponse]

  def serializeChanges(threadChangesResponse: ThreadChangesResponse): JsObject = Json.toJson(threadChangesResponse).as[JsObject]
  def serialize(threadGetResponse: ThreadGetResponse): JsObject = Json.toJson(threadGetResponse).as[JsObject]

  def deserialize(input: JsValue): JsResult[ThreadGetRequest] = Json.fromJson[ThreadGetRequest](input)
  def deserializeChanges(input: JsValue): JsResult[ThreadChangesRequest] = Json.fromJson[ThreadChangesRequest](input)
}