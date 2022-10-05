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

import org.apache.james.blob.api.{BlobId, BlobStoreDAO, BucketName}
import org.apache.james.blob.mail.MimeMessageStore
import org.apache.james.mailrepository.api.{MailRepository, MailRepositoryFactory, MailRepositoryUrl}

class BlobMailRepositoryFactory(blobStore: BlobStoreDAO,
                                blobIdFactory: BlobId.Factory,
                                mimeFactory: MimeMessageStore.Factory) extends MailRepositoryFactory {
  override val mailRepositoryClass: Class[_ <: MailRepository] = classOf[BlobMailRepository]

  override def create(url: MailRepositoryUrl): MailRepository = {
    val metadataBucketName = BucketName.of(url.getPath.asString() + "/mailMetadata")
    val mailDataBucketName = BucketName.of(url.getPath.asString() + "/mimeMessageData")
    val mimeMessageStore = mimeFactory.mimeMessageStore(mailDataBucketName)

    new BlobMailRepository(blobStore, blobIdFactory, mimeMessageStore, metadataBucketName)
  }
}
