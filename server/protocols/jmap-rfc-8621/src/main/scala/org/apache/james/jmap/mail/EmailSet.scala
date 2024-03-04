/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap.mail

import java.nio.charset.{StandardCharsets, Charset => NioCharset}
import java.util.Date

import cats.implicits._
import com.google.common.net.MediaType
import com.google.common.net.MediaType.{HTML_UTF_8, PLAIN_TEXT_UTF_8}
import eu.timepit.refined
import org.apache.james.core.MailAddress
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName}
import org.apache.james.jmap.core.Id.{Id, IdConstraint}
import org.apache.james.jmap.core.{AccountId, SetError, UTCDate, UuidState}
import org.apache.james.jmap.mail.Disposition.INLINE
import org.apache.james.jmap.mail.EmailCreationRequest.KEYWORD_DRAFT
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.jmap.routes.{Blob, BlobResolvers}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.{Cid, MessageId}
import org.apache.james.mime4j.codec.EncoderUtil.Usage
import org.apache.james.mime4j.codec.{DecodeMonitor, EncoderUtil}
import org.apache.james.mime4j.dom.address.{AddressList, MailboxList, Mailbox => Mime4jMailbox}
import org.apache.james.mime4j.dom.field.{ContentIdField, ContentTypeField, FieldName}
import org.apache.james.mime4j.dom.{Entity, Message}
import org.apache.james.mime4j.field.{ContentIdFieldImpl, Fields}
import org.apache.james.mime4j.message.{BodyPartBuilder, MultipartBuilder}
import org.apache.james.mime4j.stream.{Field, NameValuePair, RawField}
import org.apache.james.mime4j.util.MimeUtil
import org.apache.james.util.html.HtmlTextExtractor
import play.api.libs.json.JsObject

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Right, Try, Using}

object EmailSet {
  def asUnparsed(messageId: MessageId): UnparsedMessageId = refined.refineV[IdConstraint](messageId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => UnparsedMessageId(value)
  }

  def parse(messageIdFactory: MessageId.Factory)(unparsed: UnparsedMessageId): Try[MessageId] =
    Try(messageIdFactory.fromString(unparsed.id.value))
}

case class EmailCreationId(id: Id)
case class UnparsedMessageId(id: Id)

object SubType {
  val HTML_SUBTYPE = "html"
  val MIXED_SUBTYPE = "mixed"
  val RELATED_SUBTYPE = "related"
  val ALTERNATIVE_SUBTYPE = "alternative"
}

case class ClientPartId(id: Id)

case class ClientBody(partId: ClientPartId, `type`: Type)

case class ClientEmailBodyValueWithoutHeaders(value: String,
                                isEncodingProblem: Option[IsEncodingProblem],
                                isTruncated: Option[IsTruncated]) {
  def withHeaders(specificHeaders: List[EmailHeader]): ClientEmailBodyValue =
    ClientEmailBodyValue(value, isEncodingProblem, isTruncated, specificHeaders)
}

case class ClientEmailBodyValue(value: String,
                                isEncodingProblem: Option[IsEncodingProblem],
                                isTruncated: Option[IsTruncated],
                                specificHeaders: List[EmailHeader])

case class ClientBodyPart(value: String, specificHeaders: List[EmailHeader])

object ClientCid {
  def of(entity: Entity): Option[Cid] =
    Option(entity.getHeader.getField(FieldName.CONTENT_ID))
      .flatMap {
        case contentIdField: ContentIdField => Cid.parser().relaxed().unwrap().parse(contentIdField.getId).toScala
        case _ => None
      }
}

case class ClientCid(value: String) {
  def asField: Field = ContentIdFieldImpl.PARSER.parse(new RawField("Content-ID", value), DecodeMonitor.SILENT)
}

case class Attachment(blobId: BlobId,
                      `type`: Type,
                      name: Option[Name],
                      charset: Option[Charset],
                      disposition: Option[Disposition],
                      language: Option[Languages],
                      location: Option[Location],
                      cid: Option[ClientCid]) {

  def isInline: Boolean = disposition.contains(INLINE)
}

case class UncheckedEmail(value: String) extends AnyVal

object UncheckedEmailAddress {
  def from(addressList: AddressList): List[UncheckedEmailAddress] = Option(addressList)
    .map(addressList => from(addressList.flatten()))
    .getOrElse(List())

