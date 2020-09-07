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
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.model.Properties
import org.apache.james.mailbox.model.{MessageId, MessageResult}
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

object Email {
  private val logger: Logger = LoggerFactory.getLogger(classOf[Email])

  type UnparsedEmailIdConstraint = NonEmpty
  type UnparsedEmailId = String Refined UnparsedEmailIdConstraint

  val defaultProperties: Properties = Properties("id", "size")
  val allowedProperties: Properties = Properties("id", "size", "bodyStructure", "textBody", "htmlBody")
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

  def from(message: (MessageId, Seq[MessageResult])): Try[Email] = {

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
    } yield {
      Email(
        id = messageId,
        size = sanitizeSize(firstMessage.getSize),
        bodyStructure = bodyStructure,
        textBody = bodyStructure.textBody,
        htmlBody = bodyStructure.htmlBody)
    }
  }
}

case class Email(id: MessageId,
                 size: Size,
                 bodyStructure: EmailBodyPart,
                 textBody: List[EmailBodyPart],
                 htmlBody: List[EmailBodyPart])
