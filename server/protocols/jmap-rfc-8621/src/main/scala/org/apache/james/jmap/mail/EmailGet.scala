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

package org.apache.james.jmap.mail

import java.nio.charset.StandardCharsets.US_ASCII
import java.time.ZoneId
import java.util.Date

import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.mail.Email.{UnparsedEmailId, sanitizeSize}
import org.apache.james.jmap.mail.EmailGetRequest.MaxBodyValueBytes
import org.apache.james.jmap.mail.EmailHeaders.SPECIFIC_HEADER_PREFIX
import org.apache.james.jmap.model.KeywordsFactory.LENIENT_KEYWORDS_FACTORY
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, Properties, UTCDate}
import org.apache.james.mailbox.model.{MessageId, MessageResult}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.field.{AddressListField, DateTimeField, MailboxField, MailboxListField}
import org.apache.james.mime4j.dom.{Header, Message}
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.{Field, MimeConfig}
import org.apache.james.mime4j.util.MimeUtil

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

case class EmailIds(value: List[UnparsedEmailId])

case class FetchAllBodyValues(value: Boolean) extends AnyVal
case class FetchTextBodyValues(value: Boolean) extends AnyVal
case class FetchHTMLBodyValues(value: Boolean) extends AnyVal

object EmailGetRequest {
  type MaxBodyValueBytes = Int Refined NonNegative

  val ZERO: MaxBodyValueBytes = 0
}

object SpecificHeaderRequest {
  def from(property: NonEmptyString): Either[NonEmptyString, SpecificHeaderRequest] = property match {
    case property if property.value.equals(SPECIFIC_HEADER_PREFIX) => Left(property)
    case property if property.startsWith(SPECIFIC_HEADER_PREFIX)  =>
      val headerName = property.substring(SPECIFIC_HEADER_PREFIX.length)
      if (headerName.contains(":")) {
        Left(property)
      } else {
        scala.Right(SpecificHeaderRequest(property, headerName))
      }
    case _ => Left(property)
  }
}

