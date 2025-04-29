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
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.common.io.CountingInputStream;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresUploadRepository implements UploadRepository {
    public static final BucketName UPLOAD_BUCKET = BucketName.of("jmap-uploads");
    private final BlobId.Factory blobIdFactory;
    private final BlobStoreDAO blobStoreDAO;
    private final Clock clock;
    private final PostgresUploadDAO.Factory uploadDAOFactory;
    private final PostgresUploadDAO byPassRLSUploadDAO;

    @Inject
    @Singleton
    public PostgresUploadRepository(BlobId.Factory blobIdFactory, BlobStoreDAO blobStoreDAO, Clock clock,
                                    PostgresUploadDAO.Factory uploadDAOFactory,
                                    PostgresUploadDAO byPassRLSUploadDAO) {
        this.blobIdFactory = blobIdFactory;
        this.blobStoreDAO = blobStoreDAO;
        this.clock = clock;
        this.uploadDAOFactory = uploadDAOFactory;
        this.byPassRLSUploadDAO = byPassRLSUploadDAO;
    }

    @Override
    public Mono<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        UploadId uploadId = generateId();
        BlobId blobId = blobIdFactory.of(uploadId.asString());
        PostgresUploadDAO uploadDAO = uploadDAOFactory.create(user.getDomainPart());

        return Mono.fromCallable(() -> new CountingInputStream(data))
            .flatMap(countingInputStream -> Mono.from(blobStoreDAO.save(UPLOAD_BUCKET, blobId, countingInputStream))
                    .thenReturn(countingInputStream))
                .map(countingInputStream -> UploadMetaData.from(uploadId, contentType, countingInputStream.getCount(), blobId, clock.instant()))
                .flatMap(uploadMetaData -> uploadDAO.insert(uploadMetaData, user));
    }

    @Override
    public Mono<Upload> retrieve(UploadId id, Username user) {
        return uploadDAOFactory.create(user.getDomainPart()).get(id, user)
            .flatMap(upload -> Mono.from(blobStoreDAO.readReactive(UPLOAD_BUCKET, upload.blobId()))
                .map(inputStream -> Upload.from(upload, () -> inputStream)))
            .switchIfEmpty(Mono.error(() -> new UploadNotFoundException(id)));
    }

    @Override
    public Mono<Boolean> delete(UploadId id, Username user) {
        return uploadDAOFactory.create(user.getDomainPart()).delete(id, user);
    }

    @Override
    public Flux<UploadMetaData> listUploads(Username user) {
        return uploadDAOFactory.create(user.getDomainPart()).list(user);
    }

    @Override
    public Mono<Void> deleteByUploadDateBefore(Duration expireDuration) {
        LocalDateTime expirationTime = INSTANT_TO_LOCAL_DATE_TIME.apply(clock.instant().minus(expireDuration));

        return Flux.from(byPassRLSUploadDAO.listByUploadDateBefore(expirationTime))
            .flatMap(uploadPair -> {
                Username username = uploadPair.getRight();
                UploadMetaData upload = uploadPair.getLeft();
                return Mono.from(blobStoreDAO.delete(UPLOAD_BUCKET, upload.blobId()))
                    .then(byPassRLSUploadDAO.delete(upload.uploadId(), username));
            }, DEFAULT_CONCURRENCY)
            .then();
    }

    private UploadId generateId() {
        return UploadId.from(UuidCreator.getTimeOrderedEpoch());
    }
}
