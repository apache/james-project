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

import static com.datastax.driver.core.ConsistencyLevel.ALL;
import static com.datastax.driver.core.ConsistencyLevel.ONE;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.DATA;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.TABLE_NAME;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.TTL_FOR_ROW;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.ID;

import java.nio.ByteBuffer;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.cassandra.init.configuration.InjectionNames;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class CassandraBlobStoreCache implements BlobStoreCache {

    public static final Logger LOGGER = LoggerFactory.getLogger(CassandraBlobStoreCache.class);

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement deleteStatement;

    private final int readTimeOutFromDataBase;
    private final int timeToLive;

    @Inject
    @VisibleForTesting
    CassandraBlobStoreCache(@Named(InjectionNames.CACHE) Session session,
                            CassandraCacheConfiguration cacheConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.insertStatement = prepareInsert(session);
        this.selectStatement = prepareSelect(session);
        this.deleteStatement = prepareDelete(session);

        this.readTimeOutFromDataBase = Math.toIntExact(cacheConfiguration.getReadTimeOut().toMillis());
        this.timeToLive = Math.toIntExact(cacheConfiguration.getTtl().getSeconds());
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
                    .setConsistencyLevel(ONE)
                    .setReadTimeoutMillis(readTimeOutFromDataBase))
            .map(this::toByteArray);
    }

    @Override
    public Mono<Void> remove(BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteStatement.bind()
                .setString(ID, blobId.asString())
                .setConsistencyLevel(ALL));
    }

    private Mono<Void> save(BlobId blobId, ByteBuffer data) {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setString(ID, blobId.asString())
                .setBytes(DATA, data)
                .setInt(TTL_FOR_ROW, timeToLive)
                .setConsistencyLevel(ONE))
            .onErrorResume(e -> {
                LOGGER.warn("Failed saving {} in blob store cache", blobId, e);
                return Mono.empty();
            });
    }

    private ByteBuffer toByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes, 0, bytes.length);
    }

    private byte[] toByteArray(Row row) {
        ByteBuffer byteBuffer = row.getBytes(DATA);
        byte[] data = new byte[byteBuffer.remaining()];
        byteBuffer.get(data);
        return data;
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(
            delete()
                .from(TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(
            select()
                .from(TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(
            insertInto(TABLE_NAME)
                .value(ID, bindMarker(ID))
                .value(DATA, bindMarker(DATA))
                .using(ttl(bindMarker(TTL_FOR_ROW)))
        );
    }
}