  def from(addressList: MailboxList): List[UncheckedEmailAddress] =
    addressList.asScala
      .toList
      .filter(address => !address.getAddress.equals(">"))  // Temporary fix for https://github.com/linagora/james-project/issues/5086
      .map(mailbox => UncheckedEmailAddress(
        name = Option(mailbox.getName).map(EmailerName.from),
        email = UncheckedEmail(mailbox.getAddress)))
}
case class UncheckedEmailAddress(name: Option[EmailerName], email: UncheckedEmail) {
  def asMime4JMailbox: Mime4jMailbox = {
    val parts = email.value.split('@')
    val domainPart: String = parts match {
      case Array(_, domain) => domain
      case _ => ""
    }
    Some(email.value.split('@'))
      .map(parts => new Mime4jMailbox(
        name.map(_.value).orNull,
        parts.head,
        domainPart))
      .get
  }

  def validate: Either[IllegalArgumentException, EmailAddress] =
    Try(new MailAddress(email.value))
      .map(email => EmailAddress(name, email))
      .toEither match {
      case scala.Right(value) => scala.Right(value)
      case Left(e) => Left(new IllegalArgumentException(s"Invalid email address `${email.value}`", e))
    }
}

case class UncheckedAddressesHeaderValue(value: List[UncheckedEmailAddress]) {
  def asMime4JMailboxList: Option[List[Mime4jMailbox]] = Some(value.map(_.asMime4JMailbox)).filter(_.nonEmpty)

  def validate: Either[IllegalArgumentException, AddressesHeaderValue] = value.map(_.validate)
    .sequence
    .map(l => AddressesHeaderValue(l))
}

