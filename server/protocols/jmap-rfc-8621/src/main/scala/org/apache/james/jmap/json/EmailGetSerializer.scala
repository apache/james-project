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
import eu.timepit.refined.auto._
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, Preview}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, UuidState}
import org.apache.james.jmap.mail._
import org.apache.james.mailbox.model.{Cid, MailboxId, MessageId}
import play.api.libs.functional.syntax._
import play.api.libs.json._


object EmailBodyPartToSerialize {
  def from(part: EmailBodyPart): EmailBodyPartToSerialize = EmailBodyPartToSerialize(
    partId = part.partId,
    blobId = part.blobId,
    headers = part.headers,
    size = part.size,
    `type` = part.`type`,
    charset = part.charset,
    disposition = part.disposition,
    cid = part.cid,
    language = part.language,
    location = part.location,
    name = part.name,
    subParts = part.subParts.map(list => list.map(EmailBodyPartToSerialize.from)),
    specificHeaders = part.specificHeaders)
}

case class EmailBodyPartToSerialize(partId: PartId,
                                    blobId: Option[BlobId],
                                    headers: List[EmailHeader],
                                    size: Size,
                                    name: Option[Name],
                                    `type`: Type,
                                    charset: Option[Charset],
                                    disposition: Option[Disposition],
                                    cid: Option[Cid],
                                    language: Option[Languages],
                                    location: Option[Location],
                                    subParts: Option[List[EmailBodyPartToSerialize]],
                                    specificHeaders: Map[String, Option[EmailHeaderValue]])

