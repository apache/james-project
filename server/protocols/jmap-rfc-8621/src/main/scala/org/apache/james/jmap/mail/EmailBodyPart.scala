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

import java.io.OutputStream
import java.time.ZoneId

import cats.implicits._
import com.google.common.io.CountingOutputStream
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import org.apache.commons.io.IOUtils
import org.apache.james.jmap.api.model.Size
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.Properties
import org.apache.james.jmap.mail.EmailBodyPart.{FILENAME_PREFIX, MULTIPART_ALTERNATIVE, TEXT_HTML, TEXT_PLAIN}
import org.apache.james.jmap.mail.PartId.PartIdValue
import org.apache.james.mailbox.model.{Cid, MessageAttachmentMetadata, MessageId, MessageResult}
import org.apache.james.mime4j.codec.{DecodeMonitor, DecoderUtil}
import org.apache.james.mime4j.dom.field.{ContentDispositionField, ContentLanguageField, ContentTypeField, FieldName}
import org.apache.james.mime4j.dom.{Entity, Message, Multipart, SingleBody, TextBody => Mime4JTextBody}
import org.apache.james.mime4j.message.{DefaultMessageBuilder, DefaultMessageWriter}
import org.apache.james.mime4j.stream.{Field, MimeConfig, RawField}
import org.apache.james.util.html.HtmlTextExtractor

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

object PartId {
  type PartIdValue = Int Refined NonNegative

  def parse(string: String): Try[PartId] = Try(string.toInt)
    .flatMap(i => refineV[NonNegative](i) match {
      case Left(e) => Failure(new IllegalArgumentException(e))
      case scala.Right(id) => Success(PartId(id))
    })
}

case class PartId(value: PartIdValue) {
  def serialize: String = value.toString

  def next: PartId = refineV[NonNegative](value + 1) match {
    case scala.Right(incrementedValue) => PartId(incrementedValue)
    case Left(e) => throw new IllegalArgumentException(e)
  }
}

object EmailBodyPart {
  val TEXT_PLAIN: Type = Type("text/plain")
  val TEXT_HTML: Type = Type("text/html")
  val MULTIPART_ALTERNATIVE: Type = Type("multipart/alternative")
  val FILENAME_PREFIX = "name"

  val defaultProperties: Properties = Properties("partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location")
  val allowedProperties: Properties = defaultProperties ++ Properties("subParts", "headers")

