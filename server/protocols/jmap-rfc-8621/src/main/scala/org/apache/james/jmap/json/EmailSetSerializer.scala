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

import cats.implicits._
import eu.timepit.refined
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString

import javax.inject.Inject
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName}
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Id, SetError, UTCDate, UuidState}
import org.apache.james.jmap.mail.KeywordsFactory.STRICT_KEYWORDS_FACTORY
import org.apache.james.jmap.mail.{AddressesHeaderValue, AllHeaderValues, AsAddresses, AsDate, AsGroupedAddresses, AsMessageIds, AsRaw, AsText, AsURLs, Attachment, BlobId, Charset, ClientBody, ClientCid, ClientEmailBodyValue, ClientEmailBodyValueWithoutHeaders, ClientPartId, DateHeaderValue, DestroyIds, Disposition, EmailAddressGroup, EmailCreationId, EmailCreationRequest, EmailCreationResponse, EmailHeader, EmailHeaderName, EmailHeaderValue, EmailImport, EmailImportRequest, EmailImportResponse, EmailSetRequest, EmailSetResponse, EmailSetUpdate, GroupName, GroupedAddressesHeaderValue, HeaderMessageId, HeaderURL, IsEncodingProblem, IsTruncated, Keyword, Keywords, Language, Languages, Location, MailboxIds, MessageIdsHeaderValue, Name, ParseOption, RawHeaderValue, SpecificHeaderRequest, Subject, TextHeaderValue, ThreadId, Type, URLsHeaderValue, UnparsedMessageId}
import org.apache.james.mailbox.model.{MailboxId, MessageId}
import play.api.libs.json.{Format, JsArray, JsBoolean, JsError, JsNull, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OWrites, Reads, Writes}

import scala.util.Try

class EmailSetSerializer @Inject()(messageIdFactory: MessageId.Factory, mailboxIdFactory: MailboxId.Factory) {
  object EmailSetUpdateReads {
    def reads(jsObject: JsObject): JsResult[EmailSetUpdate] =
      asEmailSetUpdate(jsObject.value.map {
        case (property, value) => EntryValidation.from(property, value)
      }.toSeq)

    private def asEmailSetUpdate(entries: Seq[EntryValidation]): JsResult[EmailSetUpdate] =
      entries.flatMap(_.asJsError)
        .headOption
        .getOrElse({
          val mailboxReset: Option[MailboxIds] = entries.flatMap {
            case update: MailboxReset => Some(update)
            case _ => None
          }.headOption
            .map(_.ids)

          val keywordsReset: Option[Keywords] = entries.flatMap {
            case update: KeywordsReset => Some(update)
            case _ => None
          }.headOption
            .map(_.keywords)

          val mailboxesToAdd: Option[MailboxIds] = Some(entries
            .flatMap {
              case update: MailboxAddition => Some(update)
              case _ => None
            }.map(_.id).toList)
            .filter(_.nonEmpty)
            .map(MailboxIds)

          val mailboxesToRemove: Option[MailboxIds] = Some(entries
            .flatMap {
              case update: MailboxRemoval => Some(update)
              case _ => None
            }.map(_.id).toList)
            .filter(_.nonEmpty)
            .map(MailboxIds)

          val keywordsToAdd: Try[Option[Keywords]] = Some(entries
            .flatMap {
              case update: KeywordAddition => Some(update)
              case _ => None
            }.map(_.keyword).toSet)
            .filter(_.nonEmpty)
            .map(STRICT_KEYWORDS_FACTORY.fromSet)
            .sequence

          val keywordsToRemove: Try[Option[Keywords]] = Some(entries
            .flatMap {
              case update: KeywordRemoval => Some(update)
              case _ => None
            }.map(_.keyword).toSet)
            .filter(_.nonEmpty)
            .map(STRICT_KEYWORDS_FACTORY.fromSet)
            .sequence

          keywordsToAdd.flatMap(maybeKeywordsToAdd => keywordsToRemove
            .map(maybeKeywordsToRemove => (maybeKeywordsToAdd, maybeKeywordsToRemove)))
            .fold(e => JsError(e.getMessage),
              {
                case (maybeKeywordsToAdd, maybeKeywordsToRemove) => JsSuccess(EmailSetUpdate(keywords = keywordsReset,
                  keywordsToAdd = maybeKeywordsToAdd,
                  keywordsToRemove = maybeKeywordsToRemove,
                  mailboxIds = mailboxReset,
                  mailboxIdsToAdd = mailboxesToAdd,
                  mailboxIdsToRemove = mailboxesToRemove))
              })
        })

