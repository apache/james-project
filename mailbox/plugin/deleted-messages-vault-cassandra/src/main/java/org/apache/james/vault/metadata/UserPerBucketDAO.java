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

package org.apache.james.vault.metadata;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.UserPerBucketTable.BUCKET_NAME;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.UserPerBucketTable.TABLE;
import static org.apache.james.vault.metadata.DeletedMessageMetadataModule.UserPerBucketTable.USER;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class UserPerBucketDAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement addStatement;
    private final PreparedStatement removeStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement listBucketsStatement;

    @Inject
    UserPerBucketDAO(CqlSession session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        addStatement = prepareAddUser(session);
        removeStatement = prepareRemoveBucket(session);
        listStatement = prepareListUser(session);
        listBucketsStatement = prepareListBuckets(session);
    }

    private PreparedStatement prepareAddUser(CqlSession session) {
        return session.prepare(insertInto(TABLE)
            .value(BUCKET_NAME, bindMarker(BUCKET_NAME))
            .value(USER, bindMarker(USER))
            .build());
    }

    private PreparedStatement prepareRemoveBucket(CqlSession session) {
        return session.prepare(deleteFrom(TABLE)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .build());
    }

    private PreparedStatement prepareListUser(CqlSession session) {
        return session.prepare(selectFrom(TABLE)
            .column(USER)
            .whereColumn(BUCKET_NAME).isEqualTo(bindMarker(BUCKET_NAME))
            .build());
    }

    private PreparedStatement prepareListBuckets(CqlSession session) {
        return session.prepare(selectFrom(TABLE).column(BUCKET_NAME).perPartitionLimit(1).build());
    }

    Flux<Username> retrieveUsers(BucketName bucketName) {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString()))
            .map(row -> row.getString(USER))
            .map(Username::of);
    }

    Flux<BucketName> retrieveBuckets() {
        return cassandraAsyncExecutor.executeRows(listBucketsStatement.bind())
            .map(row -> row.getString(BUCKET_NAME))
            .map(BucketName::of);
    }

    Mono<Void> addUser(BucketName bucketName, Username username) {
        return cassandraAsyncExecutor.executeVoid(addStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString())
            .setString(USER, username.asString()));
    }

    Mono<Void> deleteBucket(BucketName bucketName) {
        return cassandraAsyncExecutor.executeVoid(removeStatement.bind()
            .setString(BUCKET_NAME, bucketName.asString()));
    }
}
