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

import com.google.common.base.Preconditions
import com.google.common.hash.{HashCode, Hashing, HashingInputStream}
import com.google.common.io.{BaseEncoding, ByteSource, FileBackedOutputStream}
import jakarta.inject.{Inject, Named}
import org.apache.commons.io.IOUtils
import org.apache.james.blob.api.BlobStore.BlobIdProvider
import org.apache.james.blob.api.{BlobId, BlobStore, BlobStoreDAO, BucketName}
import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.{SMono, tupleTwo2ScalaTuple2}
import reactor.core.scheduler.Schedulers
import reactor.util.function.{Tuple2, Tuples}

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.Callable
import scala.compat.java8.FunctionConverters._

object DeDuplicationBlobStore {
  val LAZY_RESOURCE_CLEANUP = false
  val FILE_THRESHOLD = 10000

  private def baseEncodingFrom(encodingType: String): BaseEncoding = encodingType match {
    case "base16" =>
      BaseEncoding.base16
    case "hex" =>
      BaseEncoding.base16
    case "base64" =>
      BaseEncoding.base64
    case "base64Url" =>
      BaseEncoding.base64Url
    case "base32" =>
      BaseEncoding.base32
    case "base32Hex" =>
      BaseEncoding.base32Hex
    case _ =>
      throw new IllegalArgumentException("Unknown encoding type: " + encodingType)
  }
}

class DeDuplicationBlobStore @Inject()(blobStoreDAO: BlobStoreDAO,
                                       @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) defaultBucketName: BucketName,
                                       blobIdFactory: BlobId.Factory) extends BlobStore {

  private val HASH_BLOB_ID_ENCODING_TYPE_PROPERTY = "james.blob.id.hash.encoding"
  private val HASH_BLOB_ID_ENCODING_DEFAULT = BaseEncoding.base64Url
  private val baseEncoding = Option(System.getProperty(HASH_BLOB_ID_ENCODING_TYPE_PROPERTY)).map(DeDuplicationBlobStore.baseEncodingFrom).getOrElse(HASH_BLOB_ID_ENCODING_DEFAULT)

  override def save(bucketName: BucketName, data: Array[Byte], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobId, storagePolicy)
  }

  override def save(bucketName: BucketName, data: InputStream, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobId, storagePolicy)
  }

  override def save(bucketName: BucketName, data: ByteSource, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobId, storagePolicy)
  }

  override def save(bucketName: BucketName, data: Array[Byte], blobIdProvider: BlobIdProvider, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)
    save(bucketName, new ByteArrayInputStream(data), blobIdProvider, storagePolicy)
  }

  override def save(bucketName: BucketName, data: ByteSource, blobIdProvider: BlobIdProvider, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    SMono.fromCallable(() => data.openStream())
      .using(
        use = stream => SMono(blobIdProvider.apply(stream))
          .subscribeOn(Schedulers.boundedElastic())
          .map(tupleTwo2ScalaTuple2)
          .flatMap { case (blobId, inputStream) =>
            SMono(blobStoreDAO.save(bucketName, blobId, inputStream))
              .`then`(SMono.just(blobId))
          })(
        release = _.close())
  }

  private def withBlobId(data: InputStream): Publisher[Tuple2[BlobId, InputStream]] = {
    val hashingInputStream = new HashingInputStream(Hashing.sha256, data)
    val ressourceSupplier: Callable[FileBackedOutputStream] = () => new FileBackedOutputStream(DeDuplicationBlobStore.FILE_THRESHOLD)
    val sourceSupplier: FileBackedOutputStream => Mono[(BlobId, InputStream)] =
      (fileBackedOutputStream: FileBackedOutputStream) =>
        SMono.fromCallable(() => {
          IOUtils.copy(hashingInputStream, fileBackedOutputStream)
          (blobIdFactory.of(base64(hashingInputStream.hash)), fileBackedOutputStream.asByteSource.openStream())
        }).asJava()

    Mono.using[(BlobId, InputStream),FileBackedOutputStream](
      ressourceSupplier,
      sourceSupplier.asJava,
      ((fileBackedOutputStream: FileBackedOutputStream) => fileBackedOutputStream.reset()).asJava,
      DeDuplicationBlobStore.LAZY_RESOURCE_CLEANUP
    ) .subscribeOn(Schedulers.boundedElastic())
      .map{ case (blobId, data) => Tuples.of(blobId, data)}
  }

  private def base64(hashCode: HashCode) = {
    val bytes = hashCode.asBytes
    baseEncoding.encode(bytes)
  }

  override def save(bucketName: BucketName,
                    data: InputStream,
                    blobIdProvider: BlobIdProvider,
                    storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(data)

    Mono.from(blobIdProvider(data)).subscribeOn(Schedulers.boundedElastic())
      .flatMap { tuple =>
        SMono(blobStoreDAO.save(bucketName, tuple.getT1, tuple.getT2))
          .`then`(SMono.just(tuple.getT1)).asJava()
      }
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

    SMono.just(Boolean.box(false))
  }

  override def listBuckets(): Publisher[BucketName] = Flux.concat(blobStoreDAO.listBuckets(), Flux.just(defaultBucketName)).distinct()

  override def listBlobs(bucketName: BucketName): Publisher[BlobId] = blobStoreDAO.listBlobs(bucketName)
}
