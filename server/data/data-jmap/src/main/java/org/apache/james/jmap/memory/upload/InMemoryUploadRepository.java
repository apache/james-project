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

package org.apache.james.jmap.memory.upload;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Upload;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.jmap.api.model.UploadNotFoundException;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.mailbox.model.ContentType;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.io.CountingInputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InMemoryUploadRepository implements UploadRepository {
    private static final BucketName UPLOAD_BUCKET = BucketName.of("jmap-uploads");

    private final Map<UploadId, ImmutablePair<Username, UploadMetaData>> uploadStore;
    private final BlobId.Factory blobIdFactory;
    private final BlobStoreDAO blobStoreDAO;
    private final BucketName bucketName;

    private final Clock clock;

    @Inject
    public InMemoryUploadRepository(BlobId.Factory blobIdFactory, BlobStoreDAO blobStoreDAO, Clock clock) {
        this.blobIdFactory = blobIdFactory;
        this.blobStoreDAO = blobStoreDAO;
        this.bucketName = UPLOAD_BUCKET;
        this.clock = clock;
        this.uploadStore = new HashMap<>();
    }

    @Override
    public Publisher<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(contentType);
        Preconditions.checkNotNull(user);
        BlobId blobId = blobIdFactory.of(UUID.randomUUID().toString());

        return Mono.fromCallable(() -> new CountingInputStream(data))
            .flatMap(dataAsByte -> Mono.from(blobStoreDAO.save(bucketName, blobId, dataAsByte))
                    .thenReturn(dataAsByte))
                .map(dataAsByte -> {
                    UploadId uploadId = UploadId.random();
                    Instant uploadDate = clock.instant();
                    uploadStore.put(uploadId, new ImmutablePair<>(user, UploadMetaData.from(uploadId, contentType, dataAsByte.getCount(), blobId, uploadDate)));
                    return UploadMetaData.from(uploadId, contentType, dataAsByte.getCount(), blobId, uploadDate);
                });
    }

    @Override
    public Publisher<Upload> retrieve(UploadId id, Username user) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(user);

        return Mono.justOrEmpty(uploadStore.get(id))
            .filter(pair -> user.equals(pair.left))
            .flatMap(userAndMetaData -> retrieveUpload(userAndMetaData.right))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
    }

    @Override
    public Publisher<Boolean> delete(UploadId id, Username user) {
        return Mono.justOrEmpty(uploadStore.get(id))
            .filter(pair -> user.equals(pair.left))
            .map(pair -> {
                uploadStore.remove(id);
                return true;
            })
            .defaultIfEmpty(false);
    }

    @Override
    public Publisher<UploadMetaData> listUploads(Username user) {
        return Flux.fromIterable(uploadStore.values())
            .filter(pair -> user.equals(pair.left))
            .map(pair -> pair.right);
    }

    @Override
    public Publisher<Void> deleteByUploadDateBefore(Duration expireDuration) {
        Instant expirationTime = clock.instant().minus(expireDuration);
        return Flux.fromIterable(List.copyOf(uploadStore.values()))
            .filter(pair -> pair.right.uploadDate().isBefore(expirationTime))
            .flatMap(pair -> Mono.from(blobStoreDAO.delete(bucketName, pair.right.blobId()))
                .then(Mono.fromRunnable(() -> uploadStore.remove(pair.right.uploadId()))))
            .then();
    }

    private Mono<Upload> retrieveUpload(UploadMetaData uploadMetaData) {
        return Mono.from(blobStoreDAO.readBytes(bucketName, uploadMetaData.blobId()))
            .map(content -> Upload.from(uploadMetaData, () -> new ByteArrayInputStream(content)));
    }
}
