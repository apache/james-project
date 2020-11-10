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

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Date

import cats.implicits._
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.State.State
import org.apache.james.jmap.core.{AccountId, SetError, UTCDate}
import org.apache.james.jmap.mail.Disposition.INLINE
import org.apache.james.jmap.mail.EmailSet.{EmailCreationId, UnparsedMessageId}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.exception.AttachmentNotFoundException
import org.apache.james.mailbox.model.{AttachmentId, AttachmentMetadata, Cid, MessageId}
import org.apache.james.mailbox.{AttachmentContentLoader, AttachmentManager, MailboxSession}
import org.apache.james.mime4j.codec.EncoderUtil
import org.apache.james.mime4j.codec.EncoderUtil.Usage
import org.apache.james.mime4j.dom.field.{ContentIdField, ContentTypeField, FieldName}
import org.apache.james.mime4j.dom.{Entity, Message}
import org.apache.james.mime4j.field.Fields
import org.apache.james.mime4j.message.{BodyPartBuilder, MultipartBuilder}
import org.apache.james.mime4j.stream.{Field, RawField}
import play.api.libs.json.JsObject

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Right, Try}

object EmailSet {
  type EmailCreationId = Id
  type UnparsedMessageIdConstraint = NonEmpty
  type UnparsedMessageId = String Refined UnparsedMessageIdConstraint

  def asUnparsed(messageId: MessageId): UnparsedMessageId = refined.refineV[UnparsedMessageIdConstraint](messageId.serialize()) match {
    case Left(e) => throw new IllegalArgumentException(e)
    case scala.Right(value) => value
  }

  def parse(messageIdFactory: MessageId.Factory)(unparsed: UnparsedMessageId): Try[MessageId] =
    Try(messageIdFactory.fromString(unparsed.value))
}

object SubType {
  val HTML_SUBTYPE = "html"
  val MIXED_SUBTYPE = "mixed"
  val RELATED_SUBTYPE = "related"
}

case class ClientPartId(id: Id)

case class ClientHtmlBody(partId: ClientPartId, `type`: Type)

case class ClientEmailBodyValue(value: String,
                                isEncodingProblem: Option[IsEncodingProblem],
                                isTruncated: Option[IsTruncated])

object ClientCid {
  def of(entity: Entity): Option[Cid] =
    Option(entity.getHeader.getField(FieldName.CONTENT_ID))
      .flatMap {
        case contentIdField: ContentIdField => Cid.parser().relaxed().unwrap().parse(contentIdField.getId).toScala
        case _ => None
      }
}

