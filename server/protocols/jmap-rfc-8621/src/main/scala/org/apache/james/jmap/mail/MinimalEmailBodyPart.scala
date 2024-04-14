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

import java.time.ZoneId

import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.Properties
import org.apache.james.jmap.mail.MinimalEmailBodyPart.of
import org.apache.james.jmap.mime4j.{JamesBodyDescriptorBuilder, SizeUtils}
import org.apache.james.mailbox.model.MessageResult
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.dom.{Entity, Message, Multipart}
import org.apache.james.mime4j.field.LenientFieldParser
import org.apache.james.mime4j.message.{BasicBodyFactory, DefaultMessageBuilder}
import org.apache.james.mime4j.stream.MimeConfig

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object MinimalEmailBodyPart {
  val TEXT_PLAIN: Type = Type("text/plain")
  val TEXT_HTML: Type = Type("text/html")
  val MDN_TYPE: Type = Type("message/disposition-notification")
  val MULTIPART_ALTERNATIVE: Type = Type("multipart/alternative")
  val FILENAME_PREFIX = "name"

  val defaultProperties: Properties = Properties("partId", "blobId", "size", "name", "type", "charset", "disposition", "cid", "language", "location")
  val allowedProperties: Properties = defaultProperties ++ Properties("subParts", "headers")

  def ofMessage(properties: Option[Properties], zoneId: ZoneId, blobId: BlobId, message: MessageResult): Try[MinimalEmailBodyPart] = {
    val defaultMessageBuilder = new DefaultMessageBuilder
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT)
    defaultMessageBuilder.setBodyDescriptorBuilder(new JamesBodyDescriptorBuilder(null, LenientFieldParser.getParser, DecodeMonitor.SILENT))
    defaultMessageBuilder.setBodyFactory(new BasicBodyFactory(Email.defaultCharset))

    val mime4JMessage = Try(defaultMessageBuilder.parseMessage(message.getFullContent.getInputStream))
    mime4JMessage.flatMap(of(properties, zoneId, blobId, _))
  }

  def of(properties: Option[Properties], zoneId: ZoneId, blobId: BlobId, message: Message): Try[MinimalEmailBodyPart] =
    of(properties, zoneId, blobId, PartId(1), message).map(_._1)

  private def of(properties: Option[Properties], zoneId: ZoneId, blobId: BlobId, partId: PartId, entity: Entity): Try[(MinimalEmailBodyPart, PartId)] =
    entity.getBody match {
      case multipart: Multipart =>
        val scanResults: Try[List[(Option[MinimalEmailBodyPart], PartId)]] = multipart.getBodyParts
          .asScala.toList
          .scanLeft[Try[(Option[MinimalEmailBodyPart], PartId)]](Success((None, partId)))(traverse(properties, zoneId, blobId))
          .sequence
        val highestPartIdValidation: Try[PartId] = scanResults.map(list => list.map(_._2).reverse.headOption.getOrElse(partId))
        val childrenValidation: Try[List[MinimalEmailBodyPart]] = scanResults.map(list => list.flatMap(_._1))

        zip(childrenValidation, highestPartIdValidation)
            .flatMap {
              case (children, highestPartId) => of(None, partId, entity, Some(children))
                .map(part => (part, highestPartId))
            }
      case _ => BlobId.of(blobId, partId)
          .flatMap(blobId => of(Some(blobId), partId, entity, None))
          .map(part => (part, partId))
    }

  private def traverse(properties: Option[Properties], zoneId: ZoneId, blobId: BlobId)(acc: Try[(Option[MinimalEmailBodyPart], PartId)], entity: Entity): Try[(Option[MinimalEmailBodyPart], PartId)] = {
    acc.flatMap {
      case (_, previousPartId) =>
        val partId = previousPartId.next

        of(properties, zoneId, blobId, partId, entity)
          .map({
            case (part, partId) => (Some(part), partId)
          })
    }
  }

  private def of(blobId: Option[BlobId],
                 partId: PartId,
                 entity: Entity,
                 subParts: Option[List[MinimalEmailBodyPart]]): Try[MinimalEmailBodyPart] =
    Try(MinimalEmailBodyPart(
          partId = partId,
          blobId = blobId,
          headers = entity.getHeader.getFields.asScala.toList.map(EmailHeader(_)),
          `type` = Type(entity.getMimeType),
          subParts = subParts,
          entity = entity))

  private def zip[A, B](a: Try[A], b: Try[B]): Try[(A, B)] = for {
    aValue <- a
    bValue <- b
  } yield (aValue, bValue)
}
case class MinimalEmailBodyPart(partId: PartId,
                         blobId: Option[BlobId],
                         headers: List[EmailHeader],
                         `type`: Type,
                         subParts: Option[List[MinimalEmailBodyPart]],
                         entity: Entity) {

  def partWithBlobId(blobId: BlobId): Option[MinimalEmailBodyPart] = flatten.find(_.blobId.contains(blobId))

  def nested(zoneId: ZoneId): Option[MinimalEmailBodyPart] = entity.getBody match {
      case message: Message => of(None, zoneId, blobId.get, message).toOption
      case _ => None
    }

  def size: Try[Size] = refineSize(SizeUtils.sizeOf(entity))

  private def refineSize(l: Long): Try[Size] = refineV[NonNegative](l) match {
    case scala.Right(size) => Success(size)
    case Left(e) => Failure(new IllegalArgumentException(e))
  }


  def flatten: List[MinimalEmailBodyPart] = subParts.getOrElse(Nil).flatMap(part => part.flatten) ++ List(this)
}