object EmailCreationRequest {
  val KEYWORD_DRAFT: Keyword = org.apache.james.jmap.mail.Keyword("$draft")
}
case class EmailCreationRequest(mailboxIds: MailboxIds,
                                messageId: Option[MessageIdsHeaderValue],
                                references: Option[MessageIdsHeaderValue],
                                inReplyTo: Option[MessageIdsHeaderValue],
                                from: Option[UncheckedAddressesHeaderValue],
                                to: Option[UncheckedAddressesHeaderValue],
                                cc: Option[UncheckedAddressesHeaderValue],
                                bcc: Option[UncheckedAddressesHeaderValue],
                                sender: Option[UncheckedAddressesHeaderValue],
                                replyTo: Option[UncheckedAddressesHeaderValue],
                                subject: Option[Subject],
                                sentAt: Option[UTCDate],
                                keywords: Option[Keywords],
                                receivedAt: Option[UTCDate],
                                htmlBody: Option[List[ClientBody]],
                                textBody: Option[List[ClientBody]],
                                bodyValues: Option[Map[ClientPartId, ClientEmailBodyValue]],
                                specificHeaders: List[EmailHeader],
                                attachments: Option[List[Attachment]]) {
  def toMime4JMessage(blobResolvers: BlobResolvers,
                      htmlTextExtractor: HtmlTextExtractor,
                      mailboxSession: MailboxSession): Either[Throwable, Message] =
    validateHtmlBody
      .flatMap(maybeHtmlBody => validateTextBody.map((maybeHtmlBody, _)))
      .flatMap {
        case (maybeHtmlBody, maybeTextBody) =>
          val builder = Message.Builder.of
          references.flatMap(_.asString).map(new RawField("References", _)).foreach(builder.setField)
          inReplyTo.flatMap(_.asString).map(new RawField("In-Reply-To", _)).foreach(builder.setField)
          subject.foreach(value => builder.setSubject(value.value))
          val maybeFrom: Option[List[Mime4jMailbox]] = from.flatMap(_.asMime4JMailboxList)
          maybeFrom.map(_.asJava).foreach(builder.setFrom)
          to.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setTo)
          cc.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setCc)
          bcc.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setBcc)
          sender.flatMap(_.asMime4JMailboxList).map(_.asJava).map(Fields.addressList(FieldName.SENDER, _)).foreach(builder.setField)
          replyTo.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setReplyTo)
          builder.setDate( sentAt.map(_.asUTC).map(_.toInstant).map(Date.from).getOrElse(new Date()))
          builder.setField(new RawField(FieldName.MESSAGE_ID, messageId.flatMap(_.asString).getOrElse(generateUniqueMessageId(maybeFrom))))
          validateSpecificHeaders(builder)
            .flatMap(_ => {
              specificHeaders.flatMap(_.asFields).foreach(builder.addField)
              attachments.filter(_.nonEmpty).map(attachments =>
                createMultipartWithAttachments(maybeHtmlBody, maybeTextBody, attachments, blobResolvers, htmlTextExtractor, mailboxSession)
                  .map(multipartBuilder => {
                    builder.setBody(multipartBuilder)
                    builder.build
                  }))
                .getOrElse({
                  builder.setBody(createAlternativeBody(maybeHtmlBody, maybeTextBody, htmlTextExtractor))
                  Right(builder.build)
                })
            })
      }

  private def generateUniqueMessageId(fromAddress: Option[List[Mime4jMailbox]]): String =
    MimeUtil.createUniqueMessageId(fromAddress.flatMap(_.headOption).map(_.getDomain).orNull)

  private def createAlternativeBody(htmlBody: Option[ClientBodyPart], textBody: Option[ClientBodyPart], htmlTextExtractor: HtmlTextExtractor) = {
    val alternativeBuilder = MultipartBuilder.create(SubType.ALTERNATIVE_SUBTYPE)
    val replacement: ClientBodyPart = textBody.getOrElse(ClientBodyPart(
      htmlTextExtractor.toPlainText(htmlBody.map(_.value).getOrElse("")),
      htmlBody.map(_.specificHeaders).getOrElse(List())))
    addBodypart(alternativeBuilder, replacement, PLAIN_TEXT_UTF_8, StandardCharsets.UTF_8)
    htmlBody.foreach(text => addBodypart(alternativeBuilder, text, HTML_UTF_8, StandardCharsets.UTF_8))

    alternativeBuilder
  }

  private def addBodypart(multipartBuilder: MultipartBuilder, body: ClientBodyPart, mediaType: MediaType, charset: NioCharset): MultipartBuilder = {
    val bodyPartBuilder = BodyPartBuilder.create.setBody(body.value, charset)
      .setContentType(mediaType.withoutParameters().toString, new NameValuePair("charset", charset.name))
      .setContentTransferEncoding("quoted-printable")

    body.specificHeaders
      .flatMap(_.asFields)
      .foreach(field => bodyPartBuilder.addField(field))

    multipartBuilder.addBodyPart(bodyPartBuilder)
  }

  private def createMultipartWithAttachments(maybeHtmlBody: Option[ClientBodyPart],
                                             maybeTextBody: Option[ClientBodyPart],
                                             attachments: List[Attachment],
                                             blobResolvers: BlobResolvers,
                                             htmlTextExtractor: HtmlTextExtractor,
                                             mailboxSession: MailboxSession): Either[Throwable, MultipartBuilder] = {
    val maybeAttachments: Either[Throwable, List[LoadedAttachment]] =
      attachments
        .map(loadWithMetadata(blobResolvers, mailboxSession))
        .sequence

    maybeAttachments.map(list => {
      (list.filter(_.isInline), list.filter(!_.isInline)) match {
        case (Nil, normalAttachments) => createMixedBody(maybeHtmlBody, maybeTextBody, normalAttachments, htmlTextExtractor)
        case (inlineAttachments, Nil) => createRelatedBody(maybeHtmlBody, maybeTextBody, inlineAttachments, htmlTextExtractor)
        case (inlineAttachments, normalAttachments) => createMixedRelatedBody(maybeHtmlBody, maybeTextBody, inlineAttachments, normalAttachments, htmlTextExtractor)
      }
    })
  }

  private def loadWithMetadata(blobResolvers: BlobResolvers, mailboxSession: MailboxSession)(attachment: Attachment): Either[Throwable, LoadedAttachment] =
    Try(blobResolvers.resolve(attachment.blobId, mailboxSession).block())
      .toEither.flatMap(blob => load(blob).map(content => LoadedAttachment(attachment, blob, content)))

  private def load(blob: Blob): Either[Throwable, Array[Byte]] =
    Using(blob.content) {
      _.readAllBytes()
    }.toEither

  private def createMixedRelatedBody(maybeHtmlBody: Option[ClientBodyPart],
                                     maybeTextBody: Option[ClientBodyPart],
                                     inlineAttachments: List[LoadedAttachment],
                                     normalAttachments: List[LoadedAttachment],
                                     htmlTextExtractor: HtmlTextExtractor): MultipartBuilder = {
    val mixedMultipartBuilder = MultipartBuilder.create(SubType.MIXED_SUBTYPE)
    val relatedMultipartBuilder = MultipartBuilder.create(SubType.RELATED_SUBTYPE)
    relatedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(createAlternativeBody(maybeHtmlBody, maybeTextBody, htmlTextExtractor).build))
    inlineAttachments.foldLeft(relatedMultipartBuilder) {
      case (acc, loadedAttachment) =>
        acc.addBodyPart(toBodypartBuilder(loadedAttachment))
        acc
    }

    mixedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(relatedMultipartBuilder.build))

    normalAttachments.foldLeft(mixedMultipartBuilder) {
      case (acc, loadedAttachment) =>
        acc.addBodyPart(toBodypartBuilder(loadedAttachment))
        acc
    }
  }

  private def createMixedBody(maybeHtmlBody: Option[ClientBodyPart], maybeTextBody: Option[ClientBodyPart], normalAttachments: List[LoadedAttachment], htmlTextExtractor: HtmlTextExtractor) = {
    val mixedMultipartBuilder = MultipartBuilder.create(SubType.MIXED_SUBTYPE)
    mixedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(createAlternativeBody(maybeHtmlBody, maybeTextBody, htmlTextExtractor).build))
    normalAttachments.foldLeft(mixedMultipartBuilder) {
      case (acc, loadedAttachment) =>
        acc.addBodyPart(toBodypartBuilder(loadedAttachment))
        acc
    }
  }

  private def createRelatedBody(maybeHtmlBody: Option[ClientBodyPart], maybeTextBody: Option[ClientBodyPart], inlineAttachments: List[LoadedAttachment], htmlTextExtractor: HtmlTextExtractor) = {
    val relatedMultipartBuilder = MultipartBuilder.create(SubType.RELATED_SUBTYPE)
    relatedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(createAlternativeBody(maybeHtmlBody, maybeTextBody, htmlTextExtractor).build))
    inlineAttachments.foldLeft(relatedMultipartBuilder) {
      case (acc, loadedAttachment) =>
        acc.addBodyPart(toBodypartBuilder(loadedAttachment))
        acc
    }
    relatedMultipartBuilder
  }

  private def toBodypartBuilder(loadedAttachment: LoadedAttachment) = {
    val bodypartBuilder = BodyPartBuilder.create()
    val attachment = loadedAttachment.attachment
    val blob = loadedAttachment.blob
    bodypartBuilder.setBody(loadedAttachment.content, attachment.`type`.value)
      .setField(contentTypeField(attachment, blob))
      .setContentDisposition(attachment.disposition.getOrElse(Disposition.ATTACHMENT).value)
      .setContentTransferEncoding("base64")
    attachment.cid.map(_.asField).foreach(bodypartBuilder.addField)
    attachment.location.map(_.asField).foreach(bodypartBuilder.addField)
    attachment.language.map(_.asField).foreach(bodypartBuilder.addField)
    bodypartBuilder
  }

  private def contentTypeField(attachment: Attachment, blob: Blob): ContentTypeField = {
    val typeAsField: ContentTypeField = blob.contentType.asMime4J
    val parameterBuilder = Map.newBuilder[String, String]
      .addAll(parametersWithoutNameAndCharset(typeAsField))

    attachment.name.foreach(name => parameterBuilder.addOne("name", EncoderUtil.encodeEncodedWord(name.value, Usage.TEXT_TOKEN)))
    attachment.charset.map(c => c.value).orElse(Option(typeAsField.getCharset))
      .foreach(charset => parameterBuilder.addOne("charset", charset))

    Fields.contentType(typeAsField.getMimeType,
      parameterBuilder.result.asJava)
  }

  private def parametersWithoutNameAndCharset(typeAsField: ContentTypeField): Map[String, String] =
    typeAsField.getParameters
      .asScala
      .filter(!_._1.equalsIgnoreCase("name"))
      .filter(!_._1.equalsIgnoreCase("charset"))
      .toMap

  def validateHtmlBody: Either[IllegalArgumentException, Option[ClientBodyPart]] = htmlBody match {
    case None => Right(None)
    case Some(html :: Nil) if !html.`type`.value.equals("text/html") => Left(new IllegalArgumentException("Expecting htmlBody type to be text/html"))
    case Some(html :: Nil) => retrieveCorrespondingBody(html.partId)
      .getOrElse(Left(new IllegalArgumentException("Expecting bodyValues to contain the part specified in htmlBody")))
    case _ => Left(new IllegalArgumentException("Expecting htmlBody to contains only 1 part"))
  }

  def validateTextBody: Either[IllegalArgumentException, Option[ClientBodyPart]] = textBody match {
    case None => Right(None)
    case Some(text :: Nil) if !text.`type`.value.equals("text/plain") => Left(new IllegalArgumentException("Expecting htmlBody type to be text/html"))
    case Some(text :: Nil) => retrieveCorrespondingBody(text.partId)
      .getOrElse(Left(new IllegalArgumentException("Expecting bodyValues to contain the part specified in textBody")))
    case _ => Left(new IllegalArgumentException("Expecting textBody to contains only 1 part"))
  }

  def validateRequest: Either[IllegalArgumentException, EmailCreationRequest] = validateEmailAddressHeader

  def validateEmailAddressHeader: Either[IllegalArgumentException, EmailCreationRequest] = keywords match {
    case Some(k) if k.keywords.contains(KEYWORD_DRAFT) => scala.Right(this)
    case _ => doValidateEmailAddressHeader()
  }

  private def doValidateEmailAddressHeader(): Either[IllegalArgumentException, EmailCreationRequest] = {
    val addressesHeaderInvalid: Map[String, IllegalArgumentException] = Map("from" -> from, "to" -> to, "cc" -> cc, "bcc" -> bcc, "sender" -> sender, "replyTo" -> replyTo)
      .map {
        case (name, maybeAddresses) => (name, maybeAddresses.map(_.validate))
      }.collect { case (name, Some(addresses)) => (name, addresses) }
      .collect { case (name, scala.Left(exception)) => (name, exception) }

    addressesHeaderInvalid match {
      case invalid if invalid.nonEmpty => Left(new IllegalArgumentException(s"/${addressesHeaderInvalid.map { case (name, exception) => s"$name: ${exception.getMessage}" }.mkString(", ")}"))
      case _ => scala.Right(this)
    }
  }

  private def retrieveCorrespondingBody(partId: ClientPartId): Option[Either[IllegalArgumentException, Some[ClientBodyPart]]] =
    bodyValues.getOrElse(Map())
      .get(partId)
      .map {
        case part if part.isTruncated.isDefined && part.isTruncated.get.value => Left(new IllegalArgumentException("Expecting isTruncated to be false"))
        case part if part.isEncodingProblem.isDefined && part.isEncodingProblem.get.value => Left(new IllegalArgumentException("Expecting isEncodingProblem to be false"))
        case part => Right(Some(
          ClientBodyPart(part.value, part.specificHeaders)))
      }

  private def validateSpecificHeaders(message: Message.Builder): Either[IllegalArgumentException, Unit] = {
    specificHeaders.map(header => {
      if (Option(message.getField(header.name.value)).isDefined) {
        Left(new IllegalArgumentException(s"${header.name.value} was already defined by convenience headers"))
      } else if (header.name.value.startsWith("Content-")) {
        Left(new IllegalArgumentException(s"Header fields beginning with `Content-` MUST NOT be specified on the Email object, only on EmailBodyPart objects."))
      } else {
        scala.Right(())
      }
    }).sequence.map(_ => ())
  }
}

