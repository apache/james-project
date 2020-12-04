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

import org.apache.james.jmap.core.{Properties, State}
import org.apache.james.jmap.mail._
import play.api.libs.json.{JsArray, JsObject, JsResult, JsSuccess, JsValue, Json, OWrites, Reads, Writes, __}

object IdentitySerializer {
  private implicit val emailerNameReads: Writes[EmailerName] = Json.valueWrites[EmailerName]
  private implicit val emailAddressReads: Writes[EmailAddress] = Json.writes[EmailAddress]
  private implicit val nameWrites: Writes[IdentityName] = Json.valueWrites[IdentityName]
  private implicit val textSignatureWrites: Writes[TextSignature] = Json.valueWrites[TextSignature]
  private implicit val htmlSignatureWrites: Writes[HtmlSignature] = Json.valueWrites[HtmlSignature]
  private implicit val mayDeleteWrites: Writes[MayDeleteIdentity] = Json.valueWrites[MayDeleteIdentity]
  private implicit val identityWrites: Writes[Identity] = Json.writes[Identity]
  private implicit val identityGetRequestReads: Reads[IdentityGetRequest] = Json.reads[IdentityGetRequest]
  private implicit val stateWrites: Writes[State] = Json.valueWrites[State]
  private implicit val identityGetResponseWrites: OWrites[IdentityGetResponse] = Json.writes[IdentityGetResponse]

  def serialize(response: IdentityGetResponse, properties: Properties): JsObject = Json.toJsObject(response)
    .transform((__ \ "list").json.update {
      case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
        case jsonObject: JsObject => properties.filter(jsonObject)
        case jsValue => jsValue
      }))
    }).get

  def deserialize(input: JsValue): JsResult[IdentityGetRequest] = Json.fromJson[IdentityGetRequest](input)

}
