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

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration.BlobRecoveryMode;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

/**
 * Delegates the content write, then materializes the recovery information as a sidecar blob:
 * the body blob id, stored under the header blob id prefixed by {@link BlobStoreDAO#RECOVERY_BLOB_PREFIX}.
 *
 * The sidecar write is either awaited ({@link BlobRecoveryMode#SYNCHRONOUS}) or performed on the side
 * ({@link BlobRecoveryMode#ASYNCHRONOUS}).
 */
public class ContentRecoveryMessageContentSaver implements MessageContentSaver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentRecoveryMessageContentSaver.class);

    private final MessageContentSaver delegate;
    private final BlobStore blobStore;
    private final BlobStoreDAO blobStoreDAO;
    private final BlobId.Factory blobIdFactory;
    private final BlobRecoveryMode recoveryMode;

    public ContentRecoveryMessageContentSaver(BlobStore blobStore, BlobStoreDAO blobStoreDAO,
                                              BlobId.Factory blobIdFactory, BlobRecoveryMode recoveryMode) {
        Preconditions.checkArgument(recoveryMode != BlobRecoveryMode.NONE,
            "%s does not handle %s: rely on the delegate alone instead", ContentRecoveryMessageContentSaver.class.getSimpleName(), BlobRecoveryMode.NONE);
        this.delegate = new DefaultMessageContentSaver(blobStore);
        this.blobStore = blobStore;
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdFactory = blobIdFactory;
        this.recoveryMode = recoveryMode;
    }

    @Override
    public Mono<Tuple2<BlobId, BlobId>> saveContent(byte[] headerBytes, ByteSource bodyByteSource) {
        return delegate.saveContent(headerBytes, bodyByteSource)
            .flatMap(pair -> saveRecovery(pair.getT1(), pair.getT2()).thenReturn(pair));
    }

    private Mono<Void> saveRecovery(BlobId headerId, BlobId bodyId) {
        return switch (recoveryMode) {
            case NONE -> Mono.empty();
            case SYNCHRONOUS -> writeRecoveryBlob(headerId, bodyId);
            case ASYNCHRONOUS -> Mono.fromRunnable(() ->
                writeRecoveryBlob(headerId, bodyId)
                    .subscribeOn(Schedulers.parallel())
                    .subscribe(ignored -> { }, e -> LOGGER.error("Failed to save recovery blob for header={} body={}", headerId.asString(), bodyId.asString(), e)));
        };
    }

    private Mono<Void> writeRecoveryBlob(BlobId headerId, BlobId bodyId) {
        BlobId recoveryBlobId = blobIdFactory.parse(BlobStoreDAO.RECOVERY_BLOB_PREFIX + headerId.asString());
        BlobStoreDAO.BytesBlob content = BlobStoreDAO.BytesBlob.of(bodyId.asString().getBytes(StandardCharsets.UTF_8));
        return Mono.from(blobStoreDAO.save(blobStore.getDefaultBucketName(), recoveryBlobId, content));
    }
}
