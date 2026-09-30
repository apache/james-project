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

import jakarta.inject.Inject
import org.apache.james.blob.api.{BlobId, BlobReferenceSource}
import org.apache.james.mailrepository.api.{MailRepositoryStore, Protocol}
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

import scala.jdk.CollectionConverters._

/**
 * Tells the blob store garbage collector ([[org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithm]])
 * which MIME blobs (header + body) are still referenced by every
 * [[BlobMailRepositoryV2]] instance, so GC does not reclaim them as orphaned.
 *
 * Without this, mails stored via [[BlobMailRepositoryV2]] would have their
 * MIME blobs deleted on the next GC pass, since GC only keeps blobs that
 * some [[BlobReferenceSource]] explicitly reports as referenced.
 *
 * Unlike the Cassandra/Postgres siblings - which each own a single global
 * DAO keyed by repository name - this module creates one independent
 * [[BlobMailRepositoryV2]] instance per [[org.apache.james.mailrepository.api.MailRepositoryUrl]]
 * (the URL path is baked into that instance's blob id prefix). This source
 * therefore goes through [[MailRepositoryStore]] to discover every path
 * registered under the "blobv2" protocol, and asks each corresponding
 * repository instance directly for its referenced MIME parts.
 *
 * MUST be registered (e.g. via a Guice Multibinder<BlobReferenceSource>,
 * see BlobstoreMailRepositoryModule) for every deployment using
 * [[BlobMailRepositoryV2]].
 */
class BlobMailRepositoryV2BlobReferenceSource @Inject()(mailRepositoryStore: MailRepositoryStore)
  extends BlobReferenceSource {

  private val LOGGER = LoggerFactory.getLogger(classOf[BlobMailRepositoryV2BlobReferenceSource])
  private val BLOBV2_PROTOCOL = new Protocol("blobv2")

  private def blobV2Repositories: Iterator[BlobMailRepositoryV2] =
    mailRepositoryStore.getUrls.iterator().asScala
      .filter(_.getProtocol == BLOBV2_PROTOCOL)
      .map(_.getPath)
      .distinct
      .flatMap(path => mailRepositoryStore.getByPath(path).iterator().asScala)
      .collect { case repository: BlobMailRepositoryV2 => repository }

  override def listReferencedBlobs(): Flux[BlobId] =
    Flux.fromIterable(blobV2Repositories.iterator.to(Iterable).asJava)
      .flatMap(repository => repository.listReferencedMimeParts
        .onErrorResume(e => {
          LOGGER.warn("Failed listing referenced MIME parts for a BlobMailRepositoryV2 instance", e)
          Flux.empty()
        }))
      .flatMapIterable(mimePartsId => java.util.List.of(mimePartsId.getHeaderBlobId, mimePartsId.getBodyBlobId))
}
