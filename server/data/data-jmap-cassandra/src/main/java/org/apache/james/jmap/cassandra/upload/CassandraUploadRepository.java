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
package org.apache.james.jmap.cassandra.upload;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Upload;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.jmap.api.model.UploadNotFoundException;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.mailbox.model.ContentType;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.io.CountingInputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraUploadRepository implements UploadRepository {
    public static final BucketName UPLOAD_BUCKET = BucketName.of("jmap-uploads");
    private final UploadDAO uploadDAO;
    private final BlobStore blobStore;
    private final Clock clock;

    @Inject
    public CassandraUploadRepository(UploadDAO uploadDAO, BlobStore blobStore, Clock clock) {
        this.uploadDAO = uploadDAO;
        this.blobStore = blobStore;
        this.clock = clock;
    }

    @Override
    public Mono<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        UploadId uploadId = generateId();

        return Mono.fromCallable(() -> new CountingInputStream(data))
            .flatMap(countingInputStream -> Mono.from(blobStore.save(UPLOAD_BUCKET, countingInputStream, LOW_COST))
                .map(blobId -> new UploadDAO.UploadRepresentation(uploadId, blobId, contentType, countingInputStream.getCount(), user,
                    clock.instant().truncatedTo(ChronoUnit.MILLIS)))
                .flatMap(upload -> uploadDAO.save(upload)
                    .thenReturn(upload.toUploadMetaData())));
    }

    @Override
    public Mono<Upload> retrieve(UploadId id, Username user) {
        return uploadDAO.retrieve(user, id)
            .flatMap(upload -> Mono.from(blobStore.readReactive(UPLOAD_BUCKET, upload.getBlobId(), LOW_COST))
                .map(inputStream -> Upload.from(upload.toUploadMetaData(), () -> inputStream)))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
    }

    @Override
    public Mono<Void> delete(UploadId id, Username user) {
        return uploadDAO.delete(user, id);
    }

    @Override
    public Flux<UploadMetaData> listUploads(Username user) {
        return uploadDAO.list(user)
            .map(UploadDAO.UploadRepresentation::toUploadMetaData);
    }

    @Override
    public Mono<Void> deleteByUploadDateBefore(Duration expireDuration) {
        Instant expirationTime = clock.instant().minus(expireDuration);
        return Flux.from(uploadDAO.all())
            .filter(upload -> upload.getUploadDate().isBefore(expirationTime))
            .flatMap(upload -> Mono.from(blobStore.delete(UPLOAD_BUCKET, upload.getBlobId()))
                .then(uploadDAO.delete(upload.getUser(), upload.getId())), DEFAULT_CONCURRENCY)
            .then();
    }

    private UploadId generateId() {
        return UploadId.from(Uuids.timeBased());
    }
}