case class LoadedAttachment(attachment: Attachment, blob: Blob, content: Array[Byte]) {
  def isInline: Boolean = attachment.isInline
}

case class DestroyIds(value: Seq[UnparsedMessageId])

case class EmailSetRequest(accountId: AccountId,
                           create: Option[Map[EmailCreationId, JsObject]],
                           update: Option[Map[UnparsedMessageId, JsObject]],
                           destroy: Option[DestroyIds]) extends WithAccountId

case class EmailSetResponse(accountId: AccountId,
                            oldState: Option[UuidState],
                            newState: UuidState,
                            created: Option[Map[EmailCreationId, EmailCreationResponse]],
                            notCreated: Option[Map[EmailCreationId, SetError]],
                            updated: Option[Map[MessageId, Unit]],
                            notUpdated: Option[Map[UnparsedMessageId, SetError]],
                            destroyed: Option[DestroyIds],
                            notDestroyed: Option[Map[UnparsedMessageId, SetError]])

case class EmailSetUpdate(keywords: Option[Keywords],
                          keywordsToAdd: Option[Keywords],
                          keywordsToRemove: Option[Keywords],
                          mailboxIds: Option[MailboxIds],
                          mailboxIdsToAdd: Option[MailboxIds],
                          mailboxIdsToRemove: Option[MailboxIds]) {
  def validate: Either[IllegalArgumentException, ValidatedEmailSetUpdate] = {
    if (mailboxIds.isDefined && (mailboxIdsToAdd.isDefined || mailboxIdsToRemove.isDefined)) {
      Left(new IllegalArgumentException("Partial update and reset specified for mailboxIds"))
    } else if (keywords.isDefined && (keywordsToAdd.isDefined || keywordsToRemove.isDefined)) {
      Left(new IllegalArgumentException("Partial update and reset specified for keywords"))
    } else {
      val mailboxIdsIdentity: Function[MailboxIds, MailboxIds] = ids => ids
      val mailboxIdsAddition: Function[MailboxIds, MailboxIds] = mailboxIdsToAdd
        .map(toBeAdded => (ids: MailboxIds) => ids ++ toBeAdded)
        .getOrElse(mailboxIdsIdentity)
      val mailboxIdsRemoval: Function[MailboxIds, MailboxIds] = mailboxIdsToRemove
        .map(toBeRemoved => (ids: MailboxIds) => ids -- toBeRemoved)
        .getOrElse(mailboxIdsIdentity)
      val mailboxIdsReset: Function[MailboxIds, MailboxIds] = mailboxIds
        .map(toReset => (_: MailboxIds) => toReset)
        .getOrElse(mailboxIdsIdentity)
      val mailboxIdsTransformation: Function[MailboxIds, MailboxIds] = mailboxIdsAddition
        .compose(mailboxIdsRemoval)
        .compose(mailboxIdsReset)

      val keywordsIdentity: Function[Keywords, Keywords] = keywords => keywords
      val keywordsAddition: Function[Keywords, Keywords] = keywordsToAdd
        .map(toBeAdded => (keywords: Keywords) => keywords ++ toBeAdded)
        .getOrElse(keywordsIdentity)
      val keywordsRemoval: Function[Keywords, Keywords] = keywordsToRemove
        .map(toBeRemoved => (keywords: Keywords) => keywords -- toBeRemoved)
        .getOrElse(keywordsIdentity)
      val keywordsReset: Function[Keywords, Keywords] = keywords
        .map(toReset => (_: Keywords) => toReset)
        .getOrElse(keywordsIdentity)
      val keywordsTransformation: Function[Keywords, Keywords] = keywordsAddition
        .compose(keywordsRemoval)
        .compose(keywordsReset)

      Right(ValidatedEmailSetUpdate(keywordsTransformation, mailboxIdsTransformation, this))
    }
  }

  def isOnlyMove: Boolean = mailboxIds.isDefined && mailboxIds.get.value.size == 1 &&
    keywords.isEmpty && keywordsToAdd.isEmpty && keywordsToRemove.isEmpty

  def isOnlyFlagAddition: Boolean = keywordsToAdd.isDefined && keywordsToRemove.isEmpty && mailboxIds.isEmpty &&
    mailboxIdsToAdd.isEmpty && mailboxIdsToRemove.isEmpty

  def isOnlyFlagRemoval: Boolean = keywordsToRemove.isDefined && keywordsToAdd.isEmpty && mailboxIds.isEmpty &&
    mailboxIdsToAdd.isEmpty && mailboxIdsToRemove.isEmpty
}

case class ValidatedEmailSetUpdate private(keywordsTransformation: Function[Keywords, Keywords],
                                           mailboxIdsTransformation: Function[MailboxIds, MailboxIds],
                                           update: EmailSetUpdate)

class EmailUpdateValidationException() extends IllegalArgumentException

case class InvalidEmailPropertyException(property: String, cause: String) extends EmailUpdateValidationException

case class InvalidEmailUpdateException(property: String, cause: String) extends EmailUpdateValidationException

case class EmailCreationResponse(id: MessageId, blobId: Option[BlobId], threadId: ThreadId, size: Size)