    object EntryValidation {
      private val mailboxIdPrefix: String = "mailboxIds/"
      private val keywordsPrefix: String = "keywords/"

      def from(property: String, value: JsValue): EntryValidation = property match {
        case "mailboxIds" => mailboxIdsReads.reads(value)
          .fold(
            e => InvalidPatchEntryValue(property, e.toString()),
            MailboxReset)
        case "keywords" => keywordsReads.reads(value)
          .fold(
            e => InvalidPatchEntryValue(property, e.toString()),
            KeywordsReset)
        case name if name.startsWith(mailboxIdPrefix) => Try(mailboxIdFactory.fromString(name.substring(mailboxIdPrefix.length)))
          .fold(e => InvalidPatchEntryNameWithDetails(property, e.getMessage),
            id => value match {
              case JsBoolean(true) => MailboxAddition(id)
              case JsNull => MailboxRemoval(id)
              case _ => InvalidPatchEntryValue(property, "MailboxId partial updates requires a JsBoolean(true) (set) or a JsNull (unset)")
            })
        case name if name.startsWith(keywordsPrefix) => Keyword.parse(name.substring(keywordsPrefix.length))
          .fold(e => InvalidPatchEntryNameWithDetails(property, e),
            keyword => value match {
              case JsBoolean(true) => KeywordAddition(keyword)
              case JsNull => KeywordRemoval(keyword)
              case _ => InvalidPatchEntryValue(property, "Keywords partial updates requires a JsBoolean(true) (set) or a JsNull (unset)")
            })
        case _ => InvalidPatchEntryName(property)
      }
    }

    sealed trait EntryValidation {
      def asJsError: Option[JsError] = None
    }

    private case class InvalidPatchEntryName(property: String) extends EntryValidation {
      override def asJsError: Option[JsError] = Some(JsError(s"$property is an invalid entry in an Email/set update patch"))
    }

    private case class InvalidPatchEntryNameWithDetails(property: String, cause: String) extends EntryValidation {
      override def asJsError: Option[JsError] = Some(JsError(s"$property is an invalid entry in an Email/set update patch: $cause"))
    }

    private case class InvalidPatchEntryValue(property: String, cause: String) extends EntryValidation {
      override def asJsError: Option[JsError] = Some(JsError(s"Value associated with $property is invalid: $cause"))
    }

    private case class MailboxAddition(id: MailboxId) extends EntryValidation

    private case class MailboxRemoval(id: MailboxId) extends EntryValidation

    private case class MailboxReset(ids: MailboxIds) extends EntryValidation

    private case class KeywordsReset(keywords: Keywords) extends EntryValidation

    private case class KeywordAddition(keyword: Keyword) extends EntryValidation

    private case class KeywordRemoval(keyword: Keyword) extends EntryValidation

  }

  private implicit val messageIdWrites: Writes[MessageId] = messageId => JsString(messageId.serialize)
  private implicit val messageIdReads: Reads[MessageId] = {
    case JsString(serializedMessageId) => Try(JsSuccess(messageIdFactory.fromString(serializedMessageId)))
      .fold(_ => JsError("Invalid messageId"), messageId => messageId)
    case _ => JsError("Expecting messageId to be represented by a JsString")
  }

  private implicit val mailboxIdsMapReads: Reads[Map[MailboxId, Boolean]] =
    Reads.mapReads[MailboxId, Boolean] {s => Try(mailboxIdFactory.fromString(s)).fold(e => JsError(e.getMessage), JsSuccess(_)) } (mapMarkerReads)

  private implicit val mailboxIdsReads: Reads[MailboxIds] = jsValue => mailboxIdsMapReads.reads(jsValue).map(
    mailboxIdsMap => MailboxIds(mailboxIdsMap.keys.toList))

