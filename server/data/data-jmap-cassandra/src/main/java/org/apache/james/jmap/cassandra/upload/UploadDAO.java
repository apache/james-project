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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.upload.UploadModule.BLOB_ID;
import static org.apache.james.jmap.cassandra.upload.UploadModule.BUCKET_ID;
import static org.apache.james.jmap.cassandra.upload.UploadModule.CONTENT_TYPE;
import static org.apache.james.jmap.cassandra.upload.UploadModule.ID;
import static org.apache.james.jmap.cassandra.upload.UploadModule.SIZE;
import static org.apache.james.jmap.cassandra.upload.UploadModule.TABLE_NAME;
import static org.apache.james.jmap.cassandra.upload.UploadModule.UPLOAD_DATE;
import static org.apache.james.jmap.cassandra.upload.UploadModule.USER;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.mailbox.model.ContentType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class UploadDAO {
    public static class UploadRepresentation {
        private final UploadId id;
        private final BucketName bucketName;
        private final BlobId blobId;
        private final ContentType contentType;
        private final long size;
        private final Username user;
        private final Instant uploadDate;

        public UploadRepresentation(UploadId id, BucketName bucketName, BlobId blobId, ContentType contentType, long size, Username user, Instant uploadDate) {
            this.user = user;
            Preconditions.checkArgument(size >= 0, "Size must be strictly positive");
            this.id = id;
            this.bucketName = bucketName;
            this.blobId = blobId;
            this.contentType = contentType;
            this.size = size;
            this.uploadDate = uploadDate;
        }

        public UploadId getId() {
            return id;
        }

        public BucketName getBucketName() {
            return bucketName;
        }

        public BlobId getBlobId() {
            return blobId;
        }

        public ContentType getContentType() {
            return contentType;
        }

        public long getSize() {
            return size;
        }

        public Username getUser() {
            return user;
        }

        public Instant getUploadDate() {
            return uploadDate;
        }

        public UploadMetaData toUploadMetaData() {
            return UploadMetaData.from(id, contentType, size, blobId, uploadDate);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UploadRepresentation) {
                UploadRepresentation other = (UploadRepresentation) obj;
                return Objects.equal(id, other.id)
                    && Objects.equal(bucketName, other.bucketName)
                    && Objects.equal(user, other.user)
                    && Objects.equal(blobId, other.blobId)
                    && Objects.equal(contentType, other.contentType)
                    && Objects.equal(uploadDate, other.uploadDate)
                    && Objects.equal(size, other.size);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, bucketName, blobId, contentType, size, user, uploadDate);
        }

        @Override
        public String toString() {
            return MoreObjects
                .toStringHelper(this)
                .add("id", id)
                .add("bucketName", bucketName)
                .add("blobId", blobId)
                .add("contentType", contentType)
                .add("user", user)
                .add("user", user)
                .add("uploadDate", uploadDate)
                .toString();
        }
    }

    public static final Instant UPLOAD_DATE_FALLBACK = Instant.EPOCH; // 1970-01-01T00:00:00Z
    private final CassandraAsyncExecutor executor;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement selectOne;
    private final PreparedStatement delete;

    private final PreparedStatement list;

    @Inject
    public UploadDAO(CqlSession session, BlobId.Factory blobIdFactory, UploadConfiguration configuration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.insert = session.prepare(insertInto(TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(BUCKET_ID, bindMarker(BUCKET_ID))
            .value(BLOB_ID, bindMarker(BLOB_ID))
            .value(SIZE, bindMarker(SIZE))
            .value(USER, bindMarker(USER))
            .value(CONTENT_TYPE, bindMarker(CONTENT_TYPE))
            .value(UPLOAD_DATE, bindMarker(UPLOAD_DATE))
            .usingTtl((int) configuration.getUploadTtlDuration().getSeconds())
            .build());

        this.list = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());

        this.selectOne = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());

        this.delete = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());
    }

    public Mono<Void> save(UploadRepresentation uploadRepresentation) {
        return executor.executeVoid(insert.bind()
            .setString(USER, uploadRepresentation.getUser().asString())
            .setUuid(ID, uploadRepresentation.getId().getId())
            .setString(BUCKET_ID, uploadRepresentation.getBucketName().asString())
            .setString(BLOB_ID, uploadRepresentation.getBlobId().asString())
            .setLong(SIZE, uploadRepresentation.getSize())
            .setInstant(UPLOAD_DATE, uploadRepresentation.getUploadDate())
            .setString(CONTENT_TYPE, uploadRepresentation.getContentType().asString()));
    }

    public Mono<UploadRepresentation> retrieve(Username username, UploadId id) {
        return executor.executeSingleRow(selectOne.bind()
                .setString(USER, username.asString())
                .setUuid(ID, id.getId()))
            .map(rowToUploadRepresentation());
    }

    public Flux<UploadRepresentation> list(Username username) {
        return Flux.from(executor.executeRows(list.bind()
                .setString(USER, username.asString())))
            .map(rowToUploadRepresentation());
    }

    public Mono<Void> delete(Username username, UploadId uploadId) {
        return executor.executeVoid(delete.bind()
            .setString(USER, username.asString())
            .setUuid(ID, uploadId.getId()));
    }

    private Function<Row, UploadRepresentation> rowToUploadRepresentation() {
        return row -> new UploadRepresentation(UploadId.from(row.getUuid(ID)),
            BucketName.of(row.getString(BUCKET_ID)),
            blobIdFactory.from(row.getString(BLOB_ID)),
            ContentType.of(row.getString(CONTENT_TYPE)),
            row.getLong(SIZE),
            Username.of(row.getString(USER)),
            Optional.ofNullable(row.getInstant(UPLOAD_DATE)).orElse(UPLOAD_DATE_FALLBACK));
    }
}
