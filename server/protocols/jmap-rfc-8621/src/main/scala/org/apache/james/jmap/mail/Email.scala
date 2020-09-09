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
 * ***************************************************************/

package org.apache.james.jmap.mail

import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import org.apache.james.jmap.api.model.Preview
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.model.{Properties, UTCDate}
import org.apache.james.mailbox.model.{MailboxId, MessageId}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

object Email {
  private val logger: Logger = LoggerFactory.getLogger(classOf[Email])

  type UnparsedEmailIdConstraint = NonEmpty
  type UnparsedEmailId = String Refined UnparsedEmailIdConstraint

  val defaultProperties: Properties = Properties("id", "size")
  val allowedProperties: Properties = Properties("id", "size", "bodyStructure", "textBody", "htmlBody",
    "attachments", "headers", "bodyValues", "messageId", "inReplyTo", "references", "to", "cc", "bcc",
    "from", "sender", "replyTo", "subject", "sentAt", "mailboxIds", "blobId", "threadId", "receivedAt",
    "preview", "hasAttachment")
  val idProperty: Properties = Properties("id")

  def asUnparsed(messageId: MessageId): Try[UnparsedEmailId] =
    refined.refineV[UnparsedEmailIdConstraint](messageId.serialize()) match {
      case Left(e) => Failure(new IllegalArgumentException(e))
      case scala.Right(value) => Success(value)
    }

  type Size = Long Refined NonNegative
  val Zero: Size = 0L

  def sanitizeSize(value: Long): Size = {
    val size: Either[String, Size] = refineV[NonNegative](value)
    size.fold(e => {
      logger.error(s"Encountered an invalid Email size: $e")
      Zero
    },
      refinedValue => refinedValue)
  }
}

object HeaderMessageId {
  def from(string: String): HeaderMessageId = HeaderMessageId(sanitize(string))

  private def sanitize(string: String): String = string match {
    case s if s.startsWith("<") => sanitize(s.substring(1))
    case s if s.endsWith(">") => sanitize(s.substring(0, s.length - 1))
    case s => s
  }
}

case class HeaderMessageId(value: String) extends AnyVal
case class Subject(value: String) extends AnyVal
case class MailboxIds(value: List[MailboxId])
case class ThreadId(value: String) extends AnyVal
case class HasAttachment(value: Boolean) extends AnyVal

case class EmailMetadata(id: MessageId,
                         blobId: BlobId,
                         threadId: ThreadId,
                         mailboxIds: MailboxIds,
                         size: Size,
                         receivedAt: UTCDate)

case class EmailHeaders(headers: List[EmailHeader],
                        messageId: Option[List[HeaderMessageId]],
                        inReplyTo: Option[List[HeaderMessageId]],
                        references: Option[List[HeaderMessageId]],
                        to: Option[List[EmailAddress]],
                        cc: Option[List[EmailAddress]],
                        bcc: Option[List[EmailAddress]],
                        from: Option[List[EmailAddress]],
                        sender: Option[List[EmailAddress]],
                        replyTo: Option[List[EmailAddress]],
                        subject: Option[Subject],
                        sentAt: Option[UTCDate])

case class EmailBody(bodyStructure: EmailBodyPart,
                     textBody: List[EmailBodyPart],
                     htmlBody: List[EmailBodyPart],
                     attachments: List[EmailBodyPart],
                     bodyValues: Map[PartId, EmailBodyValue],
                     hasAttachment: HasAttachment,
                     preview: Preview)

case class Email(metadata: EmailMetadata,
                 header: EmailHeaders,
                 body: EmailBody)
