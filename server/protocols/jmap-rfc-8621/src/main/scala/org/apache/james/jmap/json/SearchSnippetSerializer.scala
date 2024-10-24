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

import com.google.inject.Inject
import eu.timepit.refined
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.highlight.{SearchSnippet, SearchSnippetGetRequest, SearchSnippetGetResponse}
import org.apache.james.jmap.mail.{FilterQuery, UnparsedEmailId}
import play.api.libs.json._

class SearchSnippetSerializer @Inject()(val emailQuerySerializer: EmailQuerySerializer) {

  private implicit val unparsedMessageIdReads: Reads[UnparsedEmailId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"emailId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedEmailId(id)))
    case _ => JsError("emailId needs to be represented by a JsString")
  }

  private implicit val unparsedMessageIdWrites: Writes[UnparsedEmailId] = Json.valueWrites[UnparsedEmailId]

  implicit def filterQueryReads: Reads[FilterQuery] = emailQuerySerializer.filterQueryReads

  private def optionToJson(fieldName: String, value: Option[String]): JsObject =
    value.fold(Json.obj(fieldName -> JsNull))(str => Json.obj(fieldName -> str))

  private implicit val searchSnippetWrites: Writes[SearchSnippet] = (searchSnippet: SearchSnippet) => {
    val subjectField = optionToJson("subject", searchSnippet.subject)
    val previewField = optionToJson("preview", searchSnippet.preview)
    Json.obj("emailId" -> searchSnippet.emailId.serialize) ++ subjectField ++ previewField
  }

  private implicit val searchSnippetGetResponseWrites: OWrites[SearchSnippetGetResponse] = Json.writes[SearchSnippetGetResponse]
  private implicit val searchSnippetGetRequestReads: Reads[SearchSnippetGetRequest] = Json.reads[SearchSnippetGetRequest]

  def deserializeSearchSnippetGetRequest(input: JsValue): JsResult[SearchSnippetGetRequest] = Json.fromJson[SearchSnippetGetRequest](input)

  def serialize(searchSnippetGetResponse: SearchSnippetGetResponse): JsValue = Json.toJson(searchSnippetGetResponse)
}
