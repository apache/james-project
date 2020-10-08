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

import javax.inject.Inject
import org.apache.james.jmap.mail.EmailSet.UnparsedMessageId
import org.apache.james.jmap.mail.{DestroyIds, EmailSetRequest, EmailSetResponse}
import org.apache.james.jmap.model.SetError
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

import scala.util.Try

class EmailSetSerializer @Inject() (messageIdFactory: MessageId.Factory) {

  private implicit val messageIdWrites: Writes[MessageId] = messageId => JsString(messageId.serialize)
  private implicit val messageIdReads: Reads[MessageId] = {
    case JsString(serializedMessageId) => Try(JsSuccess(messageIdFactory.fromString(serializedMessageId)))
      .fold(_ => JsError("Invalid messageId"), messageId => messageId)
    case _ => JsError("Expecting messageId to be represented by a JsString")
  }

  private implicit val notDestroyedWrites: Writes[Map[UnparsedMessageId, SetError]] = mapWrites[UnparsedMessageId, SetError](_.value, setErrorWrites)
  private implicit val destroyIdsReads: Reads[DestroyIds] = {
    Json.valueFormat[DestroyIds]
  }
  private implicit val destroyIdsWrites: Writes[DestroyIds] = Json.valueWrites[DestroyIds]
  private implicit val emailRequestSetFormat: Format[EmailSetRequest] = Json.format[EmailSetRequest]
  private implicit val emailResponseSetWrites: OWrites[EmailSetResponse] = Json.writes[EmailSetResponse]

  def deserialize(input: JsValue): JsResult[EmailSetRequest] = Json.fromJson[EmailSetRequest](input)

  def serialize(response: EmailSetResponse): JsObject = Json.toJsObject(response)
}
