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

import jakarta.inject.Named
import jakarta.mail.internet.MimeMessage
import org.apache.james.blob.api._
import org.apache.james.blob.mail.{MimeMessagePartsId, MimeMessageStore}
import org.apache.james.mailrepository.api.{MailRepository, MailRepositoryFactory, MailRepositoryUrl}
import org.apache.james.server.blob.deduplication.BlobStoreFactory

/**
 * Wraps a default [[BlobId.Factory]] and injects a [[MailRepositoryUrl]]-dependent
 * path prefix into every generated blob id, so that blobs belonging to a given
 * [[BlobMailRepositoryV2]] instance live under that repository's own path.
 *
 * This is the V2 counterpart of the V1 id factory: it is defined here, inside
 * the V2 module, so that [[BlobMailRepositoryV2]] shares no type with
 * [[BlobMailRepository]]. Dropping V1 later has zero impact on V2.
 */
class MailRepositoryBlobIdFactoryV2(
                                     blobIdFactory: BlobId.Factory,
                                     url: MailRepositoryUrl
                                   ) extends BlobId.Factory {
  // Must wrap the default BlobId factory but inject a MailRepositoryUrl dependant prefix
  override def parse(id: String): BlobId =
    blobIdFactory.parse(id)

  override def of(id: String): BlobId =
    blobIdFactory.of(url.getPath.subPath(id).asString())

  // Random and content addressed ids go through `of`, hence through the url prefix, on their own.
  override def encoding(): BlobIdEncoding =
    blobIdFactory.encoding()
}

/**
 * Mirrors [[BlobMailRepositoryFactory]] for [[BlobMailRepositoryV2]].
 *
 * Kept as a separate factory (rather than reusing BlobMailRepositoryFactory)
 * so that V2 can evolve its storage layout independently of the original
 * [[BlobMailRepository]] without any retro-compatibility constraint. V2 shares
 * no type with V1: it uses [[MailRepositoryBlobIdFactoryV2]], not the V1 id factory.
 */
class BlobMailRepositoryV2Factory(blobStoreDao: BlobStoreDAO,
                                  blobIdFactory: BlobId.Factory,
                                  @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) defaultBucketName: BucketName
                                 ) extends MailRepositoryFactory {
  override val mailRepositoryClass: Class[_ <: MailRepository] = classOf[BlobMailRepositoryV2]

  override def create(url: MailRepositoryUrl): MailRepository = {
    val metadataUrl = url.subUrl("mailMetadata")
    val metadataIdFactory = new MailRepositoryBlobIdFactoryV2(
      blobIdFactory = blobIdFactory,
      url = metadataUrl
    )

    val metadataBlobStore = BlobStoreFactory.builder()
      .blobStoreDAO(blobStoreDao)
      .blobIdFactory(
        metadataIdFactory
      )
      .bucket(defaultBucketName)
      .passthrough()

    val mimeMessageStore: Store[MimeMessage, MimeMessagePartsId] = buildMimeMessageStore(url)

    new BlobMailRepositoryV2(metadataBlobStore, metadataIdFactory, mimeMessageStore, metadataUrl)
  }

  private def buildMimeMessageStore(url: MailRepositoryUrl) = {
    val mimeMessageIdFactory = new MailRepositoryBlobIdFactoryV2(
      blobIdFactory = blobIdFactory,
      url = url.subUrl("mimeMessagedata")
    )
    val mimeMessageBlobStore = BlobStoreFactory.builder()
      .blobStoreDAO(blobStoreDao)
      .blobIdFactory(
        mimeMessageIdFactory
      )
      .bucket(defaultBucketName)
      .passthrough()
    new MimeMessageStore.Factory(mimeMessageBlobStore).mimeMessageStore()
  }
}