object EmailGetSerializer {
  private implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)

  private implicit val fetchTextBodyValuesReads: Reads[FetchTextBodyValues] = Json.valueReads[FetchTextBodyValues]
  private implicit val fetchHTMLBodyValuesReads: Reads[FetchHTMLBodyValues] = Json.valueReads[FetchHTMLBodyValues]
  private implicit val fetchAllBodyValuesReads: Reads[FetchAllBodyValues] = Json.valueReads[FetchAllBodyValues]
  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]
  private implicit val cidWrites: Writes[Cid] = cid => JsString(cid.getValue)
  private implicit val nameWrites: Writes[Name] = Json.valueWrites[Name]
  private implicit val threadIdWrites: Writes[ThreadId] = Json.valueWrites[ThreadId]
  private implicit val mailboxIdsWrites: Writes[MailboxIds] = ids => JsObject(ids.value.map(id => (id.serialize(), JsTrue)))
  private implicit val typeWrites: Writes[Type] = Json.valueWrites[Type]
  private implicit val charsetWrites: Writes[Charset] = Json.valueWrites[Charset]
  private implicit val dispositionWrites: Writes[Disposition] = Json.valueWrites[Disposition]
  private implicit val languageWrites: Format[Language] = Json.valueFormat[Language]
  private implicit val languagesFormat: Format[Languages] = Json.valueFormat[Languages]
  private implicit val locationWrites: Writes[Location] = Json.valueWrites[Location]
  private implicit val emailerNameWrites: Writes[EmailerName] = Json.valueWrites[EmailerName]
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
  private implicit val GroupNameWrites: Writes[GroupName] = Json.valueWrites[GroupName]
  private implicit val emailAddressGroupWrites: Writes[EmailAddressGroup] = (o: EmailAddressGroup) =>
    Json.obj(
      "name" -> Json.toJson(o.name),
      "addresses" -> Json.toJson(o.addresses))
  private implicit val groupedAddressesHeaderWrites: Writes[GroupedAddressesHeaderValue] = Json.valueWrites[GroupedAddressesHeaderValue]
  private implicit val messageIdsHeaderWrites: Writes[MessageIdsHeaderValue] = Json.valueWrites[MessageIdsHeaderValue]
  private implicit val dateHeaderWrites: Writes[DateHeaderValue] = Json.valueWrites[DateHeaderValue]
  private implicit val headerURLWrites: Writes[HeaderURL] = Json.valueWrites[HeaderURL]
  private implicit val urlsHeaderWrites: Writes[URLsHeaderValue] = Json.valueWrites[URLsHeaderValue]
  private implicit val emailHeaderWrites: Writes[EmailHeaderValue] = {
    case headerValue: AllHeaderValues => JsArray(headerValue.values.map(h =>  Json.toJson[EmailHeaderValue](h)(emailHeaderWrites)))
    case headerValue: RawHeaderValue => Json.toJson[RawHeaderValue](headerValue)
    case headerValue: TextHeaderValue => Json.toJson[TextHeaderValue](headerValue)
    case headerValue: AddressesHeaderValue => Json.toJson[AddressesHeaderValue](headerValue)
    case headerValue: GroupedAddressesHeaderValue => Json.toJson[GroupedAddressesHeaderValue](headerValue)
    case headerValue: MessageIdsHeaderValue => Json.toJson[MessageIdsHeaderValue](headerValue)
    case headerValue: DateHeaderValue => Json.toJson[DateHeaderValue](headerValue)
    case headerValue: URLsHeaderValue => Json.toJson[URLsHeaderValue](headerValue)
  }
  private implicit val allHeaderWrites: Writes[AllHeaderValues] = Json.valueWrites[AllHeaderValues]
  private implicit val headersWrites: Writes[EmailHeader] = Json.writes[EmailHeader]
  private implicit val bodyValueWrites: Writes[EmailBodyValue] = Json.writes[EmailBodyValue]
  private implicit val unparsedMessageIdWrites: Writes[UnparsedEmailId] = Json.valueWrites[UnparsedEmailId]
  private implicit val unparsedMessageIdReads: Reads[UnparsedEmailId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"emailId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedEmailId(id)))
    case _ => JsError("emailId needs to be represented by a JsString")
  }
  private implicit val emailIdsReads: Reads[EmailIds] = Json.valueReads[EmailIds]
  private implicit val emailGetRequestReads: Reads[EmailGetRequest] = Json.reads[EmailGetRequest]

  private implicit val emailChangesRequestReads: Reads[EmailChangesRequest] = Json.reads[EmailChangesRequest]
  private implicit val subjectWrites: Writes[Subject] = Json.valueWrites[Subject]
  private implicit val emailNotFoundWrites: Writes[EmailNotFound] = Json.valueWrites[EmailNotFound]
  private implicit val messageIdWrites: Writes[MessageId] = id => JsString(id.serialize())
  private implicit val keywordWrites: Writes[Keyword] = Json.valueWrites[Keyword]
  private implicit val keywordsWrites: Writes[Keywords] = keywords => JsObject(keywords.asMap.map {
    case (keyword, b) => (keyword.flagName, JsBoolean(b))
  })

  private implicit val bodyValueMapWrites: Writes[Map[PartId, EmailBodyValue]] =
    mapWrites[PartId, EmailBodyValue](_.value.toString(), bodyValueWrites)

  private implicit val bodyPartWritesToSerializeWrites: Writes[EmailBodyPartToSerialize] = (
      (__ \ "partId").write[PartId] and
      (__ \ "blobId").writeNullable[BlobId] and
      (__ \ "headers").write[List[EmailHeader]] and
      (__ \ "size").write[Size] and
      (__ \ "name").writeNullable[Name] and
      (__ \ "type").write[Type] and
      (__ \ "charset").writeNullable[Charset] and
      (__ \ "disposition").writeNullable[Disposition] and
      (__ \ "cid").writeNullable[Cid] and
      (__ \ "language").writeNullable[Languages] and
      (__ \ "location").writeNullable[Location] and
      (__ \ "subParts").lazyWriteNullable(implicitly[Writes[List[EmailBodyPartToSerialize]]](list => new JsArray(list.map(bodyPartWritesToSerializeWrites.writes).toIndexedSeq) )) and
        JsPath.write[Map[String, Option[EmailHeaderValue]]]
    )(unlift(EmailBodyPartToSerialize.unapply))

  private implicit val bodyPartWrites: Writes[EmailBodyPart] = part => bodyPartWritesToSerializeWrites.writes(EmailBodyPartToSerialize.from(part))

  private implicit val emailMetadataWrites: OWrites[EmailMetadata] = Json.writes[EmailMetadata]
  private implicit val emailHeadersWrites: Writes[EmailHeaders] = Json.writes[EmailHeaders]
  private implicit val attachmentsMetadataWrites: Writes[AttachmentsMetadata] = Json.writes[AttachmentsMetadata]
  private implicit val emailBodyMetadataWrites: Writes[EmailBodyMetadata] = Json.writes[EmailBodyMetadata]

  private val emailFastViewWrites: OWrites[EmailFastView] = (JsPath.write[EmailMetadata] and
    JsPath.write[EmailHeaders] and
    JsPath.write[EmailBodyMetadata] and
    JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailFastView.unapply))
  private val emailFastViewWithAttachmentsWrites: OWrites[EmailFastViewWithAttachments] = (JsPath.write[EmailMetadata] and
    JsPath.write[EmailHeaders] and
    JsPath.write[AttachmentsMetadata] and
    JsPath.write[EmailBodyMetadata] and
    JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailFastViewWithAttachments.unapply))
  private val emailHeaderViewWrites: OWrites[EmailHeaderView] = (JsPath.write[EmailMetadata] and
    JsPath.write[EmailHeaders] and
    JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailHeaderView.unapply))
  private val emailMetadataViewWrites: OWrites[EmailMetadataView] = view => Json.toJsObject(view.metadata)
  private implicit val emailBodyWrites: Writes[EmailBody] = Json.writes[EmailBody]
  private implicit val emailFullViewWrites: OWrites[EmailFullView] = (JsPath.write[EmailMetadata] and
    JsPath.write[EmailHeaders] and
    JsPath.write[EmailBody] and
    JsPath.write[EmailBodyMetadata] and
    JsPath.write[Map[String, Option[EmailHeaderValue]]]) (unlift(EmailFullView.unapply))
  private implicit val emailWrites: OWrites[EmailView] = {
    case view: EmailMetadataView => emailMetadataViewWrites.writes(view)
    case view: EmailHeaderView => emailHeaderViewWrites.writes(view)
    case view: EmailFastView => emailFastViewWrites.writes(view)
    case view: EmailFastViewWithAttachments => emailFastViewWithAttachmentsWrites.writes(view)
    case view: EmailFullView => emailFullViewWrites.writes(view)
  }
  private implicit val emailParseNotFoundWrites: Writes[EmailParseNotFound] = Json.valueWrites[EmailParseNotFound]
  private implicit val emailNotParsableWrites: Writes[EmailParseNotParsable] = Json.valueWrites[EmailParseNotParsable]
  private implicit val emailParseMetadata: Writes[EmailParseMetadata] = Json.writes[EmailParseMetadata]

  private implicit val emailParseViewWrites: OWrites[EmailParseView] = (JsPath.write[EmailParseMetadata] and
    JsPath.write[EmailHeaders]and
    JsPath.write[EmailBody] and
    JsPath.write[EmailBodyMetadata] and
    JsPath.write[Map[String, Option[EmailHeaderValue]]] ) (unlift(EmailParseView.unapply))

  private implicit val parsedMapWrites: Writes[Map[BlobId, EmailParseView]] = mapWrites[BlobId, EmailParseView](s => s.value.value, emailParseViewWrites)

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val emailGetResponseWrites: Writes[EmailGetResponse] = Json.writes[EmailGetResponse]
  private implicit val emailParseResponseWrites: Writes[EmailParseResponse] = Json.writes[EmailParseResponse]
  private implicit val changesResponseWrites: OWrites[EmailChangesResponse] = Json.writes[EmailChangesResponse]

  def serializeChanges(changesResponse: EmailChangesResponse): JsObject = Json.toJson(changesResponse).as[JsObject]

  def serialize(emailGetResponse: EmailGetResponse, properties: Properties, bodyProperties: Properties): JsValue = {
    if (includesBodyProperties(properties)) {
      val bodyTransformation = bodyPropertiesFilteringTransformation(bodyProperties)
      Json.toJson(emailGetResponse)
        .transform((__ \ "list").json.update {
          case JsArray(underlying) => JsSuccess(JsArray(underlying.map(js => js.transform {
            case jsonObject: JsObject => bodyTransformation.reads(properties.filter(jsonObject))
            case js => JsSuccess(js)
          }.fold(_ => JsArray(underlying), o => o))))
          case jsValue => JsSuccess(jsValue)
        }).get
    } else
      Json.toJson(emailGetResponse)
        .transform((__ \ "list").json.update {
          case JsArray(underlying) => JsSuccess(JsArray(underlying.map(js => js.transform {
            case jsonObject: JsObject => JsSuccess(properties.filter(jsonObject))
            case js => JsSuccess(js)
          }.fold(_ => JsArray(underlying), o => o))))
          case jsValue => JsSuccess(jsValue)
        }).get
  }

  private def includesBodyProperties(properties: Properties): Boolean =
    properties.contains("attachments") ||
      properties.contains("bodyStructure") ||
      properties.contains("textBody") ||
      properties.contains("htmlBody")

  private def bodyPropertiesFilteringTransformation(bodyProperties: Properties): Reads[JsValue] = {
    case serializedBody: JsObject => JsSuccess(bodyPropertiesFilteringTransformationJsObject(bodyProperties).apply(serializedBody))
    case js => JsSuccess(js)
  }

  private def bodyPropertiesFilteringTransformationJsObject(bodyProperties: Properties): JsObject => JsObject =
    serializedBody => {
      val bodyPropertiesToRemove = EmailBodyPart.allowedProperties -- bodyProperties
      val noop: JsValue => JsValue = o => o

      Seq(
        bodyPropertiesFilteringTransformation(bodyPropertiesToRemove, "attachments"),
        bodyPropertiesFilteringTransformationWithRecursion(bodyPropertiesToRemove, "bodyStructure"),
        bodyPropertiesFilteringTransformation(bodyPropertiesToRemove, "textBody"),
        bodyPropertiesFilteringTransformation(bodyPropertiesToRemove, "htmlBody"))
        .reduceLeftOption(_ compose _)
        .getOrElse(noop)
        .apply(serializedBody)
        .asInstanceOf[JsObject]
    }

  private def bodyPropertiesFilteringTransformation(properties: Properties, field: String): JsValue => JsValue =
  {
    case JsObject(underlying) => JsObject(underlying.map {
      case (key, jsValue) if key.equals(field) => (field, removeFields(properties).apply(jsValue))
      case (key, jsValue) => (key, jsValue)
    })
    case jsValue => jsValue
  }

  private def bodyPropertiesFilteringTransformationWithRecursion(properties: Properties, field: String): JsValue => JsValue =
  {
    case JsObject(underlying) => JsObject(underlying.map {
      case (key, jsValue) if key.equals(field) => (field, removeFieldsRecursively(properties).apply(jsValue))
      case (key, jsValue) => (key, jsValue)
    })
    case jsValue => jsValue
  }

  private def removeFieldsRecursively(properties: Properties): JsValue => JsValue = {
    case JsObject(underlying) => JsObject(underlying.flatMap {
      case (key, _) if properties.containsString(key) => None
      case (key, value) => Some((key, removeFieldsRecursively(properties).apply(value)))
    })
    case JsArray(others) => JsArray(others.map(removeFieldsRecursively(properties)))
    case o: JsValue => o
  }

  private def removeFields(properties: Properties): JsValue => JsValue = {
    case JsObject(underlying) => JsObject(underlying.flatMap {
      case (key, _) if properties.containsString(key) => None
      case (key, value) => Some((key, value))
    })
    case JsArray(others) => JsArray(others.map(removeFieldsRecursively(properties)))
    case o: JsValue => o
  }

  def deserializeEmailGetRequest(input: JsValue): JsResult[EmailGetRequest] = Json.fromJson[EmailGetRequest](input)

  def deserializeEmailChangesRequest(input: JsValue): JsResult[EmailChangesRequest] = Json.fromJson[EmailChangesRequest](input)

  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val emailParseRequestReads: Reads[EmailParseRequest] = Json.reads[EmailParseRequest]

  def deserializeEmailParseRequest(input: JsValue): JsResult[EmailParseRequest] = Json.fromJson[EmailParseRequest](input)

  def serializeEmailParseResponse(emailGetResponse: EmailParseResponse, properties: Properties, bodyProperties: Properties): JsValue =
    JsObject(Json.toJson(emailGetResponse)
      .asInstanceOf[JsObject].fields.map {
            case ("parsed", parsed) => ("parsed", JsObject(parsed.asInstanceOf[JsObject].fields.map {
              case (key, value) => (key, bodyPropertiesFilteringTransformationJsObject(bodyProperties)
                .apply(properties.filter(value.asInstanceOf[JsObject])))
            }))
            case any => any
          })
}