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
import jakarta.inject.Inject
import org.apache.commons.io.IOUtils
import org.apache.james.blob.api.BlobStore.BlobIdProvider
import org.apache.james.blob.api.{BlobId, BlobStore, BlobStoreDAO, BucketName}
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore.THREAD_SWITCH_THRESHOLD
import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.util.function.Tuples

import java.io.InputStream
import java.util.concurrent.Callable
import scala.compat.java8.FunctionConverters._

object DeDuplicationBlobStore {
  val LAZY_RESOURCE_CLEANUP = false
  val FILE_THRESHOLD = Integer.parseInt(System.getProperty("james.deduplicating.blobstore.file.threshold", "10240"))
  val THREAD_SWITCH_THRESHOLD = Integer.parseInt(System.getProperty("james.deduplicating.blobstore.thread.switch.threshold", "32768"));

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
                                       blobIdFactory: BlobId.Factory) extends BlobStore {

  private val HASH_BLOB_ID_ENCODING_TYPE_PROPERTY = "james.blob.id.hash.encoding"
  private val HASH_BLOB_ID_ENCODING_DEFAULT = BaseEncoding.base64Url
  private val baseEncoding = Option(System.getProperty(HASH_BLOB_ID_ENCODING_TYPE_PROPERTY)).map(DeDuplicationBlobStore.baseEncodingFrom).getOrElse(HASH_BLOB_ID_ENCODING_DEFAULT)

  override def save(bucketName: BucketName, data: Array[Byte], storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobIdFromArray, storagePolicy)
  }

  override def save(bucketName: BucketName, data: InputStream, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobId, storagePolicy)
  }

  override def save(bucketName: BucketName, data: ByteSource, storagePolicy: BlobStore.StoragePolicy): Publisher[BlobId] = {
    save(bucketName, data, withBlobIdFromByteSource, storagePolicy)
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

  private def withBlobId: BlobIdProvider[InputStream] = data => {
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
      DeDuplicationBlobStore.LAZY_RESOURCE_CLEANUP)
      .subscribeOn(Schedulers.boundedElastic())
      .map{ case (blobId, data) => Tuples.of(blobId, data)}
  }

  private def withBlobIdFromByteSource: BlobIdProvider[ByteSource] =
    data => Mono.fromCallable(() => data.hash(Hashing.sha256()))
      .subscribeOn(Schedulers.boundedElastic())
      .map(base64)
      .map(blobIdFactory.of)
      .map(blobId => Tuples.of(blobId, data))

  private def withBlobIdFromArray: BlobIdProvider[Array[Byte]] = data => {
    if (data.length < THREAD_SWITCH_THRESHOLD) {
      val code = Hashing.sha256.hashBytes(data)
      val blobId = blobIdFactory.of(base64(code))
      Mono.just(Tuples.of(blobId, data))
    } else {
      SMono.fromCallable(() => {
        val code = Hashing.sha256.hashBytes(data)
        val blobId = blobIdFactory.of(base64(code))
        Tuples.of(blobId, data)
      })
    }
  }

  private def base64(hashCode: HashCode) = {
    val bytes = hashCode.asBytes
    baseEncoding.encode(bytes)
  }

  override def save(bucketName: BucketName,
                    data: InputStream,
                    blobIdProvider: BlobIdProvider[InputStream],
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

  override def deleteBucket(bucketName: BucketName): Publisher[Void] = {
    blobStoreDAO.deleteBucket(bucketName)
  }

  override def delete(bucketName: BucketName, blobId: BlobId): Publisher[java.lang.Boolean] = {
    Preconditions.checkNotNull(bucketName)
    Preconditions.checkNotNull(blobId)

    SMono.just(Boolean.box(false))
  }

  override def listBuckets(): Publisher[BucketName] = Flux.concat(blobStoreDAO.listBuckets(), Flux.just(BucketName.DEFAULT)).distinct()

  override def listBlobs(bucketName: BucketName): Publisher[BlobId] = blobStoreDAO.listBlobs(bucketName)
}
