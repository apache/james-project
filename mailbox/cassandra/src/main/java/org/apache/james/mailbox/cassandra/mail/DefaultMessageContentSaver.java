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
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Writes the headers and the body as two distinct blobs, without any recovery information.
 */
public class DefaultMessageContentSaver implements MessageContentSaver {
    private final BlobStore blobStore;

    @Inject
    public DefaultMessageContentSaver(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    @Override
    public Mono<Tuple2<BlobId, BlobId>> saveContent(byte[] headerBytes, ByteSource bodyByteSource) {
        Mono<BlobId> headerFuture = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), headerBytes, SIZE_BASED));
        Mono<BlobId> bodyFuture = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), bodyByteSource, LOW_COST));

        return headerFuture.zipWith(bodyFuture);
    }
}
