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

package org.apache.james.jmap.postgres.upload;

import static org.apache.james.backends.postgres.PostgresCommons.INSTANT_TO_LOCAL_DATE_TIME;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Upload;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.jmap.api.model.UploadNotFoundException;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.mailbox.model.ContentType;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.common.io.CountingInputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresUploadRepository implements UploadRepository {
    public static final BucketName UPLOAD_BUCKET = BucketName.of("jmap-uploads");
    private final BlobStore blobStore;
    private final Clock clock;
    private final PostgresUploadDAO.Factory uploadDAOFactory;
    private final PostgresUploadDAO nonRLSUploadDAO;

    @Inject
    @Singleton
    public PostgresUploadRepository(BlobStore blobStore, Clock clock,
                                    PostgresUploadDAO.Factory uploadDAOFactory,
                                    PostgresUploadDAO nonRLSUploadDAO) {
        this.blobStore = blobStore;
        this.clock = clock;
        this.uploadDAOFactory = uploadDAOFactory;
        this.nonRLSUploadDAO = nonRLSUploadDAO;
    }

    @Override
    public Mono<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        UploadId uploadId = generateId();
        PostgresUploadDAO uploadDAO = uploadDAOFactory.create(user.getDomainPart());
        return Mono.fromCallable(() -> new CountingInputStream(data))
            .flatMap(countingInputStream -> Mono.from(blobStore.save(UPLOAD_BUCKET, countingInputStream, LOW_COST))
                .map(blobId -> UploadMetaData.from(uploadId, contentType, countingInputStream.getCount(), blobId, clock.instant()))
                .flatMap(uploadMetaData -> uploadDAO.insert(uploadMetaData, user)
                    .thenReturn(uploadMetaData)));
    }

    @Override
    public Mono<Upload> retrieve(UploadId id, Username user) {
        return uploadDAOFactory.create(user.getDomainPart()).get(id, user)
            .flatMap(upload -> Mono.from(blobStore.readReactive(UPLOAD_BUCKET, upload.blobId(), LOW_COST))
                .map(inputStream -> Upload.from(upload, () -> inputStream)))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
    }

    @Override
    public Mono<Void> delete(UploadId id, Username user) {
        return uploadDAOFactory.create(user.getDomainPart()).delete(id, user);
    }

    @Override
    public Flux<UploadMetaData> listUploads(Username user) {
        return uploadDAOFactory.create(user.getDomainPart()).list(user);
    }

    @Override
    public Mono<Void> deleteByUploadDateBefore(Duration expireDuration) {
        LocalDateTime expirationTime = INSTANT_TO_LOCAL_DATE_TIME.apply(clock.instant().minus(expireDuration));

        return Flux.from(nonRLSUploadDAO.listByUploadDateBefore(expirationTime))
            .flatMap(uploadPair -> {
                Username username = uploadPair.getRight();
                UploadMetaData upload = uploadPair.getLeft();
                return Mono.from(blobStore.delete(UPLOAD_BUCKET, upload.blobId()))
                    .then(nonRLSUploadDAO.delete(upload.uploadId(), username));
            }, DEFAULT_CONCURRENCY)
            .then();
    }

    private UploadId generateId() {
        return UploadId.from(UuidCreator.getTimeOrderedEpoch());
    }
}
