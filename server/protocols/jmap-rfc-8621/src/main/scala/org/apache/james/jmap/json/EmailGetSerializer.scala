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

import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.mail.{Address, AddressesHeaderValue, BlobId, Charset, Disposition, EmailAddress, EmailBody, EmailBodyMetadata, EmailBodyPart, EmailBodyValue, EmailFastView, EmailFullView, EmailGetRequest, EmailGetResponse, EmailHeader, EmailHeaderName, EmailHeaderValue, EmailHeaderView, EmailHeaders, EmailIds, EmailMetadata, EmailMetadataView, EmailNotFound, EmailView, EmailerName, FetchAllBodyValues, FetchHTMLBodyValues, FetchTextBodyValues, HasAttachment, HeaderMessageId, IsEncodingProblem, IsTruncated, Language, Location, MailboxIds, Name, PartId, RawHeaderValue, Subject, TextHeaderValue, ThreadId, Type}
import org.apache.james.jmap.model._
import org.apache.james.mailbox.model.{Cid, MailboxId, MessageId}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

object EmailGetSerializer {
  private implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)

  private implicit val fetchTextBodyValuesReads: Reads[FetchTextBodyValues] = Json.valueReads[FetchTextBodyValues]
  private implicit val fetchHTMLBodyValuesReads: Reads[FetchHTMLBodyValues] = Json.valueReads[FetchHTMLBodyValues]
  private implicit val fetchAllBodyValuesReads: Reads[FetchAllBodyValues] = Json.valueReads[FetchAllBodyValues]
  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]
  private implicit val cidWrites: Writes[Cid] = cid => JsString(cid.getValue)
  private implicit val nameWrites: Writes[Name] = Json.valueWrites[Name]
  private implicit val threadIdWrites: Writes[ThreadId] = Json.valueWrites[ThreadId]
  private implicit val mailboxIdsWrites: Writes[MailboxIds] = ids => JsObject(ids.value.map(id => (id.serialize(), JsBoolean(true))))
  private implicit val typeWrites: Writes[Type] = Json.valueWrites[Type]
  private implicit val charsetWrites: Writes[Charset] = Json.valueWrites[Charset]
  private implicit val dispositionWrites: Writes[Disposition] = Json.valueWrites[Disposition]
  private implicit val languageWrites: Writes[Language] = Json.valueWrites[Language]
  private implicit val locationWrites: Writes[Location] = Json.valueWrites[Location]
  private implicit val emailerNameWrites: Writes[EmailerName] = Json.valueWrites[EmailerName]
  private implicit val addressWrites: Writes[Address] = Json.valueWrites[Address]
  private implicit val emailAddressWrites: Writes[EmailAddress] = Json.writes[EmailAddress]
  private implicit val headerMessageIdWrites: Writes[HeaderMessageId] = Json.valueWrites[HeaderMessageId]
  private implicit val isEncodingProblemWrites: Writes[IsEncodingProblem] = Json.valueWrites[IsEncodingProblem]
  private implicit val isTruncatedWrites: Writes[IsTruncated] = Json.valueWrites[IsTruncated]
  private implicit val partIdWrites: Writes[PartId] = partId => JsString(partId.serialize)
  private implicit val previewWrites: Writes[Preview] = preview => JsString(preview.getValue)
  private implicit val hasAttachmentWrites: Writes[HasAttachment] = Json.valueWrites[HasAttachment]
  private implicit val headerNameWrites: Writes[EmailHeaderName] = Json.valueWrites[EmailHeaderName]
  private implicit val rawHeaderWrites: Writes[RawHeaderValue] = Json.valueWrites[RawHeaderValue]
  private implicit val textHeaderWrites: Writes[TextHeaderValue] = Json.valueWrites[TextHeaderValue]
  private implicit val addressesHeaderWrites: Writes[AddressesHeaderValue] = Json.valueWrites[AddressesHeaderValue]
  private implicit val emailHeaderWrites: Writes[EmailHeaderValue] = {
    case headerValue: RawHeaderValue => Json.toJson[RawHeaderValue](headerValue)
    case headerValue: TextHeaderValue => Json.toJson[TextHeaderValue](headerValue)
    case headerValue: AddressesHeaderValue => Json.toJson[AddressesHeaderValue](headerValue)
  }
  private implicit val headersWrites: Writes[EmailHeader] = Json.writes[EmailHeader]
  private implicit val bodyValueWrites: Writes[EmailBodyValue] = Json.writes[EmailBodyValue]
  private implicit val emailIdsReads: Reads[EmailIds] = Json.valueReads[EmailIds]
  private implicit val emailGetRequestReads: Reads[EmailGetRequest] = Json.reads[EmailGetRequest]
  private implicit val subjectWrites: Writes[Subject] = Json.valueWrites[Subject]
  private implicit val emailNotFoundWrites: Writes[EmailNotFound] = Json.valueWrites[EmailNotFound]
  private implicit val messageIdWrites: Writes[MessageId] = id => JsString(id.serialize())
  private implicit val keywordWrites: Writes[Keyword] = Json.valueWrites[Keyword]
  private implicit val keywordsWrites: Writes[Keywords] = keywords => JsObject(keywords.asMap.map {
    case (keyword, b) => (keyword.flagName, JsBoolean(b))
  })

  private implicit def bodyValueMapWrites(implicit bodyValueWriter: Writes[EmailBodyValue]): Writes[Map[PartId, EmailBodyValue]] =
    mapWrites[PartId, EmailBodyValue](_.value.toString(), bodyValueWriter)
  private def bodyPartWritesWithPropertyFilter(properties: Properties): Writes[EmailBodyPart] =
    new Writes[EmailBodyPart] {
      def removeJsNull(obj: JsObject): JsObject =
        JsObject(obj.fields.filter({
          case (_, JsNull) => false
          case _ => true
        }))
      def writes(part: EmailBodyPart): JsValue = properties.filter(
        removeJsNull(
          Json.obj("partId" -> Json.toJson(part.partId),
            "blobId" -> Json.toJson(part.blobId),
            "headers" -> Json.toJson(part.headers),
            "size" -> Json.toJson(part.size),
            "name" -> Json.toJson(part.name),
            "type" -> Json.toJson(part.`type`),
            "charset" -> Json.toJson(part.charset),
            "disposition" -> Json.toJson(part.disposition),
            "cid" -> Json.toJson(part.cid),
            "language" -> Json.toJson(part.language),
            "location" -> Json.toJson(part.location),
            "subParts" -> part.subParts.map(list => list.map(writes)))))
    }

  private def emailWritesWithPropertyFilter(properties: Properties)(implicit partsWrites: Writes[EmailBodyPart]): Writes[EmailView] = {
    implicit val emailMetadataWrites: OWrites[EmailMetadata] = Json.writes[EmailMetadata]
    implicit val emailHeadersWrites: Writes[EmailHeaders] = Json.writes[EmailHeaders]
    implicit val emailBodyWrites: Writes[EmailBody] = Json.writes[EmailBody]
    implicit val emailBodyMetadataWrites: Writes[EmailBodyMetadata] = Json.writes[EmailBodyMetadata]

    val emailFullViewWrites: OWrites[EmailFullView] = (JsPath.write[EmailMetadata] and
        JsPath.write[EmailHeaders] and
        JsPath.write[EmailBody] and
        JsPath.write[EmailBodyMetadata] and
        JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailFullView.unapply))

    val emailFastViewWrites: OWrites[EmailFastView] = (JsPath.write[EmailMetadata] and
        JsPath.write[EmailHeaders] and
        JsPath.write[EmailBodyMetadata] and
        JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailFastView.unapply))
    val emailHeaderViewWrites: OWrites[EmailHeaderView] = (JsPath.write[EmailMetadata] and
        JsPath.write[EmailHeaders] and
        JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailHeaderView.unapply))
    val emailMetadataViewWrites: OWrites[EmailMetadataView] = view => Json.toJsObject(view.metadata)

    val emailWrites: OWrites[EmailView] = {
      case view: EmailMetadataView => emailMetadataViewWrites.writes(view)
      case view: EmailHeaderView => emailHeaderViewWrites.writes(view)
      case view: EmailFastView => emailFastViewWrites.writes(view)
      case view: EmailFullView => emailFullViewWrites.writes(view)
    }

    emailWrites.transform(properties.filter(_))
  }
  private implicit def emailGetResponseWrites(implicit emailWrites: Writes[EmailView]): Writes[EmailGetResponse] = Json.writes[EmailGetResponse]

  def serialize(emailGetResponse: EmailGetResponse, properties: Properties, bodyProperties: Properties): JsValue =
    Json.toJson(emailGetResponse)(emailGetResponseWrites(emailWritesWithPropertyFilter(properties)(bodyPartWritesWithPropertyFilter(bodyProperties))))

  def deserializeEmailGetRequest(input: JsValue): JsResult[EmailGetRequest] = Json.fromJson[EmailGetRequest](input)
}