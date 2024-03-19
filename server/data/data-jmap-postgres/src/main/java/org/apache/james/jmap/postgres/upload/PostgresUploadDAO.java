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
import static org.apache.james.jmap.postgres.upload.PostgresUploadModule.PostgresUploadTable;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.mailbox.model.ContentType;
import org.jooq.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresUploadDAO {
    public static class Factory {
        private final BlobId.Factory blobIdFactory;
        private final PostgresExecutor.Factory executorFactory;

        @Inject
        @Singleton
        public Factory(BlobId.Factory blobIdFactory, PostgresExecutor.Factory executorFactory) {
            this.blobIdFactory = blobIdFactory;
            this.executorFactory = executorFactory;
        }

        public PostgresUploadDAO create(Optional<Domain> domain) {
            return new PostgresUploadDAO(executorFactory.create(domain), blobIdFactory);
        }
    }

    private final PostgresExecutor postgresExecutor;

    private final BlobId.Factory blobIdFactory;

    @Singleton
    @Inject
    public PostgresUploadDAO(@Named(PostgresExecutor.NON_RLS_INJECT) PostgresExecutor postgresExecutor, BlobId.Factory blobIdFactory) {
        this.postgresExecutor = postgresExecutor;
        this.blobIdFactory = blobIdFactory;
    }

    public Mono<Void> insert(UploadMetaData upload, Username user) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(PostgresUploadTable.TABLE_NAME)
            .set(PostgresUploadTable.ID, upload.uploadId().getId())
            .set(PostgresUploadTable.CONTENT_TYPE, upload.contentType().asString())
            .set(PostgresUploadTable.SIZE, upload.sizeAsLong())
            .set(PostgresUploadTable.BLOB_ID, upload.blobId().asString())
            .set(PostgresUploadTable.USER_NAME, user.asString())
            .set(PostgresUploadTable.UPLOAD_DATE, INSTANT_TO_LOCAL_DATE_TIME.apply(upload.uploadDate()))));
    }

    public Flux<UploadMetaData> list(Username user) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(PostgresUploadTable.TABLE_NAME)
                .where(PostgresUploadTable.USER_NAME.eq(user.asString()))))
            .map(this::uploadMetaDataFromRow);
    }

    public Mono<UploadMetaData> get(UploadId uploadId, Username user) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.selectFrom(PostgresUploadTable.TABLE_NAME)
                .where(PostgresUploadTable.ID.eq(uploadId.getId()))
                .and(PostgresUploadTable.USER_NAME.eq(user.asString()))))
            .map(this::uploadMetaDataFromRow);
    }

    public Mono<Boolean> delete(UploadId uploadId, Username user) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.deleteFrom(PostgresUploadTable.TABLE_NAME)
            .where(PostgresUploadTable.ID.eq(uploadId.getId()))
                .and(PostgresUploadTable.USER_NAME.eq(user.asString()))
                .returning(PostgresUploadTable.ID)))
            .hasElement();
    }

    public Flux<Pair<UploadMetaData, Username>> listByUploadDateBefore(LocalDateTime before) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(PostgresUploadTable.TABLE_NAME)
                .where(PostgresUploadTable.UPLOAD_DATE.lessThan(before))))
            .map(record -> Pair.of(uploadMetaDataFromRow(record), Username.of(record.get(PostgresUploadTable.USER_NAME))));
    }

    private UploadMetaData uploadMetaDataFromRow(Record record) {
        return UploadMetaData.from(
            UploadId.from(record.get(PostgresUploadTable.ID)),
            Optional.ofNullable(record.get(PostgresUploadTable.CONTENT_TYPE)).map(ContentType::of).orElse(null),
            record.get(PostgresUploadTable.SIZE),
            blobIdFactory.from(record.get(PostgresUploadTable.BLOB_ID)),
            PostgresCommons.LOCAL_DATE_TIME_INSTANT_FUNCTION.apply(record.get(PostgresUploadTable.UPLOAD_DATE, LocalDateTime.class)));
    }
}
