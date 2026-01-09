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

import com.google.common.collect.ImmutableMap
import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED
import org.apache.james.blob.api.Store.Impl
import org.apache.james.blob.api._
import org.apache.james.blob.mail.MimeMessagePartsId
import org.apache.james.core.{MailAddress, MaybeSender}
import org.apache.james.mailrepository.api.{MailKey, MailRepository, MailRepositoryUrl}
import org.apache.james.server.core.MailImpl
import org.apache.james.util.AuditTrail
import org.apache.mailet._
import play.api.libs.json.{Format, Json}
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.SMono
import reactor.util.function.Tuples

import java.io.{ByteArrayInputStream, InputStream}
import java.util
import java.util.Date
import java.util.stream.Stream
import scala.jdk.CollectionConverters.IterableHasAsJava
import scala.jdk.StreamConverters._


private[blob] object serializers {
  implicit val headerFormat: Format[Header] = Json.format[Header]
  implicit val mailMetadataFormat: Format[MailMetadata] = Json.format[MailMetadata]
}

object BlobMailRepository {
  private[blob] object MailPartsId {
    private[blob] val METADATA_BLOB_TYPE = new BlobType("mailMetadata", SIZE_BASED)

    class Factory extends BlobPartsId.Factory[BlobMailRepository.MailPartsId] {
      override def generate(map: util.Map[BlobType, BlobId]): MailPartsId = {
        require(map.containsKey(METADATA_BLOB_TYPE), "Expecting 'mailMetadata' blobId to be specified")
        require(map.size == 1, "blobId other than 'mailMetadata' are not supported")
        new BlobMailRepository.MailPartsId(map.get(METADATA_BLOB_TYPE))
      }
    }
  }

  private[blob] case class MailPartsId private[blob](metadataBlobId: BlobId) extends BlobPartsId {
    override def asMap: ImmutableMap[BlobType, BlobId] = ImmutableMap.of(MailPartsId.METADATA_BLOB_TYPE, metadataBlobId)

    def toMailKey: MailKey = new MailKey(metadataBlobId.asString())
  }

  private[blob] class MailEncoder private[blob](blobIdFactory: MailRepositoryBlobIdFactory)
    extends Impl.Encoder[(Mail, MimeMessagePartsId)] {

    import serializers._

    override def encode(mailAndPartsId: (Mail, MimeMessagePartsId)): Stream[Pair[BlobType, Impl.ValueToSave]] = {
      val (mail, partsIds) = mailAndPartsId
      val mailMetadata = MailMetadata.of(mail, partsIds)
      val payload = Json.stringify(Json.toJson(mailMetadata))
      val mailKey = MailKey.forMail(mail)

      val blobId = blobIdFactory.of(mailKey.asString())

      val save: Impl.ValueToSave = (bucketName, blobStore) =>
        Mono.from(blobStore.save(
          bucketName,
          new ByteArrayInputStream(payload.getBytes),
          (data:InputStream)=>SMono.just(Tuples.of(blobId,data)).asJava(),
          BlobStore.StoragePolicy.SIZE_BASED
        ))

      LazyList(Pair.of(MailPartsId.METADATA_BLOB_TYPE, save)).asJavaSeqStream
    }
  }

  private[blob] class MailDecoder private[blob](blobIdFactory: BlobId.Factory)
    extends Impl.Decoder[(Mail, MimeMessagePartsId)] {

    private def readMail(mailMetadata: MailMetadata): Mail = {
      val builder = MailImpl.builder
        .name(mailMetadata.name)
        .sender(mailMetadata.sender.map(MaybeSender.getMailSender).getOrElse(MaybeSender.nullSender))
        .addRecipients(mailMetadata.recipients.map(new MailAddress(_)).asJavaCollection)
        .remoteAddr(mailMetadata.remoteAddr)
        .remoteHost(mailMetadata.remoteHost)

      mailMetadata.state.foreach(builder.state)
      mailMetadata.errorMessage.foreach(builder.errorMessage)

      mailMetadata.lastUpdated.map(Date.from).foreach(builder.lastUpdated)

      mailMetadata.attributes.foreach { case (name, value) => builder.addAttribute(new Attribute(AttributeName.of(name), AttributeValue.fromJsonString(value))) }

      builder.addAllHeadersForRecipients(retrievePerRecipientHeaders(mailMetadata.perRecipientHeaders))

      builder.build
    }


    private def retrievePerRecipientHeaders(perRecipientHeaders: Map[String, Iterable[Header]]): PerRecipientHeaders = {
      val result = new PerRecipientHeaders()
      perRecipientHeaders.foreach { case (key, value) =>
        value.foreach(headers => {
          headers.values.foreach(header => {
            val builder = PerRecipientHeaders.Header.builder().name(headers.key).value(header)
            result.addHeaderForRecipient(builder, new MailAddress(key))
          })
        })
      }
      result
    }


    import serializers._

    override def decode(streams: util.Map[BlobType, Store.CloseableByteSource]): (Mail, MimeMessagePartsId) = {
      val source = streams.get(MailPartsId.METADATA_BLOB_TYPE)
      val value = Json.fromJson[MailMetadata](Json.parse(source.openStream())).get
      (readMail(value), value.mimePartsId(blobIdFactory))
    }
  }
}