case class ClientCid(value: String) {
  def asField: Field = new RawField("Content-ID", value)
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

case class EmailCreationRequest(mailboxIds: MailboxIds,
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
                                htmlBody: Option[List[ClientHtmlBody]],
                                bodyValues: Option[Map[ClientPartId, ClientEmailBodyValue]],
                                specificHeaders: List[EmailHeader],
                                attachments: Option[List[Attachment]]) {
  def toMime4JMessage(attachmentManager: AttachmentManager, attachmentContentLoader: AttachmentContentLoader, mailboxSession: MailboxSession): Either[Exception, Message] =
    validateHtmlBody
      .flatMap(maybeHtmlBody => {
        val builder = Message.Builder.of
        references.flatMap(_.asString).map(new RawField("References", _)).foreach(builder.setField)
        inReplyTo.flatMap(_.asString).map(new RawField("In-Reply-To", _)).foreach(builder.setField)
        messageId.flatMap(_.asString).map(new RawField(FieldName.MESSAGE_ID, _)).foreach(builder.setField)
        subject.foreach(value => builder.setSubject(value.value))
        from.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setFrom)
        to.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setTo)
        cc.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setCc)
        bcc.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setBcc)
        sender.flatMap(_.asMime4JMailboxList).map(_.asJava).map(Fields.addressList(FieldName.SENDER, _)).foreach(builder.setField)
        replyTo.flatMap(_.asMime4JMailboxList).map(_.asJava).foreach(builder.setReplyTo)
        sentAt.map(_.asUTC).map(_.toInstant).map(Date.from).foreach(builder.setDate)
        validateSpecificHeaders(builder)
          .flatMap(_ => {
            specificHeaders.map(_.asField).foreach(builder.addField)
            attachments.filter(_.nonEmpty).map(attachments =>
              createMultipartWithAttachments(maybeHtmlBody, attachments, attachmentManager, attachmentContentLoader, mailboxSession)
                .map(multipartBuilder => {
                  builder.setBody(multipartBuilder)
                  builder.build
                }))
              .getOrElse(Right(builder.setBody(maybeHtmlBody.getOrElse(""), SubType.HTML_SUBTYPE, StandardCharsets.UTF_8).build))
          })
      })

  private def createMultipartWithAttachments(maybeHtmlBody: Option[String],
                                             attachments: List[Attachment],
                                             attachmentManager: AttachmentManager,
                                             attachmentContentLoader: AttachmentContentLoader,
                                             mailboxSession: MailboxSession): Either[Exception, MultipartBuilder] = {
    val maybeAttachments: Either[Exception, List[(Attachment, AttachmentMetadata, Array[Byte])]] =
      attachments
        .map(attachment => getAttachmentMetadata(attachment, attachmentManager, mailboxSession))
        .map(attachmentMetadataList => attachmentMetadataList
          .flatMap(attachmentAndMetadata => loadAttachment(attachmentAndMetadata._1, attachmentAndMetadata._2, attachmentContentLoader, mailboxSession)))
        .sequence

    maybeAttachments.map(list => {

      (list.filter(_._1.isInline), list.filter(!_._1.isInline)) match {
        case (Nil, normalAttachments) => createMixedBody(maybeHtmlBody, normalAttachments)
        case (inlineAttachments, Nil) => createRelatedBody(maybeHtmlBody, inlineAttachments)
        case (inlineAttachments, normalAttachments) => createMixedRelatedBody(maybeHtmlBody, inlineAttachments, normalAttachments)
      }
    })
  }

  private def createMixedRelatedBody(maybeHtmlBody: Option[String], inlineAttachments: List[(Attachment, AttachmentMetadata, Array[Byte])], normalAttachments: List[(Attachment, AttachmentMetadata, Array[Byte])]) = {
    val mixedMultipartBuilder = MultipartBuilder.create(SubType.MIXED_SUBTYPE)
    val relatedMultipartBuilder = MultipartBuilder.create(SubType.RELATED_SUBTYPE)
    relatedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(maybeHtmlBody.getOrElse(""), SubType.HTML_SUBTYPE, StandardCharsets.UTF_8).build)
    inlineAttachments.foldLeft(relatedMultipartBuilder) {
      case (acc, (attachment, storedMetadata, content)) =>
        acc.addBodyPart(toBodypartBuilder(attachment, storedMetadata, content))
        acc
    }

    mixedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(relatedMultipartBuilder.build))

    normalAttachments.foldLeft(mixedMultipartBuilder) {
      case (acc, (attachment, storedMetadata, content)) =>
        acc.addBodyPart(toBodypartBuilder(attachment, storedMetadata, content))
        acc
    }
  }

  private def createMixedBody(maybeHtmlBody: Option[String], normalAttachments: List[(Attachment, AttachmentMetadata, Array[Byte])]) = {
    val mixedMultipartBuilder = MultipartBuilder.create(SubType.MIXED_SUBTYPE)
    mixedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(maybeHtmlBody.getOrElse(""), SubType.HTML_SUBTYPE, StandardCharsets.UTF_8).build)
    normalAttachments.foldLeft(mixedMultipartBuilder) {
      case (acc, (attachment, storedMetadata, content)) =>
        acc.addBodyPart(toBodypartBuilder(attachment, storedMetadata, content))
        acc
    }
  }

  private def createRelatedBody(maybeHtmlBody: Option[String], inlineAttachments: List[(Attachment, AttachmentMetadata, Array[Byte])]) = {
    val relatedMultipartBuilder = MultipartBuilder.create(SubType.RELATED_SUBTYPE)
    relatedMultipartBuilder.addBodyPart(BodyPartBuilder.create().setBody(maybeHtmlBody.getOrElse(""), SubType.HTML_SUBTYPE, StandardCharsets.UTF_8).build)
    inlineAttachments.foldLeft(relatedMultipartBuilder) {
      case (acc, (attachment, storedMetadata, content)) =>
        acc.addBodyPart(toBodypartBuilder(attachment, storedMetadata, content))
        acc
    }
    relatedMultipartBuilder
  }

  private def toBodypartBuilder(attachment: Attachment, storedMetadata: AttachmentMetadata, content: Array[Byte]) = {
    val bodypartBuilder = BodyPartBuilder.create()
    bodypartBuilder.setBody(content, attachment.`type`.value)
      .setField(contentTypeField(attachment, storedMetadata))
      .setContentDisposition(attachment.disposition.getOrElse(Disposition.ATTACHMENT).value)
    attachment.cid.map(_.asField).foreach(bodypartBuilder.addField)
    attachment.location.map(_.asField).foreach(bodypartBuilder.addField)
    attachment.language.map(_.asField).foreach(bodypartBuilder.addField)
    bodypartBuilder
  }

  private def contentTypeField(attachment: Attachment, attachmentMetadata: AttachmentMetadata): ContentTypeField = {
    val typeAsField: ContentTypeField = attachmentMetadata.getType.asMime4J
    if (attachment.name.isDefined) {
      Fields.contentType(typeAsField.getMimeType,
        Map.newBuilder[String, String]
          .addAll(parametersWithoutName(typeAsField))
          .addOne("name", EncoderUtil.encodeEncodedWord(attachment.name.get.value, Usage.TEXT_TOKEN))
          .result
          .asJava)
    } else {
      typeAsField
    }
  }

  private def parametersWithoutName(typeAsField: ContentTypeField): Map[String, String] =
    typeAsField.getParameters
      .asScala
      .filter(!_._1.equals("name"))
      .toMap

  private def getAttachmentMetadata(attachment: Attachment,
                                    attachmentManager: AttachmentManager,
                                    mailboxSession: MailboxSession): Either[AttachmentNotFoundException, (Attachment, AttachmentMetadata)] =
    Try(attachmentManager.getAttachment(AttachmentId.from(attachment.blobId.value.toString), mailboxSession))
      .fold(e => Left(new AttachmentNotFoundException(attachment.blobId.value.value, s"Attachment not found: ${attachment.blobId.value}", e)),
        attachmentMetadata => Right((attachment, attachmentMetadata)))

  private def loadAttachment(attachment: Attachment,
                             attachmentMetadata: AttachmentMetadata,
                             attachmentContentLoader: AttachmentContentLoader,
                             mailboxSession: MailboxSession): Either[Exception, (Attachment, AttachmentMetadata, Array[Byte])] =
    Try(attachmentContentLoader.load(attachmentMetadata, mailboxSession))
      .toEither
      .fold(e => e match {
        case e: AttachmentNotFoundException => Left(new AttachmentNotFoundException(attachment.blobId.value.value, s"Attachment not found: ${attachment.blobId.value}", e))
        case e: IOException => Left(e)
      },
        inputStream => scala.Right((attachment, attachmentMetadata, inputStream.readAllBytes())))

  def validateHtmlBody: Either[IllegalArgumentException, Option[String]] = htmlBody match {
    case None => Right(None)
    case Some(html :: Nil) if !html.`type`.value.equals("text/html") => Left(new IllegalArgumentException("Expecting htmlBody type to be text/html"))
    case Some(html :: Nil) => bodyValues.getOrElse(Map())
      .get(html.partId)
      .map {
        case part if part.isTruncated.isDefined && part.isTruncated.get.value.equals(true) => Left(new IllegalArgumentException("Expecting isTruncated to be false"))
        case part if part.isEncodingProblem.isDefined && part.isEncodingProblem.get.value.equals(true) => Left(new IllegalArgumentException("Expecting isEncodingProblem to be false"))
        case part => Right(Some(part.value))
      }
      .getOrElse(Left(new IllegalArgumentException("Expecting bodyValues to contain the part specified in htmlBody")))
    case _ => Left(new IllegalArgumentException("Expecting htmlBody to contains only 1 part"))
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

case class DestroyIds(value: Seq[UnparsedMessageId])

case class EmailSetRequest(accountId: AccountId,
                           create: Option[Map[EmailCreationId, JsObject]],
                           update: Option[Map[UnparsedMessageId, JsObject]],
                           destroy: Option[DestroyIds]) extends WithAccountId

case class EmailSetResponse(accountId: AccountId,
                            newState: State,
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

case class EmailCreationResponse(id: MessageId)

