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
import org.apache.james.jmap.mail.{Collation, Comparator, EmailQueryRequest, EmailQueryResponse, FilterCondition, HasAttachment, IsAscending, ReceivedAtSortProperty, SortProperty}
import org.apache.james.jmap.model.{AccountId, CanCalculateChanges, Keyword, LimitUnparsed, Position, QueryState}
import org.apache.james.mailbox.model.{MailboxId, MessageId}
import play.api.libs.json._

import scala.language.implicitConversions
import scala.util.Try

class EmailQuerySerializer @Inject()(mailboxIdFactory: MailboxId.Factory) {
  private implicit val accountIdWrites: Format[AccountId] = Json.valueFormat[AccountId]

  private implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)
  private implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => Try(JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))).getOrElse(JsError())
    case _ => JsError()
  }

  private implicit val keywordReads: Reads[Keyword] = {
    case JsString(keywordValue) =>
      Keyword.parse(keywordValue)
        .flatMap(keyword => if (keyword.isForbiddenImapKeyword) {
          Left(s"Search based on IMAP unexposed keywords is not supported for $keywordValue")
        } else {
          Right(keyword)
        })
      .fold(JsError(_), JsSuccess(_))
    case _ => JsError("Expecting keywords to be represented by a JsString")
  }
  private implicit val hasAttachmentReads: Reads[HasAttachment] = Json.valueReads[HasAttachment]
  private implicit val filterConditionReads: Reads[FilterCondition] = Json.reads[FilterCondition]
  private implicit val limitUnparsedReads: Reads[LimitUnparsed] = Json.valueReads[LimitUnparsed]
  private implicit val CanCalculateChangesFormat: Format[CanCalculateChanges] = Json.valueFormat[CanCalculateChanges]

  private implicit val queryStateWrites: Writes[QueryState] = Json.valueWrites[QueryState]
  private implicit val positionFormat: Format[Position] = Json.valueFormat[Position]
  private implicit val messageIdWrites: Writes[MessageId] = id => JsString(id.serialize())

  private implicit val sortPropertyReads: Reads[SortProperty] = {
    case JsString("receivedAt") => JsSuccess(ReceivedAtSortProperty)
    case JsString(others) => JsError(s"'$others' is not a supported sort property")
    case _ => JsError(s"Expecting a JsString to represent a sort property")
  }
  private implicit val sortPropertyWrites: Writes[SortProperty] = {
    case ReceivedAtSortProperty => JsString("receivedAt")
  }

  private implicit val isAscendingFormat: Format[IsAscending] = Json.valueFormat[IsAscending]
  private implicit val collationFormat: Format[Collation] = Json.valueFormat[Collation]
  private implicit val comparatorFormat: Format[Comparator] = Json.format[Comparator]

  private implicit val emailQueryRequestReads: Reads[EmailQueryRequest] = Json.reads[EmailQueryRequest]

  private implicit def emailQueryResponseWrites: OWrites[EmailQueryResponse] = Json.writes[EmailQueryResponse]

  def serialize(emailQueryResponse: EmailQueryResponse): JsObject = Json.toJsObject(emailQueryResponse)

  def deserializeEmailQueryRequest(input: JsValue): JsResult[EmailQueryRequest] = Json.fromJson[EmailQueryRequest](input)
}