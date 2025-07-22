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
import java.util.Locale
import java.util.concurrent.Callable

import com.google.common.base.Preconditions
import com.google.common.hash.{Hashing, HashingInputStream}
import com.google.common.io.{BaseEncoding, ByteSource, FileBackedOutputStream}
import jakarta.inject.{Inject, Named}
import org.apache.commons.codec.digest.Blake3
import org.apache.commons.io.IOUtils
import org.apache.james.blob.api.BlobStore.BlobIdProvider
import org.apache.james.blob.api.{BlobId, BlobStore, BlobStoreDAO, BucketName}
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore.HashAlgorithms.{BLAKE3, HashAlgorithm, SHA256}
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore.{HashAlgorithms, THREAD_SWITCH_THRESHOLD}
import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.util.function.Tuples

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

  object HashAlgorithms extends Enumeration {
    type HashAlgorithm = Value

    val SHA256, BLAKE3 = Value

    def from(value: String): HashAlgorithm = value.toLowerCase(Locale.US) match {
      case "sha256" => SHA256
      case "blake3" => BLAKE3
      case _ => throw new IllegalArgumentException("Unsupported hashing algorithm: " + value)
    }
  }
}

class DeDuplicationBlobStore @Inject()(blobStoreDAO: BlobStoreDAO,
                                       @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) defaultBucketName: BucketName,
                                       blobIdFactory: BlobId.Factory) extends BlobStore {

  private val HASH_BLOB_ID_ENCODING_TYPE_PROPERTY = "james.blob.id.hash.encoding"
  private val HASH_BLOB_ID_ENCODING_DEFAULT = BaseEncoding.base64Url
  private val baseEncoding = Option(System.getProperty(HASH_BLOB_ID_ENCODING_TYPE_PROPERTY)).map(DeDuplicationBlobStore.baseEncodingFrom).getOrElse(HASH_BLOB_ID_ENCODING_DEFAULT)
  private val HASH_ALGORITHM_PROPERTY = "james.deduplicating.blobstore.hash.algorithm"
  private lazy val hashAlgorithm: HashAlgorithm = Option(System.getProperty(HASH_ALGORITHM_PROPERTY))
    .map(HashAlgorithms.from)
    .getOrElse(SHA256)
  private lazy val BLAKE3_OUTPUT_BYTES_SIZE = 16

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
    val resourceSupplier: Callable[FileBackedOutputStream] = () => new FileBackedOutputStream(DeDuplicationBlobStore.FILE_THRESHOLD)
    val sourceSupplier: FileBackedOutputStream => Mono[(BlobId, InputStream)] = hashAlgorithm match {
      case SHA256 =>
        val hashingInputStream = new HashingInputStream(Hashing.sha256, data)
        (fileBackedOutputStream: FileBackedOutputStream) =>
          SMono.fromCallable(() => {
              IOUtils.copy(hashingInputStream, fileBackedOutputStream)
              (blobIdFactory.of(base64(hashingInputStream.hash.asBytes())), fileBackedOutputStream.asByteSource.openStream())
            })
            .asJava()

      case BLAKE3 =>
        (fileBackedOutputStream: FileBackedOutputStream) =>
          SMono.fromCallable(() => {
              IOUtils.copy(data, fileBackedOutputStream)
              val blake3HashOutput: Array[Byte] = Blake3.initHash.update(fileBackedOutputStream.asByteSource().read()).doFinalize(BLAKE3_OUTPUT_BYTES_SIZE)
              (blobIdFactory.of(base64(blake3HashOutput)), fileBackedOutputStream.asByteSource.openStream())
            })
            .asJava()
    }

    Mono.using[(BlobId, InputStream),FileBackedOutputStream](
      resourceSupplier,
      sourceSupplier.asJava,
      ((fileBackedOutputStream: FileBackedOutputStream) => fileBackedOutputStream.reset()).asJava,
      DeDuplicationBlobStore.LAZY_RESOURCE_CLEANUP)
      .subscribeOn(Schedulers.boundedElastic())
      .map{ case (blobId, data) => Tuples.of(blobId, data)}
  }

  private def withBlobIdFromByteSource: BlobIdProvider[ByteSource] =
    data => hashByteSource(data)
      .subscribeOn(Schedulers.boundedElastic())
      .map(base64)
      .map(blobIdFactory.of)
      .map(blobId => Tuples.of(blobId, data))

  private def hashByteSource(data: ByteSource): Mono[Array[Byte]] =
    Mono.fromCallable(() => hashAlgorithm match {
      case SHA256 =>
        data.hash(Hashing.sha256()).asBytes()
      case BLAKE3 =>
        Blake3.initHash.update(data.read()).doFinalize(BLAKE3_OUTPUT_BYTES_SIZE)
    })

  private def withBlobIdFromArray: BlobIdProvider[Array[Byte]] = data => {
    if (data.length < THREAD_SWITCH_THRESHOLD) {
      val blobId = blobIdFactory.of(base64(hashBytes(data)))
      Mono.just(Tuples.of(blobId, data))
    } else {
      SMono.fromCallable(() => {
        val blobId = blobIdFactory.of(base64(hashBytes(data)))
        Tuples.of(blobId, data)
      })
    }
  }

  private def hashBytes(data: Array[Byte]): Array[Byte] =
    hashAlgorithm match {
      case SHA256 =>
        Hashing.sha256.hashBytes(data).asBytes()
      case BLAKE3 =>
        Blake3.initHash.update(data).doFinalize(BLAKE3_OUTPUT_BYTES_SIZE)
    }

  private def base64(bytes: Array[Byte]): String =
    baseEncoding.encode(bytes)

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
