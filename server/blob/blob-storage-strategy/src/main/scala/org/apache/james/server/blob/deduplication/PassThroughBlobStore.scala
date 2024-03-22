/*****************************************************************
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
 *****************************************************************/

package org.apache.james.server.blob.deduplication

import java.io.InputStream

import com.google.common.base.Preconditions
import com.google.common.io.ByteSource
import jakarta.inject.{Inject, Named}
import org.apache.james.blob.api.{BlobId, BlobStore, BlobStoreDAO, BucketName}
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono


class PassThroughBlobStore @Inject()(blobStoreDAO: BlobStoreDAO,
                                     @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) defaultBucketName: BucketName,
                                     blobIdFactory: BlobId.Factory) extends BlobStore {

  override def save(bucketName: BucketName, data: Array[Byte], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    val blobId = blobIdFactory.randomId()

    SMono(blobStoreDAO.save(bucketName, blobId, data))
      .`then`(SMono.just(blobId))
  }

  override def save(bucketName: BucketName, data: InputStream, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)
    val blobId = blobIdFactory.randomId()

    SMono(blobStoreDAO.save(bucketName, blobId, data))
      .`then`(SMono.just(blobId))
  }

  override def save(bucketName: BucketName, data: ByteSource, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)
    val blobId = blobIdFactory.randomId()

    SMono(blobStoreDAO.save(bucketName, blobId, data))
      .`then`(SMono.just(blobId))
  }

  override def readBytes(bucketName: BucketName, blobId: BlobId): Publisher[Array[Byte]] = {
    Preconditions.checkNotNull(bucketName)

    blobStoreDAO.readBytes(bucketName, blobId)
  }

  override def read(bucketName: BucketName, blobId: BlobId): InputStream = {
    Preconditions.checkNotNull(bucketName)

    blobStoreDAO.read(bucketName, blobId)
  }

  override def readReactive(bucketName: BucketName, blobId: BlobId): Publisher[InputStream] = {
    Preconditions.checkNotNull(bucketName)

    blobStoreDAO.readReactive(bucketName, blobId)
  }

  override def getDefaultBucketName: BucketName = defaultBucketName

  override def deleteBucket(bucketName: BucketName): Publisher[Void] = {
    blobStoreDAO.deleteBucket(bucketName)
  }

  override def delete(bucketName: BucketName, blobId: BlobId): Publisher[java.lang.Boolean] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(blobId)

    SMono.fromPublisher(blobStoreDAO.delete(bucketName, blobId))
      .`then`(SMono.just(Boolean.box(true)))
  }

  override def listBuckets(): Publisher[BucketName] = Flux.concat(blobStoreDAO.listBuckets(), Flux.just(defaultBucketName)).distinct()

  override def listBlobs(bucketName: BucketName): Publisher[BlobId] = blobStoreDAO.listBlobs(bucketName)
}
