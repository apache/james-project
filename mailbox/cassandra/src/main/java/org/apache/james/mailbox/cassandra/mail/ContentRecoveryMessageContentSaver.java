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

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdEntropy;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreCacheCallback;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;
import org.apache.james.blob.api.BlobStoreDAO.BytesBlob;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Carries the recovery information within the header blob itself, rather than in a companion object.
 *
 * <p>The body is written first, then the headers are written under a randomly generated blob id carrying
 * the body blob id as metadata. Recovering a message therefore only requires walking the header blobs of
 * the bucket: the {@value #HEADER_BLOB_ID_SUFFIX} suffix tells them apart, and the
 * {@code body-blob-id} metadata points at their body.</p>
 *
 * <p>The header blob id is random rather than content addressed, so that a header and its recovery
 * information stay paired. Headers are consequently not deduplicated, which they hardly ever were.</p>
 *
 * <p>Headers go through the {@link BlobStoreDAO} rather than the {@link BlobStore} because only the
 * former exposes metadata. That DAO is the decorated one, so compression and encryption still apply; the
 * caching a {@code SIZE_BASED} save would have performed is restored by {@link BlobStoreCacheCallback}.</p>
 */
public class ContentRecoveryMessageContentSaver implements MessageContentSaver {
    public static final String HEADER_BLOB_ID_SUFFIX = "_hdr";
    public static final BlobMetadataName BODY_BLOB_ID = new BlobMetadataName("body-blob-id");
    private static final BaseEncoding BLOB_ID_ENCODING = BaseEncoding.base64Url().omitPadding();

    private final BlobStore blobStore;
    private final BlobStoreDAO blobStoreDAO;
    private final BlobId.Factory blobIdFactory;
    private final BlobStoreCacheCallback cacheCallback;

    public ContentRecoveryMessageContentSaver(BlobStore blobStore, BlobStoreDAO blobStoreDAO,
                                              BlobId.Factory blobIdFactory, BlobStoreCacheCallback cacheCallback) {
        this.blobStore = blobStore;
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdFactory = blobIdFactory;
        this.cacheCallback = cacheCallback;
    }

    @Override
    public Mono<Tuple2<BlobId, BlobId>> saveContent(byte[] headerBytes, ByteSource bodyByteSource) {
        return Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyByteSource, LOW_COST))
            .flatMap(bodyId -> saveHeaders(headerBytes, bodyId)
                .map(headerId -> Tuples.of(headerId, bodyId)));
    }

    private Mono<BlobId> saveHeaders(byte[] headerBytes, BlobId bodyId) {
        BlobId headerId = generateHeaderBlobId();
        BlobMetadata metadata = BlobMetadata.empty()
            .withMetadata(BODY_BLOB_ID, new BlobMetadataValue(bodyId.asString()));

        return Mono.from(blobStoreDAO.save(blobStore.getDefaultBucketName(), headerId, BytesBlob.of(headerBytes, metadata)))
            .then(Mono.from(cacheCallback.cacheIfNeeded(headerId, headerBytes)))
            .thenReturn(headerId);
    }

    /**
     * Leaves the family and generation prefixes to the configured {@link BlobId.Factory}, so that the
     * header blob stays generation aware and is garbage collected like any other blob.
     */
    private BlobId generateHeaderBlobId() {
        return blobIdFactory.of(BLOB_ID_ENCODING.encode(BlobIdEntropy.randomBytes()) + HEADER_BLOB_ID_SUFFIX);
    }
}