  def of(properties: Option[Properties], zoneId: ZoneId, messageId: MessageId, message: MessageResult): Try[EmailBodyPart] = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)

    val mime4JMessage = Try(defaultMessageBuilder.parseMessage(message.getFullContent.getInputStream))
    mime4JMessage.flatMap(of(properties, zoneId, messageId, _))
  }

  def fromAttachment(properties: Option[Properties], zoneId: ZoneId, attachment: MessageAttachmentMetadata, entity: Message): EmailBodyPart = {
    def parseDisposition(attachment: MessageAttachmentMetadata): Option[Disposition] =
      if (attachment.isInline) {
        Option(Disposition.INLINE)
      } else {
        Option(Disposition.ATTACHMENT)
      }

    def parsePartIdFromBlobId(blobId: String): PartId =
      PartId(blobId.substring(blobId.lastIndexOf("_") + 1).asInstanceOf[PartIdValue])

    EmailBodyPart(partId = parsePartIdFromBlobId(attachment.getAttachmentId.getId),
      blobId = BlobId.of(attachment.getAttachmentId.getId).toOption,
      headers = entity.getHeader.getFields.asScala.toList.map(EmailHeader(_)),
      size = Size.sanitizeSize(attachment.getAttachment.getSize),
      name = attachment.getName.map(Name(_)).toScala,
      `type` = Type(attachment.getAttachment.getType.mimeType().asString()),
      charset = attachment.getAttachment.getType.charset().map(charset => Charset(charset.name())).toScala,
      disposition = parseDisposition(attachment),
      cid = attachment.getCid.toScala,
      language = Option.empty,
      location = Option.empty,
      subParts = Option.empty,
      entity = entity,
      specificHeaders = EmailHeaders.extractSpecificHeaders(properties)(zoneId, entity.getHeader))
  }

  def of(properties: Option[Properties], zoneId: ZoneId, messageId: MessageId, message: Message): Try[EmailBodyPart] =
    of(properties, zoneId, messageId, PartId(1), message).map(_._1)

  private def of(properties: Option[Properties], zoneId: ZoneId, messageId: MessageId, partId: PartId, entity: Entity): Try[(EmailBodyPart, PartId)] =
    entity.getBody match {
      case multipart: Multipart =>
        val scanResults: Try[List[(Option[EmailBodyPart], PartId)]] = multipart.getBodyParts
          .asScala.toList
          .scanLeft[Try[(Option[EmailBodyPart], PartId)]](Success((None, partId)))(traverse(properties, zoneId, messageId))
          .sequence
        val highestPartIdValidation: Try[PartId] = scanResults.map(list => list.map(_._2).reverse.headOption.getOrElse(partId))
        val childrenValidation: Try[List[EmailBodyPart]] = scanResults.map(list => list.flatMap(_._1))

        zip(childrenValidation, highestPartIdValidation)
            .flatMap {
              case (children, highestPartId) => of(properties, zoneId, None, partId, entity, Some(children))
                .map(part => (part, highestPartId))
            }
      case _ => BlobId.of(messageId, partId)
          .flatMap(blobId => of(properties, zoneId, Some(blobId), partId, entity, None))
          .map(part => (part, partId))
    }

  private def traverse(properties: Option[Properties], zoneId: ZoneId, messageId: MessageId)(acc: Try[(Option[EmailBodyPart], PartId)], entity: Entity): Try[(Option[EmailBodyPart], PartId)] = {
    acc.flatMap {
      case (_, previousPartId) =>
        val partId = previousPartId.next

        of(properties, zoneId, messageId, partId, entity)
          .map({
            case (part, partId) => (Some(part), partId)
          })
    }
  }

  private def of(properties: Option[Properties],
                 zoneId: ZoneId,
                 blobId: Option[BlobId],
                 partId: PartId,
                 entity: Entity,
                 subParts: Option[List[EmailBodyPart]]): Try[EmailBodyPart] =
    size(entity)
      .map(size => EmailBodyPart(
          partId = partId,
          blobId = blobId,
          headers = entity.getHeader.getFields.asScala.toList.map(EmailHeader(_)),
          size = size,
          name = Name.of(entity),
          `type` = Type(entity.getMimeType),
          charset = Option(entity.getCharset).map(Charset),
          disposition = Option(entity.getDispositionType).map(Disposition(_)),
          cid = ClientCid.of(entity),
          language = Languages.of(entity),
          location = headerValue(entity, "Content-Location")
            .map(Location),
          subParts = subParts,
          entity = entity,
          specificHeaders = EmailHeaders.extractSpecificHeaders(properties)(zoneId, entity.getHeader)))

  private def headerValue(entity: Entity, headerName: String): Option[String] = entity.getHeader
    .getFields(headerName)
    .asScala
    .headOption
    .map(_.getBody)

  private def size(entity: Entity): Try[Size] =
    entity.getBody match {
      case body: SingleBody => refineSize(body.size())
      case body =>
        val countingOutputStream: CountingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream())
        val writer = new DefaultMessageWriter
        writer.writeBody(body, countingOutputStream)
        refineSize(countingOutputStream.getCount)
    }

  private def refineSize(l: Long): Try[Size] = refineV[NonNegative](l) match {
    case scala.Right(size) => Success(size)
    case Left(e) => Failure(new IllegalArgumentException(e))
  }

  private def zip[A, B](a: Try[A], b: Try[B]): Try[(A, B)] = for {
    aValue <- a
    bValue <- b
  } yield (aValue, bValue)
}

object Name {
  def of(entity: Entity): Option[Name] = Option(entity.getHeader.getField(FieldName.CONTENT_TYPE))
    .flatMap {
      case contentTypeField: ContentTypeField => Option(contentTypeField.getParameter(FILENAME_PREFIX))
          .map(DecoderUtil.decodeEncodedWords(_, DecodeMonitor.SILENT))
      case _ => None
    }.map(Name(_))
    .orElse(Option(entity.getHeader.getField(FieldName.CONTENT_DISPOSITION))
      .flatMap {
        case contentDispositionField: ContentDispositionField => Option(contentDispositionField.getFilename)
          .map(DecoderUtil.decodeEncodedWords(_, DecodeMonitor.SILENT))
        case _ => None
      }.map(Name(_)))
}

case class Name(value: String) extends AnyVal
case class Type(value: String) extends AnyVal
case class Charset(value: String) extends AnyVal

object Disposition {
  val ATTACHMENT = Disposition("attachment")
  val INLINE = Disposition("inline")
}

case class Disposition(value: String) extends AnyVal

object Languages {
  def of(entity: Entity): Option[Languages] =
    Option(entity.getHeader.getField(FieldName.CONTENT_LANGUAGE))
      .flatMap {
        case contentLanguageField: ContentLanguageField => Some(Languages(contentLanguageField.getLanguages.asScala.toList.map(Language)))
        case _ => None
      }
}

case class Languages(value: List[Language]) {
  def asField: Field = new RawField("Content-Language", value.map(_.value).mkString(", "))
}

