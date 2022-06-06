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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.upload.UploadModule.BLOB_ID;
import static org.apache.james.jmap.cassandra.upload.UploadModule.BUCKET_ID;
import static org.apache.james.jmap.cassandra.upload.UploadModule.CONTENT_TYPE;
import static org.apache.james.jmap.cassandra.upload.UploadModule.ID;
import static org.apache.james.jmap.cassandra.upload.UploadModule.SIZE;
import static org.apache.james.jmap.cassandra.upload.UploadModule.TABLE_NAME;
import static org.apache.james.jmap.cassandra.upload.UploadModule.USER;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.mailbox.model.ContentType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class UploadDAO {
    public static class UploadRepresentation {
        private final UploadId id;
        private final BucketName bucketName;
        private final BlobId blobId;
        private final ContentType contentType;
        private final long size;
        private final Username user;

        public UploadRepresentation(UploadId id, BucketName bucketName, BlobId blobId, ContentType contentType, long size, Username user) {
            this.user = user;
            Preconditions.checkArgument(size >= 0, "Size must be strictly positive");
            this.id = id;
            this.bucketName = bucketName;
            this.blobId = blobId;
            this.contentType = contentType;
            this.size = size;
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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UploadRepresentation) {
                UploadRepresentation other = (UploadRepresentation) obj;
                return Objects.equal(id, other.id)
                    && Objects.equal(bucketName, other.bucketName)
                    && Objects.equal(user, other.user)
                    && Objects.equal(blobId, other.blobId)
                    && Objects.equal(contentType, other.contentType)
                    && Objects.equal(size, other.size);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, bucketName, blobId, contentType, size, user);
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
                .add("size", size)
                .toString();
        }
    }

    private final CassandraAsyncExecutor executor;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement insert;
    private final PreparedStatement selectOne;

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
            .usingTtl((int) configuration.getUploadTtlDuration().getSeconds())
            .build());
        this.selectOne = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(ID)
            .isEqualTo(bindMarker(ID))
            .build());
    }

    public Mono<Void> save(UploadRepresentation uploadRepresentation) {
        return executor.executeVoid(insert.bind()
            .setUuid(ID, uploadRepresentation.getId().getId())
            .setString(BUCKET_ID, uploadRepresentation.getBucketName().asString())
            .setString(BLOB_ID, uploadRepresentation.getBlobId().asString())
            .setLong(SIZE, uploadRepresentation.getSize())
            .setString(USER, uploadRepresentation.getUser().asString())
            .setString(CONTENT_TYPE, uploadRepresentation.getContentType().asString()));
    }

    public Mono<UploadRepresentation> retrieve(UploadId id) {
        return executor.executeSingleRow(selectOne.bind()
            .setUuid(ID, id.getId()))
            .map(row -> new UploadRepresentation(id,
                BucketName.of(row.getString(BUCKET_ID)),
                blobIdFactory.from(row.getString(BLOB_ID)),
                ContentType.of(row.getString(CONTENT_TYPE)),
                row.getLong(SIZE),
                Username.of(row.getString(USER))));
    }
}
