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

import java.io.OutputStream

import cats.implicits._
import com.google.common.io.CountingOutputStream
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.mail.EmailBodyPart.{MULTIPART_ALTERNATIVE, TEXT_HTML, TEXT_PLAIN}
import org.apache.james.jmap.mail.PartId.PartIdValue
import org.apache.james.mailbox.model.{Cid, MessageId}
import org.apache.james.jmap.model.Properties
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mime4j.dom.{Entity, Message, Multipart}
import org.apache.james.mime4j.message.DefaultMessageWriter

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

object PartId {
  type PartIdValue = Int Refined NonNegative
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

  val defaultProperties: Properties = Properties("partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location")
  val allowedProperties: Properties = defaultProperties ++ Properties("subParts", "headers")

  def of(messageId: MessageId, message: Message): Try[EmailBodyPart] =
    of(messageId, PartId(1), message).map(_._1)

  private def of(messageId: MessageId, partId: PartId, entity: Entity): Try[(EmailBodyPart, PartId)] =
    entity.getBody match {
      case multipart: Multipart =>
        val scanResults: Try[List[(Option[EmailBodyPart], PartId)]] = multipart.getBodyParts
          .asScala.toList
          .scanLeft[Try[(Option[EmailBodyPart], PartId)]](Success((None, partId)))(traverse(messageId))
          .sequence
        val highestPartIdValidation: Try[PartId] = scanResults.map(list => list.map(_._2).reverse.headOption.getOrElse(partId))
        val childrenValidation: Try[List[EmailBodyPart]] = scanResults.map(list => list.flatMap(_._1))

        zip(childrenValidation, highestPartIdValidation)
            .flatMap {
              case (children, highestPartId) => of(None, partId, entity, Some(children))
                .map(part => (part, highestPartId))
            }
      case _ => BlobId.of(messageId, partId)
          .flatMap(blobId => of(Some(blobId), partId, entity, None))
          .map(part => (part, partId))
    }

  private def traverse(messageId: MessageId)(acc: Try[(Option[EmailBodyPart], PartId)], entity: Entity): Try[(Option[EmailBodyPart], PartId)] = {
    acc.flatMap {
      case (_, previousPartId) =>
        val partId = previousPartId.next

        of(messageId, partId, entity)
          .map({
            case (part, partId) => (Some(part), partId)
          })
    }
  }

  private def of(blobId: Option[BlobId],
                 partId: PartId,
                 entity: Entity,
                 subParts: Option[List[EmailBodyPart]]): Try[EmailBodyPart] =
    size(entity)
      .map(size => EmailBodyPart(
          partId = partId,
          blobId = blobId,
          headers = entity.getHeader.getFields.asScala.toList.map(EmailHeader(_)),
          size = size,
          name = Option(entity.getFilename).map(Name),
          `type` = Type(entity.getMimeType),
          charset = Option(entity.getCharset).map(Charset),
          disposition = Option(entity.getDispositionType).map(Disposition),
          cid = headerValue(entity, "Content-Id")
            .flatMap(Cid.parser()
              .relaxed()
              .unwrap()
              .parse(_)
              .toScala),
          language = headerValue(entity, "Content-Language")
            .map(Language),
          location = headerValue(entity, "Content-Location")
            .map(Location),
          subParts = subParts))

  private def headerValue(entity: Entity, headerName: String): Option[String] = entity.getHeader
    .getFields(headerName)
    .asScala
    .headOption
    .map(_.getBody)

  private def size(entity: Entity): Try[Size] = {
    val countingOutputStream: CountingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream())
    val writer = new DefaultMessageWriter
    writer.writeEntity(entity, countingOutputStream)
    refineV[NonNegative](countingOutputStream.getCount) match {
      case scala.Right(size) => Success(size)
      case Left(e) => Failure(new IllegalArgumentException(e))
    }
  }

  private def zip[A, B](a: Try[A], b: Try[B]): Try[(A, B)] = for {
    aValue <- a
    bValue <- b
  } yield (aValue, bValue)
}

case class Name(value: String)
case class Type(value: String)
case class Charset(value: String)
case class Disposition(value: String)
case class Language(value: String)
case class Location(value: String)

case class EmailBodyPart(partId: PartId,
                         blobId: Option[BlobId],
                         headers: List[EmailHeader],
                         size: Size,
                         name: Option[Name],
                         `type`: Type,
                         charset: Option[Charset],
                         disposition: Option[Disposition],
                         cid: Option[Cid],
                         language: Option[Language],
                         location: Option[Location],
                         subParts: Option[List[EmailBodyPart]]) {

  def textBody: List[EmailBodyPart] = selfBody ++ textBodyOfMultipart

  def htmlBody: List[EmailBodyPart] = selfBody ++ htmlBodyOfMultipart

  def attachments: List[EmailBodyPart] = selfAttachment ++ attachmentsOfMultipart

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
  private val shouldBeDisplayedAsBody: Boolean = hasTextMediaType && disposition.isEmpty && cid.isEmpty
  private val shouldBeDisplayedAsAttachment: Boolean = !shouldBeDisplayedAsBody && subParts.isEmpty

  private def textBodyOfMultipart: List[EmailBodyPart] = `type` match {
    case MULTIPART_ALTERNATIVE => textPlainSubparts
    case _ => subParts.getOrElse(Nil)
      .flatMap(subPart => subPart.textBody)
  }

  private def htmlBodyOfMultipart: List[EmailBodyPart] = `type` match {
    case MULTIPART_ALTERNATIVE => textHtmlSubparts
    case _ => subParts.getOrElse(Nil)
      .flatMap(subPart => subPart.htmlBody)
  }

  private def attachmentsOfMultipart: List[EmailBodyPart] = subParts.getOrElse(Nil)
    .flatMap(_.attachments)

  private def textPlainSubparts: List[EmailBodyPart] = subParts.getOrElse(Nil)
    .filter(subPart => subPart.`type`.equals(TEXT_PLAIN))

  private def textHtmlSubparts: List[EmailBodyPart] = subParts.getOrElse(Nil)
    .filter(subPart => subPart.`type`.equals(TEXT_HTML))
}
