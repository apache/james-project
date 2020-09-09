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

import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import org.apache.james.jmap.mail.Email.{UnparsedEmailId, sanitizeSize}
import org.apache.james.jmap.mail.EmailGetRequest.MaxBodyValueBytes
import org.apache.james.jmap.model.State.State
import org.apache.james.jmap.model.{AccountId, Properties}
import org.apache.james.mailbox.model.{MessageId, MessageResult}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.{Header, Message}
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig

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

case class EmailGetRequest(accountId: AccountId,
                           ids: Option[EmailIds],
                           fetchAllBodyValues: Option[FetchAllBodyValues],
                           fetchTextBodyValues: Option[FetchTextBodyValues],
                           fetchHTMLBodyValues: Option[FetchHTMLBodyValues],
                           maxBodyValueBytes: Option[MaxBodyValueBytes],
                           properties: Option[Properties],
                           bodyProperties: Option[Properties]) {
  def toEmail(message: (MessageId, Seq[MessageResult])): Try[Email] = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)
    val messageId: MessageId = message._1

    for {
      firstMessage <- message._2
        .headOption
        .map(Success(_))
        .getOrElse(Failure(new IllegalArgumentException("No message supplied")))
      mime4JMessage <- Try(defaultMessageBuilder.parseMessage(firstMessage.getFullContent.getInputStream))
      bodyStructure <- EmailBodyPart.of(messageId, mime4JMessage)
      bodyValues <- extractBodyValues(bodyStructure)
    } yield {
      Email(
        id = messageId,
        size = sanitizeSize(firstMessage.getSize),
        bodyStructure = bodyStructure,
        textBody = bodyStructure.textBody,
        htmlBody = bodyStructure.htmlBody,
        attachments = bodyStructure.attachments,
        headers = asEmailHeaders(mime4JMessage.getHeader),
        bodyValues = bodyValues,
        messageId = extractMessageId(mime4JMessage, "Message-Id"),
        inReplyTo = extractMessageId(mime4JMessage, "In-Reply-To"),
        references = extractMessageId(mime4JMessage, "References"),
        to = Option(mime4JMessage.getTo).map(EmailAddress.from),
        cc = Option(mime4JMessage.getCc).map(EmailAddress.from),
        bcc = Option(mime4JMessage.getBcc).map(EmailAddress.from),
        from = Option(mime4JMessage.getFrom).map(EmailAddress.from),
        sender = Option(mime4JMessage.getSender).map(EmailAddress.from).map(List(_)))
    }
  }

  private def extractMessageId(mime4JMessage: Message, fieldName: String): Option[List[HeaderMessageId]] =
    Option(mime4JMessage.getHeader.getFields(fieldName))
      .map(_.asScala
        .map(_.getBody)
        .map(HeaderMessageId.from)
        .toList)
      .filter(_.nonEmpty)

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
