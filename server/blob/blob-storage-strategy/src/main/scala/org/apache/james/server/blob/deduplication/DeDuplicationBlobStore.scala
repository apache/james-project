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
import java.util.concurrent.Callable

import com.google.common.base.Preconditions
import com.google.common.hash.{Hashing, HashingInputStream}
import com.google.common.io.{ByteSource, FileBackedOutputStream}
import javax.inject.{Inject, Named}
import org.apache.commons.io.IOUtils
import org.apache.james.blob.api.{BlobId, BlobStore, BlobStoreDAO, BucketName}
import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.util.function.{Tuple2, Tuples}

import scala.compat.java8.FunctionConverters._

object DeDuplicationBlobStore {
  val LAZY_RESOURCE_CLEANUP = false
  val FILE_THRESHOLD = 10000
}

class DeDuplicationBlobStore @Inject()(blobStoreDAO: BlobStoreDAO,
                                       @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) defaultBucketName: BucketName,
                                       blobIdFactory: BlobId.Factory) extends BlobStore {

  override def save(bucketName: BucketName, data: Array[Byte], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    val blobId = blobIdFactory.forPayload(data)

    SMono(blobStoreDAO.save(bucketName, blobId, data))
      .`then`(SMono.just(blobId))
  }

  override def save(bucketName: BucketName, data: ByteSource, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    SMono.fromCallable(() => blobIdFactory.forPayload(data))
      .subscribeOn(Schedulers.boundedElastic())
      .flatMap(blobId => SMono(blobStoreDAO.save(bucketName, blobId, data))
        .`then`(SMono.just(blobId)))
  }

  override def save(bucketName: BucketName, data: InputStream, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)
    val hashingInputStream = new HashingInputStream(Hashing.sha256, data)
    val sourceSupplier: FileBackedOutputStream => Mono[BlobId] = (fileBackedOutputStream: FileBackedOutputStream) => saveAndGenerateBlobId(bucketName, hashingInputStream, fileBackedOutputStream).asJava()
    val ressourceSupplier: Callable[FileBackedOutputStream] = () => new FileBackedOutputStream(DeDuplicationBlobStore.FILE_THRESHOLD)

    Mono.using(
      ressourceSupplier,
      sourceSupplier.asJava,
      ((fileBackedOutputStream: FileBackedOutputStream) => fileBackedOutputStream.reset()).asJava,
      DeDuplicationBlobStore.LAZY_RESOURCE_CLEANUP)
      .subscribeOn(Schedulers.boundedElastic())
  }

  private def saveAndGenerateBlobId(bucketName: BucketName, hashingInputStream: HashingInputStream, fileBackedOutputStream: FileBackedOutputStream): SMono[BlobId] =
    SMono.fromCallable(() => {
      IOUtils.copy(hashingInputStream, fileBackedOutputStream)
      Tuples.of(blobIdFactory.from(hashingInputStream.hash.toString), fileBackedOutputStream.asByteSource)
    }).subscribeOn(Schedulers.boundedElastic())
      .flatMap((tuple: Tuple2[BlobId, ByteSource]) =>
        SMono(blobStoreDAO.save(bucketName, tuple.getT1, tuple.getT2))
          .`then`(SMono.just(tuple.getT1)))


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

    SMono.just(Boolean.box(false))
  }

  override def listBuckets(): Publisher[BucketName] = Flux.concat(blobStoreDAO.listBuckets(), Flux.just(defaultBucketName)).distinct()

  override def listBlobs(bucketName: BucketName): Publisher[BlobId] = blobStoreDAO.listBlobs(bucketName)
}
