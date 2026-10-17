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
package org.apache.james.mailrepository.blob

import java.time.Instant

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._
import scala.jdk.OptionConverters._

import org.apache.james.blob.api.BlobId
import org.apache.james.blob.mail.MimeMessagePartsId
import org.apache.mailet.Mail

/**
 * Per-recipient header entry, V2 storage model.
 *
 * Deliberately a distinct type from [[Header]] (the V1 model) so that
 * [[BlobMailRepositoryV2]] shares no code nor on-wire schema with
 * [[BlobMailRepository]]. This keeps V2 fully self-contained: dropping V1
 * later is a pure file deletion with zero impact on V2.
 */
private[blob] object HeaderV2 {
  def of: ((String, Iterable[String])) => HeaderV2 = (this.apply _).tupled
}

private[blob] case class HeaderV2(key: String, values: Iterable[String])

/**
 * Metadata payload persisted by [[BlobMailRepositoryV2]].
 *
 * This is the V2 on-wire storage format. It is intentionally independent from
 * the V1 [[MailMetadata]]: V2 owns its own schema so its layout can evolve
 * freely without any retro-compatibility constraint against V1.
 *
 * The leading [[version]] field is a schema discriminator: every blob written
 * by V2 carries [[MailMetadataV2.CURRENT_VERSION]], giving future format
 * revisions an unambiguous hook to branch on while reading.
 */
private[blob] object MailMetadataV2 {
  /** Current V2 metadata schema version, stamped on every write. */
  val CURRENT_VERSION: Int = 2

  def of(mail: Mail, partsId: MimeMessagePartsId): MailMetadataV2 = {
    MailMetadataV2(
      CURRENT_VERSION,
      Option(mail.getRecipients).map(_.asScala.map(_.asString).toSeq).getOrElse(Seq.empty),
      mail.getName,
      mail.getMaybeSender.asOptional().map(_.asString()).toScala,
      Option(mail.getState),
      Option(mail.getErrorMessage),
      Option(mail.getLastUpdated).map(_.toInstant),
      serializedAttributes(mail),
      mail.getRemoteAddr,
      mail.getRemoteHost,
      fromPerRecipientHeaders(mail),
      partsId.getHeaderBlobId.asString(),
      partsId.getBodyBlobId.asString()
    )
  }

  private def serializedAttributes(mail: Mail): Map[String, String] =
    mail.attributes().toScala(LazyList)
      .flatMap(attribute => attribute.getValue.toJson.toScala.map(value => attribute.getName.asString() -> value.toString))
      .toMap

  private def fromPerRecipientHeaders(mail: Mail): Map[String, Iterable[HeaderV2]] = {
    mail.getPerRecipientSpecificHeaders
      .getHeadersByRecipient
      .asMap
      .asScala
      .view
      .map { case (mailAddress, headers) =>
        mailAddress.asString() -> headers
          .asScala
          .groupMap(_.getName)(_.getValue)
          .map(HeaderV2.of)
      }.toMap
  }
}

private[blob] case class MailMetadataV2(
                        version: Int,
                        recipients: Seq[String],
                        name: String,
                        sender: Option[String],
                        state: Option[String],
                        errorMessage: Option[String],
                        lastUpdated: Option[Instant],
                        attributes: Map[String, String],
                        remoteAddr: String,
                        remoteHost: String,
                        perRecipientHeaders: Map[String, Iterable[HeaderV2]],
                        headerBlobId: String,
                        bodyBlobId: String) {

  def mimePartsId(implicit blobIdFactory: BlobId.Factory): MimeMessagePartsId =
    MimeMessagePartsId.builder()
      .headerBlobId(blobIdFactory.parse(headerBlobId))
      .bodyBlobId(blobIdFactory.parse(bodyBlobId))
      .build()

}
