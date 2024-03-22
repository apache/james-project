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

import jakarta.inject.Inject
import org.apache.james.jmap.core.{CanCalculateChanges, LimitUnparsed, PositionUnparsed, QueryState}
import org.apache.james.jmap.mail.{AllInThreadHaveKeywordSortProperty, Anchor, AnchorOffset, And, Bcc, Body, Cc, CollapseThreads, Collation, Comparator, EmailQueryRequest, EmailQueryResponse, FilterCondition, FilterOperator, FilterQuery, From, FromSortProperty, HasAttachment, HasKeywordSortProperty, Header, HeaderContains, HeaderExist, IsAscending, Keyword, Not, Operator, Or, ReceivedAtSortProperty, SentAtSortProperty, SizeSortProperty, SomeInThreadHaveKeywordSortProperty, SortProperty, Subject, SubjectSortProperty, Text, To, ToSortProperty}
import org.apache.james.mailbox.model.{MailboxId, MessageId}
import play.api.libs.json._

import scala.util.Try

class EmailQuerySerializer @Inject()(mailboxIdFactory: MailboxId.Factory) {
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
  private implicit val textReads: Reads[Text] = Json.valueReads[Text]
  private implicit val fromReads: Reads[From] = Json.valueReads[From]
  private implicit val toReads: Reads[To] = Json.valueReads[To]
  private implicit val ccReads: Reads[Cc] = Json.valueReads[Cc]
  private implicit val bccReads: Reads[Bcc] = Json.valueReads[Bcc]
  private implicit val subjectReads: Reads[Subject] = Json.valueReads[Subject]
  private implicit val headerReads: Reads[Header] = {
    case array: JsArray if array.value.length == 1 =>
      extractString(array.value.head)
          .fold[JsResult[Header]](e => e, name => JsSuccess(HeaderExist(name)))
    case array: JsArray if array.value.length == 2 =>
      extractString(array.value.head)
          .flatMap(name => extractString(array.value.last)
            .map(value => (name, value)))
          .fold(e => e, {
            case (name, value) => JsSuccess(HeaderContains(name, value))
          })
    case _ => JsError("header filter needs to be an array of one or two strings")
  }
  private def extractString(jsValue: JsValue): Either[JsResult[Header], String] = jsValue match {
    case JsString(value) => Right(value)
    case _ => Left(JsError("header filter needs to be an array of one or two strings"))
  }
  private implicit val bodyReads: Reads[Body] = Json.valueReads[Body]

  private implicit val operatorReads: Reads[Operator] = {
    case JsString("AND") => JsSuccess(And)
    case JsString("OR") => JsSuccess(Or)
    case JsString("NOT") => JsSuccess(Not)
    case _ => JsError(s"Expecting a JsString to represent a known operator")
  }

  private val filterConditionRawRead: Reads[FilterCondition] = Json.reads[FilterCondition]
  private implicit val filterConditionReads: Reads[FilterCondition] = {
    case JsObject(underlying) =>
      val unsupported: collection.Set[String] = underlying.keySet.diff(FilterCondition.SUPPORTED)
      if (unsupported.nonEmpty) {
        JsError(s"These '${unsupported.mkString("[", ", ", "]")}' was unsupported filter options")
      } else {
        filterConditionRawRead.reads(JsObject(underlying))
      }
    case jsValue => filterConditionRawRead.reads(jsValue)
  }

  private implicit val limitUnparsedReads: Reads[LimitUnparsed] = Json.valueReads[LimitUnparsed]
  private implicit val CanCalculateChangesFormat: Format[CanCalculateChanges] = Json.valueFormat[CanCalculateChanges]

  private implicit val queryStateWrites: Writes[QueryState] = Json.valueWrites[QueryState]
  private implicit val positionUnparsedReads: Reads[PositionUnparsed] = Json.valueReads[PositionUnparsed]
  private implicit val messageIdWrites: Writes[MessageId] = id => JsString(id.serialize())

  private implicit val filterOperatorReads: Reads[FilterOperator] = Json.reads[FilterOperator]

  private implicit def filterQueryReads: Reads[FilterQuery] = {
    case JsObject(underlying) if underlying.contains("operator") && (!underlying.contains("conditions") || underlying.contains("conditions") && underlying.size > 2) => JsError("Expecting filterOperator to contain only operator and conditions")
    case jsValue@JsObject(underlying) if underlying.contains("operator") && underlying.contains("conditions") && underlying.size.equals(2) => filterOperatorReads.reads(jsValue)
    case jsValue => filterConditionReads.reads(jsValue)
  }

  private implicit val sortPropertyReads: Reads[SortProperty] = {
    case JsString("receivedAt") => JsSuccess(ReceivedAtSortProperty)
    case JsString("allInThreadHaveKeyword") => JsSuccess(AllInThreadHaveKeywordSortProperty)
    case JsString("someInThreadHaveKeyword") => JsSuccess(SomeInThreadHaveKeywordSortProperty)
    case JsString("size") => JsSuccess(SizeSortProperty)
    case JsString("from") => JsSuccess(FromSortProperty)
    case JsString("to") => JsSuccess(ToSortProperty)
    case JsString("subject") => JsSuccess(SubjectSortProperty)
    case JsString("hasKeyword") => JsSuccess(HasKeywordSortProperty)
    case JsString("sentAt") => JsSuccess(SentAtSortProperty)
    case JsString(others) => JsError(s"'$others' is not a supported sort property")
    case _ => JsError(s"Expecting a JsString to represent a sort property")
  }
  private implicit val sortPropertyWrites: Writes[SortProperty] = {
    case ReceivedAtSortProperty => JsString("receivedAt")
    case _ => throw new NotImplementedError()
  }

  private implicit val isAscendingFormat: Format[IsAscending] = Json.valueFormat[IsAscending]
  private implicit val collationFormat: Format[Collation] = Json.valueFormat[Collation]
  private implicit val comparatorFormat: Format[Comparator] = Json.format[Comparator]
  private implicit val collapseThreadsReads: Reads[CollapseThreads] = Json.valueReads[CollapseThreads]
  private implicit val anchorReads: Reads[Anchor] = Json.valueReads[Anchor]
  private implicit val anchorOffsetReads: Reads[AnchorOffset] = Json.valueReads[AnchorOffset]

  private implicit val emailQueryRequestReads: Reads[EmailQueryRequest] = Json.reads[EmailQueryRequest]

  private implicit val emailQueryResponseWrites: OWrites[EmailQueryResponse] = Json.writes[EmailQueryResponse]

  def serialize(emailQueryResponse: EmailQueryResponse): JsObject = Json.toJsObject(emailQueryResponse)

  def deserializeEmailQueryRequest(input: JsValue): JsResult[EmailQueryRequest] = Json.fromJson[EmailQueryRequest](input)
}