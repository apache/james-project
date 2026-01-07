/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.mailrepository.blob

import jakarta.mail.internet.MimeMessage
import org.apache.james.blob.api._
import org.apache.james.blob.mail.{MimeMessagePartsId, MimeMessageStore}
import org.apache.james.mailrepository.api.{MailRepository, MailRepositoryFactory, MailRepositoryUrl}
import org.apache.james.server.blob.deduplication.BlobStoreFactory

class MailRepositoryBlobIdFactory(
                                   blobIdFactory: BlobId.Factory,
                                   url: MailRepositoryUrl
                                 ) extends BlobId.Factory {
  // Must wrap the default BlobId factory but inject a MailRepositoryUrl dependant prefix
  override def parse(id: String): BlobId =
    blobIdFactory.parse(id)

  override def of(id: String): BlobId =
    blobIdFactory.of(url.getPath.subPath(id).asString())

}

class BlobMailRepositoryFactory(blobStoreDao: BlobStoreDAO,
                                blobIdFactory: BlobId.Factory) extends MailRepositoryFactory {
  override val mailRepositoryClass: Class[_ <: MailRepository] = classOf[BlobMailRepository]

  override def create(url: MailRepositoryUrl): MailRepository = {
    val metadataUrl = url.subUrl("mailMetadata")
    val metadataIdFactory = new MailRepositoryBlobIdFactory(
      blobIdFactory = blobIdFactory,
      url = metadataUrl)

    val metadataBlobStore = BlobStoreFactory.builder()
      .blobStoreDAO(blobStoreDao)
      .blobIdFactory(metadataIdFactory)
      .passthrough()

    val mimeMessageStore: Store[MimeMessage, MimeMessagePartsId] = buildMimeMessageStore(url)

    new BlobMailRepository(metadataBlobStore, metadataIdFactory, mimeMessageStore, metadataUrl)
  }

  private def buildMimeMessageStore(url: MailRepositoryUrl) = {
    val mimeMessageIdFactory = new MailRepositoryBlobIdFactory(
      blobIdFactory = blobIdFactory,
      url = url.subUrl("mimeMessagedata"))

    val mimeMessageBlobStore = BlobStoreFactory.builder()
      .blobStoreDAO(blobStoreDao)
      .blobIdFactory(mimeMessageIdFactory)
      .passthrough()
    val mimeMessageStore = new MimeMessageStore.Factory(mimeMessageBlobStore).mimeMessageStore()
    mimeMessageStore
  }
}
