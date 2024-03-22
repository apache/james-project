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

package org.apache.james.blob.cassandra.cache;


import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.DATA;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.TABLE_NAME;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.TTL_FOR_ROW;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.ID;

import java.nio.ByteBuffer;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.cassandra.init.configuration.InjectionNames;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class CassandraBlobStoreCache implements BlobStoreCache {

    public static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobStoreCache.class);

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement deleteStatement;

    private final int timeToLive;
    private final DriverExecutionProfile cachingProfile;

    @Inject
    @VisibleForTesting
    CassandraBlobStoreCache(@Named(InjectionNames.CACHE) CqlSession session,
                            CassandraCacheConfiguration cacheConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.timeToLive = Math.toIntExact(cacheConfiguration.getTtl().getSeconds());

        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(DATA, bindMarker(DATA))
            .usingTtl(bindMarker(TTL_FOR_ROW))
            .build());

        this.selectStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(DATA)
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());

        this.deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(ID).isEqualTo(bindMarker(ID))
            .build());

        cachingProfile = JamesExecutionProfiles.getCachingProfile(session);
    }

    @Override
    public Mono<Void> cache(BlobId blobId, byte[] bytes) {
        return save(blobId, toByteBuffer(bytes));
    }

    @Override
    public Mono<byte[]> read(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(
                selectStatement.bind()
                    .setString(ID, blobId.asString())
                    .setExecutionProfile(cachingProfile))
            .map(this::toByteArray)
            .onErrorResume(e -> {
                LOGGER.warn("Fail reading blob store cache", e);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Void> remove(BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteStatement.bind()
                .setString(ID, blobId.asString())
                .setExecutionProfile(cachingProfile));
    }

    private Mono<Void> save(BlobId blobId, ByteBuffer data) {
        return cassandraAsyncExecutor.executeVoid(
                insertStatement.bind()
                    .setString(ID, blobId.asString())
                    .setByteBuffer(DATA, data)
                    .setInt(TTL_FOR_ROW, timeToLive)
                    .setExecutionProfile(cachingProfile))
            .onErrorResume(e -> {
                LOGGER.warn("Failed saving {} in blob store cache", blobId, e);
                return Mono.empty();
            });
    }

    private ByteBuffer toByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes, 0, bytes.length);
    }

    private byte[] toByteArray(Row row) {
        ByteBuffer byteBuffer = row.get(0, TypeCodecs.BLOB);
        assert byteBuffer != null;
        byte[] data = new byte[byteBuffer.remaining()];
        byteBuffer.get(data);
        return data;
    }
}
