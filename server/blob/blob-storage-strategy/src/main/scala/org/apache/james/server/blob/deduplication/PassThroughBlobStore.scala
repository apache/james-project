/** ***************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * *************************************************************** */

package org.apache.james.server.blob.deduplication

import com.google.common.base.Preconditions
import com.google.common.io.ByteSource
import jakarta.inject.Inject
import org.apache.james.blob.api.BlobStore.BlobIdProvider
import org.apache.james.blob.api.{BlobId, BlobStore, BlobStoreDAO, BucketName}
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.util.function.Tuples

import java.io.InputStream
import java.util.UUID

class PassThroughBlobStore @Inject()(blobStoreDAO: BlobStoreDAO, blobIdFactory: BlobId.Factory) extends BlobStore {

  override def save(bucketName: BucketName, data: Array[Byte], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobIdByteArray, storagePolicy)
  }

  override def save(bucketName: BucketName, data: InputStream, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobId, storagePolicy)
  }

  override def save(bucketName: BucketName, data: ByteSource, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobIdByteSource, storagePolicy)
  }

  override def save(bucketName: BucketName, data: Array[Byte], blobIdProvider: BlobIdProvider[Array[Byte]], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)
    SMono(blobIdProvider.apply(data))
      .map(_.getT1)
      .flatMap(blobId => SMono(blobStoreDAO.save(bucketName, blobId, data))
        .`then`(SMono.just(blobId)))
  }

  override def save(bucketName: BucketName, data: ByteSource, blobIdProvider: BlobIdProvider[ByteSource], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    SMono(blobIdProvider.apply(data))
      .map(_.getT1)
      .flatMap(blobId => SMono(blobStoreDAO.save(bucketName, blobId, data))
        .`then`(SMono.just(blobId)))
      .subscribeOn(Schedulers.boundedElastic())
  }

  override def save(
                     bucketName: BucketName,
                     data: InputStream,
                     blobIdProvider: BlobIdProvider[InputStream],
                     storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    SMono(blobIdProvider(data))
      .subscribeOn(Schedulers.boundedElastic())
      .flatMap { tuple =>
        SMono(blobStoreDAO.save(bucketName, tuple.getT1, tuple.getT2))
          .`then`(SMono.just(tuple.getT1))
      }
  }

  private def withBlobId: BlobIdProvider[InputStream] = data =>
    SMono.just(Tuples.of(blobIdFactory.of(UUID.randomUUID.toString), data))
  private def withBlobIdByteArray: BlobIdProvider[Array[Byte]] = data =>
    SMono.just(Tuples.of(blobIdFactory.of(UUID.randomUUID.toString), data))
  private def withBlobIdByteSource: BlobIdProvider[ByteSource] = data =>
    SMono.just(Tuples.of(blobIdFactory.of(UUID.randomUUID.toString), data))

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

  override def deleteBucket(bucketName: BucketName): Publisher[Void] = {
    blobStoreDAO.deleteBucket(bucketName)
  }

  override def delete(bucketName: BucketName, blobId: BlobId): Publisher[java.lang.Boolean] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(blobId)

    SMono.fromPublisher(blobStoreDAO.delete(bucketName, blobId))
      .`then`(SMono.just(Boolean.box(true)))
  }

  override def listBlobs(bucketName: BucketName): Publisher[BlobId] = blobStoreDAO.listBlobs(bucketName)
}
