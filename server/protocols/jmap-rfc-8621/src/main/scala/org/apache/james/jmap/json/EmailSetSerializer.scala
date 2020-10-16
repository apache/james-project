/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.jmap.json

import eu.timepit.refined.refineV
import javax.inject.Inject
import org.apache.james.jmap.mail.EmailSet.{UnparsedMessageId, UnparsedMessageIdConstraint}
import org.apache.james.jmap.mail.{DestroyIds, EmailSetRequest, EmailSetResponse, EmailSetUpdate, MailboxIds}
import org.apache.james.jmap.model.SetError
import org.apache.james.mailbox.model.{MailboxId, MessageId}
import play.api.libs.json.{JsBoolean, JsError, JsNull, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

import scala.util.Try

class EmailSetSerializer @Inject()(messageIdFactory: MessageId.Factory, mailboxIdFactory: MailboxId.Factory) {

  private implicit val messageIdWrites: Writes[MessageId] = messageId => JsString(messageId.serialize)
  private implicit val messageIdReads: Reads[MessageId] = {
    case JsString(serializedMessageId) => Try(JsSuccess(messageIdFactory.fromString(serializedMessageId)))
      .fold(_ => JsError("Invalid messageId"), messageId => messageId)
    case _ => JsError("Expecting messageId to be represented by a JsString")
  }

  private implicit val mailboxIdsMapReads: Reads[Map[MailboxId, Boolean]] =
    readMapEntry[MailboxId, Boolean](s => Try(mailboxIdFactory.fromString(s)).toEither.left.map(error => error.getMessage),
      {
        case JsBoolean(true) => JsSuccess(true)
        case JsBoolean(false) => JsError("mailboxId value can only be true")
        case _ => JsError("Expecting mailboxId value to be a boolean")
      })

  private implicit val mailboxIdsReads: Reads[MailboxIds] = jsValue => mailboxIdsMapReads.reads(jsValue).map(
    mailboxIdsMap => MailboxIds(mailboxIdsMap.keys.toList))

  private implicit val emailSetUpdateReads: Reads[EmailSetUpdate] = Json.reads[EmailSetUpdate]

  private implicit val updatesMapReads: Reads[Map[UnparsedMessageId, EmailSetUpdate]] =
    readMapEntry[UnparsedMessageId, EmailSetUpdate](s => refineV[UnparsedMessageIdConstraint](s), emailSetUpdateReads)

  private implicit val unitWrites: Writes[Unit] = _ => JsNull
  private implicit val updatedWrites: Writes[Map[MessageId, Unit]] = mapWrites[MessageId, Unit](_.serialize, unitWrites)
  private implicit val notDestroyedWrites: Writes[Map[UnparsedMessageId, SetError]] = mapWrites[UnparsedMessageId, SetError](_.value, setErrorWrites)
  private implicit val destroyIdsReads: Reads[DestroyIds] = {
    Json.valueFormat[DestroyIds]
  }
  private implicit val destroyIdsWrites: Writes[DestroyIds] = Json.valueWrites[DestroyIds]
  private implicit val emailRequestSetReads: Reads[EmailSetRequest] = Json.reads[EmailSetRequest]
  private implicit val emailResponseSetWrites: OWrites[EmailSetResponse] = Json.writes[EmailSetResponse]

  def deserialize(input: JsValue): JsResult[EmailSetRequest] = Json.fromJson[EmailSetRequest](input)

  def serialize(response: EmailSetResponse): JsObject = Json.toJsObject(response)
}