case class EmailGetRequest(accountId: AccountId,
                           ids: Option[EmailIds],
                           fetchAllBodyValues: Option[FetchAllBodyValues],
                           fetchTextBodyValues: Option[FetchTextBodyValues],
                           fetchHTMLBodyValues: Option[FetchHTMLBodyValues],
                           maxBodyValueBytes: Option[MaxBodyValueBytes],
                           properties: Option[Properties],
                           bodyProperties: Option[Properties]) {
  def toEmail(previewFactory: Preview.Factory, zoneId: ZoneId)(message: (MessageId, Seq[MessageResult])): Try[Email] = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)
    val messageId: MessageId = message._1
    val mailboxIds: MailboxIds = MailboxIds(message._2
      .map(_.getMailboxId)
      .toList)

    for {
      firstMessage <- message._2
        .headOption
        .map(Success(_))
        .getOrElse(Failure(new IllegalArgumentException("No message supplied")))
      mime4JMessage <- Try(defaultMessageBuilder.parseMessage(firstMessage.getFullContent.getInputStream))
      bodyStructure <- EmailBodyPart.of(messageId, mime4JMessage)
      bodyValues <- extractBodyValues(bodyStructure)
      keywords <- LENIENT_KEYWORDS_FACTORY.fromFlags(firstMessage.getFlags)
      blobId <- BlobId.of(messageId)
      preview <- Try(previewFactory.fromMessageResult(firstMessage))
    } yield {
      Email(metadata = EmailMetadata(
          id = messageId,
          blobId = blobId,
          threadId = ThreadId(messageId.serialize),
          mailboxIds = mailboxIds,
          receivedAt = UTCDate.from(firstMessage.getInternalDate, zoneId),
          size = sanitizeSize(firstMessage.getSize)),
        header = EmailHeaders(
          headers = asEmailHeaders(mime4JMessage.getHeader),
          messageId = extractMessageId(mime4JMessage, "Message-Id"),
          inReplyTo = extractMessageId(mime4JMessage, "In-Reply-To"),
          references = extractMessageId(mime4JMessage, "References"),
          to = extractAddresses(mime4JMessage, "To"),
          cc = extractAddresses(mime4JMessage, "Cc"),
          bcc = extractAddresses(mime4JMessage, "Bcc"),
          from = extractAddresses(mime4JMessage, "From"),
          replyTo = extractAddresses(mime4JMessage, "Reply-To"),
          sender = extractAddresses(mime4JMessage, "Sender"),
          subject = extractSubject(mime4JMessage),
          sentAt = extractDate(mime4JMessage, "Date").map(date => UTCDate.from(date, zoneId))),
        body = EmailBody(
          bodyStructure = bodyStructure,
          textBody = bodyStructure.textBody,
          htmlBody = bodyStructure.htmlBody,
          attachments = bodyStructure.attachments,
          keywords = keywords,
          bodyValues = bodyValues,
          hasAttachment = HasAttachment(!firstMessage.getLoadedAttachments.isEmpty),
          preview = preview),
        specificHeaders = properties.getOrElse(Properties.empty()).value
          .flatMap(property => SpecificHeaderRequest.from(property).toOption)
          .map(_.retrieveHeader(mime4JMessage))
          .toMap)
    }
  }

  private def extractSubject(mime4JMessage: Message) =
    extractLastField(mime4JMessage, "Subject")
      .map(_.getBody)
      .map(MimeUtil.unscrambleHeaderValue)
      .map(Subject)

  private def extractMessageId(mime4JMessage: Message, fieldName: String): Option[List[HeaderMessageId]] =
    Option(mime4JMessage.getHeader.getFields(fieldName))
      .map(_.asScala
        .map(_.getBody)
        .map(HeaderMessageId.from)
        .toList)
      .filter(_.nonEmpty)

  private def extractAddresses(mime4JMessage: Message, fieldName: String): Option[List[EmailAddress]] =
    extractLastField(mime4JMessage, fieldName)
      .flatMap {
        case f: AddressListField => Some(EmailAddress.from(f.getAddressList))
        case f: MailboxListField => Some(EmailAddress.from(f.getMailboxList))
        case f: MailboxField => Some(List(EmailAddress.from(f.getMailbox)))
        case _ => None
      }
      .filter(_.nonEmpty)

  private def extractDate(mime4JMessage: Message, fieldName: String): Option[Date] =
    extractLastField(mime4JMessage, fieldName)
      .flatMap {
        case f: DateTimeField => Some(f.getDate)
        case _ => None
      }

  private def extractLastField(mime4JMessage: Message, fieldName: String): Option[Field] =
    Option(mime4JMessage.getHeader.getFields(fieldName))
      .map(_.asScala)
      .flatMap(fields => fields.reverse.headOption)

  def extractBodyValues(bodyStructure: EmailBodyPart): Try[Map[PartId, EmailBodyValue]] = for {
    textBodyValues <- extractBodyValues(bodyStructure.textBody, fetchTextBodyValues.exists(_.value))
    htmlBodyValues <- extractBodyValues(bodyStructure.htmlBody, fetchHTMLBodyValues.exists(_.value))
    allBodyValues <- extractBodyValues(bodyStructure.flatten, fetchAllBodyValues.exists(_.value))
  } yield {
    (textBodyValues ++ htmlBodyValues ++ allBodyValues)
      .distinctBy(_._1)
      .toMap
  }

  private def extractBodyValues(parts: List[EmailBodyPart], shouldFetch: Boolean): Try[List[(PartId, EmailBodyValue)]] =
    if (shouldFetch) {
      parts
        .map(part => part.bodyContent.map(bodyValue => bodyValue.map(b => (part.partId, b.truncate(maxBodyValueBytes)))))
        .sequence
        .map(list => list.flatten)
    } else {
      Success(Nil)
    }

  private def asEmailHeaders(header: Header): List[EmailHeader] =
    header.iterator()
      .asScala
      .map(header => EmailHeader(
        EmailHeaderName(header.getName),
        EmailHeaderValue(new String(header.getRaw.toByteArray, US_ASCII)
          .substring(header.getName.length + 1))))
      .toList
}

case class EmailNotFound(value: Set[UnparsedEmailId]) {
  def merge(other: EmailNotFound): EmailNotFound = EmailNotFound(this.value ++ other.value)
}

case class EmailGetResponse(accountId: AccountId,
                            state: State,
                            list: List[Email],
                            notFound: EmailNotFound)

case class SpecificHeaderRequest(headerName: NonEmptyString, property: String) {
  def retrieveHeader(message: Message): (String, Option[EmailHeaderValue]) = (headerName,
    Option(message.getHeader.getField(property))
    .map(field => EmailHeaderValue.from(field)))
}