  private implicit val emailSetUpdateReads: Reads[EmailSetUpdate] = {
    case o: JsObject => EmailSetUpdateReads.reads(o)
    case _ => JsError("Expecting a JsObject to represent an EmailSetUpdate")
  }

  private implicit val updatesMapReads: Reads[Map[UnparsedMessageId, JsObject]] =
    Reads.mapReads[UnparsedMessageId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"messageId needs to match id constraints: $e"),
        id => JsSuccess(UnparsedMessageId(id))) }

  private implicit val createsMapReads: Reads[Map[EmailCreationId, JsObject]] =
    Reads.mapReads[EmailCreationId, JsObject] {s => refineV[IdConstraint](s)
      .fold(e => JsError(s"email creationId needs to match id constraints: $e"),
        id => JsSuccess(EmailCreationId(id))) }

  private implicit val keywordReads: Reads[Keyword] = {
    case jsString: JsString => Keyword.parse(jsString.value)
      .fold(JsError(_),
        JsSuccess(_))
    case _ => JsError("Expecting a string as a keyword")
  }

  private implicit val keywordsMapReads: Reads[Map[Keyword, Boolean]] =
    Reads.mapReads[Keyword, Boolean] {string => Keyword.parse(string).fold(JsError(_), JsSuccess(_)) } (mapMarkerReads)
  private implicit val keywordsReads: Reads[Keywords] = jsValue => keywordsMapReads.reads(jsValue).flatMap(
    keywordsMap => STRICT_KEYWORDS_FACTORY.fromSet(keywordsMap.keys.toSet)
      .fold(e => JsError(e.getMessage), keywords => JsSuccess(keywords)))

  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]
  private implicit val blobIdReads: Reads[BlobId] = {
    case JsString(string) => BlobId.of(string)
      .toEither.fold(
        e => JsError(s"blobId does not match Id constraints: $e"),
        blobId => JsSuccess(blobId))
    case _ => JsError("blobId needs to be represented by a JsString")
  }
  private implicit val unparsedMessageIdWrites: Writes[UnparsedMessageId] = Json.valueWrites[UnparsedMessageId]
  private implicit val unparsedMessageIdReads: Reads[UnparsedMessageId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"messageId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedMessageId(id)))
    case _ => JsError("messageId needs to be represented by a JsString")
  }
  private implicit val unitWrites: Writes[Unit] = _ => JsNull
  private implicit val updatedWrites: Writes[Map[MessageId, Unit]] = mapWrites[MessageId, Unit](_.serialize, unitWrites)
  private implicit val notDestroyedWrites: Writes[Map[UnparsedMessageId, SetError]] = mapWrites[UnparsedMessageId, SetError](_.id.value, setErrorWrites)
  private implicit val destroyIdsReads: Reads[DestroyIds] = Json.valueFormat[DestroyIds]
  private implicit val destroyIdsWrites: Writes[DestroyIds] = Json.valueWrites[DestroyIds]
  private implicit val emailRequestSetReads: Reads[EmailSetRequest] = Json.reads[EmailSetRequest]
  private implicit val threadIdWrites: Writes[ThreadId] = Json.valueWrites[ThreadId]
  private implicit val emailCreationResponseWrites: Writes[EmailCreationResponse] = Json.writes[EmailCreationResponse]
  private implicit val createsMapWrites: Writes[Map[EmailCreationId, EmailCreationResponse]] =
    mapWrites[EmailCreationId, EmailCreationResponse](_.id.value, emailCreationResponseWrites)
  private implicit val notCreatedMapWrites: Writes[Map[EmailCreationId, SetError]] =
    mapWrites[EmailCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val emailResponseSetWrites: OWrites[EmailSetResponse] = Json.writes[EmailSetResponse]

  private implicit val subjectReads: Reads[Subject] = Json.valueReads[Subject]
  private implicit val emailerNameReads: Reads[EmailerName] = Json.valueReads[EmailerName]
  private implicit val headerMessageIdReads: Reads[HeaderMessageId] = Json.valueReads[HeaderMessageId]
  private implicit val emailAddressReads: Reads[EmailAddress] = Json.reads[EmailAddress]
  private implicit val addressesHeaderValueReads: Reads[AddressesHeaderValue] = Json.valueReads[AddressesHeaderValue]
  private implicit val messageIdsHeaderValueReads: Reads[MessageIdsHeaderValue] = {
    case JsArray(value) => value.map(headerMessageIdReads.reads)
      .map(_.asEither)
      .toList
      .sequence
      .fold(e => JsError(e),
        ids => JsSuccess(MessageIdsHeaderValue(Some(ids).filter(_.nonEmpty))))
    case jsValue => JsError(s"expected JsArray, got $jsValue")
  }

  private implicit val isTruncatedReads: Reads[IsTruncated] = Json.valueReads[IsTruncated]
  private implicit val isEncodingProblemReads: Reads[IsEncodingProblem] = Json.valueReads[IsEncodingProblem]
  private implicit val clientEmailBodyValueWithoutHeadersReads: Reads[ClientEmailBodyValueWithoutHeaders] = Json.reads[ClientEmailBodyValueWithoutHeaders]
  private implicit val clientEmailBodyValueReads: Reads[ClientEmailBodyValue] = {
    case o: JsObject =>
      if (o.value.contains("headers")) {
        JsError("'headers' is not allowed")
      } else {
        extractSpecificHeaders(o).fold(e => JsError(e.getMessage),
          specificHeaders => clientEmailBodyValueWithoutHeadersReads.reads(o).map(_.withHeaders(specificHeaders)))
      }

    case _ => JsError("Expecting a JsObject to represent a creation request")
  }

  private def extractSpecificHeaders(o: JsObject): Either[IllegalArgumentException, List[EmailHeader]] =
    o.value.toList
      .filter {
        case (name, _) => name.startsWith("header:")
      }.map {
      case (name, value) =>
        val refinedName: Either[String, NonEmptyString] = refineV[NonEmpty](name)
        refinedName.left.map(e => new IllegalArgumentException(e))
          .flatMap(property => SpecificHeaderRequest.from(property)
            .left.map(_ => new IllegalArgumentException(s"$name is an invalid specific header")))
          .flatMap(_.validate)
          .flatMap(specificHeaderRequest => asReads(specificHeaderRequest)
            .reads(value).asEither.left.map(e => new IllegalArgumentException(e.toString()))
            .map(headerValue => EmailHeader(EmailHeaderName(specificHeaderRequest.headerName), headerValue)))
    }.sequence

  private implicit val typeReads: Reads[Type] = Json.valueReads[Type]
  private implicit val clientPartIdReads: Reads[ClientPartId] = Json.valueReads[ClientPartId]
  private val rawClientBodyReads: Reads[ClientBody] = Json.reads[ClientBody]
  private implicit val clientBodyReads: Reads[ClientBody] = {
    case JsObject(underlying) if underlying.contains("charset") => JsError("charset must not be specified in htmlBody")
    case JsObject(underlying) if underlying.contains("size") => JsError("size must not be specified in htmlBody")
    case JsObject(underlying) if underlying.contains("header:Content-Transfer-Encoding") => JsError("Content-Transfer-Encoding must not be specified in htmlBody or textBody")
    case JsObject(underlying) if underlying.keySet.exists(s => s.startsWith("header:Content-Transfer-Encoding:asText")) => JsError("Content-Transfer-Encoding must not be specified in htmlBody or textBody")
    case o: JsObject => rawClientBodyReads.reads(o)
    case _ => JsError("Expecting a JsObject to represent an ClientHtmlBody")
  }

  private implicit val bodyValuesReads: Reads[Map[ClientPartId, ClientEmailBodyValue]] =
    Reads.mapReads[ClientPartId, ClientEmailBodyValue] {
      s => Id.validate(s).fold(
        e => JsError(e.getMessage),
        partId => JsSuccess(ClientPartId(partId)))
    }

  case class EmailCreationRequestWithoutHeaders(mailboxIds: MailboxIds,
                                                messageId: Option[MessageIdsHeaderValue],
                                                references: Option[MessageIdsHeaderValue],
                                                inReplyTo: Option[MessageIdsHeaderValue],
                                                from: Option[AddressesHeaderValue],
                                                to: Option[AddressesHeaderValue],
                                                cc: Option[AddressesHeaderValue],
                                                bcc: Option[AddressesHeaderValue],
                                                sender: Option[AddressesHeaderValue],
                                                replyTo: Option[AddressesHeaderValue],
                                                subject: Option[Subject],
                                                sentAt: Option[UTCDate],
                                                keywords: Option[Keywords],
                                                receivedAt: Option[UTCDate],
                                                htmlBody: Option[List[ClientBody]],
                                                textBody: Option[List[ClientBody]],
                                                bodyValues: Option[Map[ClientPartId, ClientEmailBodyValue]],
                                                attachments: Option[List[Attachment]]) {
    def toCreationRequest(specificHeaders: List[EmailHeader]): EmailCreationRequest = EmailCreationRequest(
      mailboxIds = mailboxIds,
      messageId = messageId,
      references = references,
      inReplyTo = inReplyTo,
      from = from,
      to = to,
      cc = cc,
      bcc = bcc,
      sender = sender,
      replyTo = replyTo,
      subject = subject,
      sentAt = sentAt,
      keywords = keywords,
      receivedAt = receivedAt,
      specificHeaders = specificHeaders,
      bodyValues = bodyValues,
      htmlBody = htmlBody,
      textBody = textBody,
      attachments = attachments)
  }

  private implicit val headerUrlReads: Reads[HeaderURL] = Json.valueReads[HeaderURL]
  private implicit val groupNameReads: Reads[GroupName] = Json.valueReads[GroupName]
  private implicit val groupReads: Reads[EmailAddressGroup] = Json.reads[EmailAddressGroup]

  private implicit val dateReads: Reads[DateHeaderValue] = {
    case JsNull => JsSuccess(DateHeaderValue(None))
    case json: JsValue => UTCDateReads.reads(json).map(date => DateHeaderValue(Some(date)))
  }

  sealed trait HeaderValueReads extends Reads[EmailHeaderValue]
  case object RawReads extends HeaderValueReads {
    val rawReads: Reads[RawHeaderValue] = Json.valueReads[RawHeaderValue]
    override def reads(json: JsValue): JsResult[EmailHeaderValue] = rawReads.reads(json)
  }
  case object AllReads extends HeaderValueReads {
    val rawReads: Reads[RawHeaderValue] = Json.valueReads[RawHeaderValue]
    override def reads(json: JsValue): JsResult[EmailHeaderValue] = rawReads.reads(json)
  }
  case object TextReads extends HeaderValueReads {
    val textReads: Reads[TextHeaderValue] = Json.valueReads[TextHeaderValue]
    override def reads(json: JsValue): JsResult[TextHeaderValue] = textReads.reads(json)
  }
  case object AddressesReads extends HeaderValueReads {
    override def reads(json: JsValue): JsResult[AddressesHeaderValue] = addressesHeaderValueReads.reads(json)
  }
  case object DateReads extends HeaderValueReads {
    override def reads(json: JsValue): JsResult[DateHeaderValue] = dateReads.reads(json)
  }
  case object MessageIdReads extends HeaderValueReads {
    override def reads(json: JsValue): JsResult[MessageIdsHeaderValue] = messageIdsHeaderValueReads.reads(json)
  }
  case object URLReads extends HeaderValueReads {
    val urlsReads: Reads[URLsHeaderValue] = {
      case JsNull => JsSuccess(URLsHeaderValue(None))
      case JsArray(value) => value.map(headerUrlReads.reads).map(_.asEither).toList.sequence
        .fold(e => JsError(e), urls => JsSuccess(URLsHeaderValue(Some(urls))))
      case _ => JsError("Expecting a JsArray")
    }
    override def reads(json: JsValue): JsResult[URLsHeaderValue] = urlsReads.reads(json)
  }
  case object GroupedAddressReads extends HeaderValueReads {
    val groupsReads: Reads[GroupedAddressesHeaderValue] = Json.valueReads[GroupedAddressesHeaderValue]
    override def reads(json: JsValue): JsResult[GroupedAddressesHeaderValue] = groupsReads.reads(json)
  }

  def asReads(parseOption: ParseOption): Reads[EmailHeaderValue] = parseOption match {
      case AsRaw => RawReads
      case AsText => TextReads
      case AsAddresses => AddressesReads
      case AsDate => DateReads
      case AsMessageIds => MessageIdReads
      case AsURLs => URLReads
      case AsGroupedAddresses => GroupedAddressReads
    }

  def asReads(specificHeaderRequest: SpecificHeaderRequest): Reads[EmailHeaderValue] = {
    val option = specificHeaderRequest.parseOption.getOrElse(AsRaw)
    if (specificHeaderRequest.isAll) {
      case array: JsArray =>
        val valueReads = asReads(option)
        val value: JsResult[List[EmailHeaderValue]] = array.value.toList.map(v => valueReads.reads(v))
          .foldLeft[JsResult[List[EmailHeaderValue]]](JsSuccess(List())) {
            case (acc, value) => value.fold(invalid => JsError(invalid), valid => acc.map(v => valid :: v))
          }
        value.map(_.reverse).map(AllHeaderValues)
      case _ => JsError("Expecting an array of headers when specifying :all as header qualifier")
    } else {
      asReads(option)
    }
  }

  private implicit val nameReads: Reads[Name] = Json.valueReads[Name]
  private implicit val charsetReads: Reads[Charset] = Json.valueReads[Charset]
  private implicit val dispositionReads: Reads[Disposition] = Json.valueReads[Disposition]
  private implicit val languageReads: Format[Language] = Json.valueFormat[Language]
  private implicit val languagesWrites: Format[Languages] = Json.valueFormat[Languages]
  private implicit val locationReads: Reads[Location] = Json.valueReads[Location]
  private implicit val cidFormat: Format[ClientCid] = Json.valueFormat[ClientCid]
  private implicit val attachmentReads: Reads[Attachment] = {
    case JsObject(keys) if keys.keys.exists(_.contains("header:Content-Transfer-Encoding")) =>
      JsError("Content-Transfer-Encoding should not be specified on attachment")
    case JsObject(keys) if keys.keys.exists(_.startsWith("header:Content-Transfer-Encoding:")) =>
      JsError("Content-Transfer-Encoding should not be specified on attachment")
    case jsValue: JsValue => Json.reads[Attachment].reads(jsValue)
  }
  private implicit val emailCreationRequestWithoutHeadersReads: Reads[EmailCreationRequestWithoutHeaders] = Json.reads[EmailCreationRequestWithoutHeaders]
  private implicit val emailCreationRequestReads: Reads[EmailCreationRequest] = {
    case o: JsObject =>
      if(o.value.contains("headers")) {
        JsError("'headers' is not allowed")
      } else {
        extractSpecificHeaders(o)
          .fold(e => JsError(e.getMessage),
          specificHeaders => emailCreationRequestWithoutHeadersReads.reads(o).map(_.toCreationRequest(specificHeaders)))
      }
    case _ => JsError("Expecting a JsObject to represent a creation request")
  }
  private implicit val emailImportReads: Reads[EmailImport] = Json.reads[EmailImport]
  private implicit val importMapRead: Reads[Map[EmailCreationId, EmailImport]] =
    Reads.mapReads[EmailCreationId, EmailImport]{s => refineV[IdConstraint](s)
      .fold(e => JsError(s"email creationId needs to match id constraints: $e"),
        id => JsSuccess(EmailCreationId(id))) } (emailImportReads)
  private implicit val emailImportRequestReads: Reads[EmailImportRequest] = Json.reads[EmailImportRequest]
  private implicit val emailImportResponseWrite: OWrites[EmailImportResponse] = Json.writes[EmailImportResponse]

  def deserialize(input: JsValue): JsResult[EmailSetRequest] = Json.fromJson[EmailSetRequest](input)

  def deserializeCreationRequest(input: JsValue): JsResult[EmailCreationRequest] = Json.fromJson[EmailCreationRequest](input)

  def deserializeEmailSetUpdate(input: JsValue): JsResult[EmailSetUpdate] = Json.fromJson[EmailSetUpdate](input)

  def serialize(response: EmailSetResponse): JsObject = Json.toJsObject(response)

  def deserializeEmailImportRequest(input: JsValue): JsResult[EmailImportRequest] = Json.fromJson[EmailImportRequest](input)

  def serializeEmailImportResponse(response: EmailImportResponse): JsObject = Json.toJsObject(response)
}