class BlobMailRepository(val mailMetaDataBlobStore: BlobStore,
                         val mailMetadataBlobIdFactory: MailRepositoryBlobIdFactory,
                         val mimeMessageStore: Store[MimeMessage, MimeMessagePartsId],
                         val url: MailRepositoryUrl,
                        ) extends MailRepository {

  import BlobMailRepository._


  @throws[MessagingException]
  override def store(mc: Mail): MailKey =
    mimeMessageStore.save(mc.getMessage)
      .flatMap(mimePartsId => mailMetadataStore.save((mc, mimePartsId)))
      .doOnSuccess(_ => AuditTrail.entry
        .protocol("mailrepository")
        .action("store")
        .parameters(() => ImmutableMap.of("mailId", mc.getName,
          "mimeMessageId", Option(mc.getMessage)
            .flatMap(message => Option(message.getMessageID))
            .getOrElse(""),
          "sender", mc.getMaybeSender.asString,
          "recipients", StringUtils.join(mc.getRecipients)))
        .log("BlobMailRepository stored mail."))
      .map(mailPartsId => mailPartsId.toMailKey)
      .block()

  @throws[MessagingException]
  override def size: Long =
    Flux.from(mailMetaDataBlobStore.listBlobs(BucketName.DEFAULT))
    .filter(this.belongsToMailRepository)
    .count()
    .block()

  @throws[MessagingException]
  override def list: util.Iterator[MailKey] =
    Flux.from(mailMetaDataBlobStore.listBlobs(BucketName.DEFAULT))
    .filter(this.belongsToMailRepository)
    .map[MailKey](blobId => new MailKey(blobId.asString))
    .toIterable
    .iterator

  private def belongsToMailRepository(blobId: BlobId): Boolean =
    blobId.asString().startsWith(url.getPath.asString())

  @throws[MessagingException]
  override def retrieve(key: MailKey): Mail = {
    mailMetadataStore.read(MailPartsId(mailMetadataBlobIdFactory.parse(key.asString())))
      .flatMap {
        case (mail, mimeMessagePartsId) => mimeMessageStore.read(mimeMessagePartsId).map { mimeMessage =>
          mail.setMessage(mimeMessage)
          Some(mail): Option[Mail]
        }
      }
      .onErrorReturn(None)
      .block()
      .orNull
  }

  @throws[MessagingException]
  override def remove(key: MailKey): Unit = {
    val mailMetadataId: MailPartsId = MailPartsId(mailMetadataBlobIdFactory.parse(key.asString()))
    remove(mailMetadataId).block()
  }

  private def remove(mailMetadataId: MailPartsId): SMono[Unit] =
    for {
      maybeMimeMessagePartsId <- SMono(mailMetadataStore.read(mailMetadataId))
        .map { case ((_, mimeMessagePartsId)) => Some(mimeMessagePartsId) }
        .onErrorRecover(_ => None)
      _ <- SMono(mailMetadataStore.delete(mailMetadataId))
      _ <- SMono(maybeMimeMessagePartsId.map(mimeMessagePartsId => mimeMessageStore.delete(mimeMessagePartsId)).getOrElse(SMono.empty))
    } yield ()


  @throws[MessagingException]
  override def removeAll(): Unit = {
    Flux.from(mailMetaDataBlobStore.listBlobs(BucketName.DEFAULT))
      .flatMap(blobId => this.remove(MailPartsId(blobId)))
      .blockLast()
  }

  private val mailMetadataStore = new Store.Impl[(Mail, MimeMessagePartsId), BlobMailRepository.MailPartsId](
    new MailPartsId.Factory,
    new BlobMailRepository.MailEncoder(mailMetadataBlobIdFactory),
    new BlobMailRepository.MailDecoder(mailMetadataBlobIdFactory),
    mailMetaDataBlobStore
  )
}