case class Language(value: String) extends AnyVal

case class Location(value: String) extends AnyVal {
  def asField: Field = new RawField("Content-Location", value)
}

object Context {
  def of(`type`: Type): Context = `type` match {
    case MULTIPART_ALTERNATIVE => AlternativeContext
    case _ => NoContext
  }
  def of(`type`: Type, previousContext: Context): Context = (`type`, previousContext) match {
    case (_, AlternativeContext) => AlternativeContext
    case (MULTIPART_ALTERNATIVE, _) => AlternativeContext
    case _ => NoContext
  }
}

sealed trait Context
case object NoContext extends Context
case object AlternativeContext extends Context

case class EmailBodyPart(partId: PartId,
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
                         subParts: Option[List[EmailBodyPart]],
                         entity: Entity,
                         specificHeaders: Map[String, Option[EmailHeaderValue]]) {

  def bodyContent: Try[Option[EmailBodyValue]] = entity.getBody match {
    case textBody: Mime4JTextBody =>
      for {
        value <- Try(IOUtils.toString(textBody.getInputStream, charset(Option(textBody.getMimeCharset))))
      } yield {
        Some(EmailBodyValue(value = value,
          isEncodingProblem = IsEncodingProblem(false),
          isTruncated = IsTruncated(false)))
      }
    case _ => Success(None)
  }

  def textBodyContent(htmlTextExtractor: HtmlTextExtractor): Try[Option[EmailBodyValue]] = `type` match {
    case TEXT_HTML => bodyContent.map(maybeContent => maybeContent.map(
      content => EmailBodyValue(htmlTextExtractor.toPlainText(content.value),
        content.isEncodingProblem,
        content.isTruncated)))
    case _ => bodyContent
  }

  private def charset(charset: Option[String]): java.nio.charset.Charset = charset
    .map(java.nio.charset.Charset.forName)
    .getOrElse(org.apache.james.mime4j.Charsets.DEFAULT_CHARSET)

  def textBody: List[EmailBodyPart] = selfBody ++ textBodyOfMultipart

  def htmlBody: List[EmailBodyPart] = selfBody ++ htmlBodyOfMultipart

  def attachments: List[EmailBodyPart] = selfAttachment ++ attachmentsOfMultipart

  def flatten: List[EmailBodyPart] = subParts.getOrElse(Nil).flatMap(part => part.flatten) ++ List(this)

  private def selfBody: List[EmailBodyPart] = if (shouldBeDisplayedAsBody) {
    List(this)
  } else {
    Nil
  }

  private def selfAttachment: List[EmailBodyPart] = if (shouldBeDisplayedAsAttachment) {
    List(this)
  } else {
    Nil
  }

  private val hasTextMediaType: Boolean = `type`.equals(TEXT_PLAIN) || `type`.equals(TEXT_HTML)
  private val shouldBeDisplayedAsBody: Boolean = hasTextMediaType && !disposition.contains(Disposition.ATTACHMENT )
  private val shouldBeDisplayedAsBodyStrict: Boolean = hasTextMediaType && disposition.isEmpty && cid.isEmpty
  private val shouldBeDisplayedAsAttachment: Boolean = !shouldBeDisplayedAsBodyStrict && subParts.isEmpty

  private def textBodyOfMultipart: List[EmailBodyPart] = `type` match {
    case MULTIPART_ALTERNATIVE => getBodyParts(subParts.getOrElse(Nil), TEXT_PLAIN)
    case _ => subParts.getOrElse(Nil)
      .flatMap(subPart => subPart.textBody)
  }

  private def htmlBodyOfMultipart: List[EmailBodyPart] = `type` match {
    case MULTIPART_ALTERNATIVE => Some(getBodyParts(subParts.getOrElse(Nil), TEXT_HTML)
      .flatMap(subPart => subPart.htmlBody))
      .filter(_.nonEmpty)
      .getOrElse(textBodyOfMultipart)
    case _ => subParts.getOrElse(Nil)
      .flatMap(subPart => subPart.htmlBody)
  }

  private def attachmentsOfMultipart: List[EmailBodyPart] = subParts.getOrElse(Nil)
    .flatMap(_.attachments)

  private def getBodyParts(bodyParts: List[EmailBodyPart], `type`: Type): List[EmailBodyPart] =
    if (bodyParts.isEmpty) {
      Nil
    } else {
      bodyParts.filter(subPart => subPart.`type`.equals(`type`)) ++
        getBodyParts(
          bodyParts
            .filter(subPart => !subPart.`type`.equals(`type`))
            .flatMap(subPart => subPart.subParts.getOrElse(Nil)), `type`)
    }
}
