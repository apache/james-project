/** **************************************************************
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
 * ************************************************************** */
package org.apache.james.mailrepository.blob

import java.time.Instant

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._
import scala.jdk.OptionConverters._

import org.apache.james.blob.api.BlobId
import org.apache.james.blob.mail.MimeMessagePartsId
import org.apache.mailet.Mail

private[blob] object Header {
  def of: ((String, Iterable[String])) => Header = (this.apply _).tupled
}

private[blob] case class Header(key: String, values: Iterable[String])

private[blob] object MailMetadata {
  def of(mail: Mail, partsId: MimeMessagePartsId): MailMetadata = {
    MailMetadata(
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

  private def fromPerRecipientHeaders(mail: Mail): Map[String, Iterable[Header]] = {
    mail.getPerRecipientSpecificHeaders
      .getHeadersByRecipient
      .asMap
      .asScala
      .view
      .map { case (mailAddress, headers) =>
        mailAddress.asString() -> headers
          .asScala
          .groupMap(_.getName)(_.getValue)
          .map(Header.of)
      }.toMap

  }
}

private[blob] case class MailMetadata(
                        recipients: Seq[String],
                        name: String,
                        sender: Option[String],
                        state: Option[String],
                        errorMessage: Option[String],
                        lastUpdated: Option[Instant],
                        attributes: Map[String, String],
                        remoteAddr: String,
                        remoteHost: String,
                        perRecipientHeaders: Map[String, Iterable[Header]],
                        headerBlobId: String,
                        bodyBlobId: String){

  def mimePartsId(implicit blobIdFactory: BlobId.Factory): MimeMessagePartsId =
    MimeMessagePartsId.builder()
      .headerBlobId(blobIdFactory.from(headerBlobId))
      .bodyBlobId(blobIdFactory.from(bodyBlobId))
      .build()

}