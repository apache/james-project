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

package org.apache.james.sieve.cassandra;


import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.sieve.cassandra.tables.CassandraSieveClusterQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveSpaceTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Mono;

public class CassandraSieveQuotaDAOV1 implements CassandraSieveQuotaDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement selectClusterQuotaStatement;
    private final PreparedStatement selectSpaceUsedByUserStatement;
    private final PreparedStatement selectUserQuotaStatement;
    private final PreparedStatement updateClusterQuotaStatement;
    private final PreparedStatement updateUserQuotaStatement;
    private final PreparedStatement updateSpaceUsedStatement;
    private final PreparedStatement deleteClusterQuotaStatement;
    private final PreparedStatement deleteUserQuotaStatement;

    @Inject
    public CassandraSieveQuotaDAOV1(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        selectClusterQuotaStatement = session.prepare(
            selectFrom(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .column(CassandraSieveClusterQuotaTable.VALUE)
                .whereColumn(CassandraSieveClusterQuotaTable.NAME).isEqualTo(bindMarker(CassandraSieveClusterQuotaTable.NAME))
                .build());

        selectSpaceUsedByUserStatement = session.prepare(
            selectFrom(CassandraSieveSpaceTable.TABLE_NAME)
                .column(CassandraSieveSpaceTable.SPACE_USED)
                .whereColumn(CassandraSieveSpaceTable.USER_NAME).isEqualTo(bindMarker(CassandraSieveSpaceTable.USER_NAME))
                .build());

        selectUserQuotaStatement = session.prepare(
            selectFrom(CassandraSieveQuotaTable.TABLE_NAME)
                .column(CassandraSieveQuotaTable.QUOTA)
                .whereColumn(CassandraSieveQuotaTable.USER_NAME).isEqualTo(bindMarker(CassandraSieveQuotaTable.USER_NAME))
                .build());

        updateClusterQuotaStatement = session.prepare(
            update(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .setColumn(CassandraSieveClusterQuotaTable.VALUE, bindMarker(CassandraSieveClusterQuotaTable.VALUE))
                .whereColumn(CassandraSieveClusterQuotaTable.NAME).isEqualTo(bindMarker(CassandraSieveClusterQuotaTable.NAME))
                .build());

        updateSpaceUsedStatement = session.prepare(
            update(CassandraSieveSpaceTable.TABLE_NAME)
                .increment(CassandraSieveSpaceTable.SPACE_USED, bindMarker(CassandraSieveSpaceTable.SPACE_USED))
                .whereColumn(CassandraSieveSpaceTable.USER_NAME).isEqualTo(bindMarker(CassandraSieveSpaceTable.USER_NAME))
                .build());

        updateUserQuotaStatement = session.prepare(
            update(CassandraSieveQuotaTable.TABLE_NAME)
                .setColumn(CassandraSieveQuotaTable.QUOTA, bindMarker(CassandraSieveQuotaTable.QUOTA))
                .whereColumn(CassandraSieveQuotaTable.USER_NAME).isEqualTo(bindMarker(CassandraSieveQuotaTable.USER_NAME))
                .build());

        deleteClusterQuotaStatement = session.prepare(
            deleteFrom(CassandraSieveClusterQuotaTable.TABLE_NAME)
                .whereColumn(CassandraSieveClusterQuotaTable.NAME).isEqualTo(bindMarker(CassandraSieveClusterQuotaTable.NAME))
                .build());

        deleteUserQuotaStatement = session.prepare(
            deleteFrom(CassandraSieveQuotaTable.TABLE_NAME)
                .whereColumn(CassandraSieveQuotaTable.USER_NAME).isEqualTo(bindMarker(CassandraSieveQuotaTable.USER_NAME))
                .build());
    }

    @Override
    public Mono<Long> spaceUsedBy(Username username) {
        return cassandraAsyncExecutor.executeSingleRowOptional(
                selectSpaceUsedByUserStatement.bind()
                    .setString(CassandraSieveSpaceTable.USER_NAME, username.asString()))
            .map(optional -> optional.map(row -> row.getLong(CassandraSieveSpaceTable.SPACE_USED))
                .orElse(0L));
    }

    @Override
    public Mono<Void> updateSpaceUsed(Username username, long spaceUsed) {
        return cassandraAsyncExecutor.executeVoid(
            updateSpaceUsedStatement.bind()
                .setLong(CassandraSieveSpaceTable.SPACE_USED, spaceUsed)
                .setString(CassandraSieveSpaceTable.USER_NAME, username.asString()));
    }

    @Override
    public Mono<Optional<QuotaSizeLimit>> getQuota() {
        return cassandraAsyncExecutor.executeSingleRowOptional(
                selectClusterQuotaStatement.bind()
                    .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
            .map(optional -> optional.map(row ->
                QuotaSizeLimit.size(row.getLong(CassandraSieveClusterQuotaTable.VALUE))));
    }

    @Override
    public Mono<Void> setQuota(QuotaSizeLimit quota) {
        return cassandraAsyncExecutor.executeVoid(
            updateClusterQuotaStatement.bind()
                .setLong(CassandraSieveClusterQuotaTable.VALUE, quota.asLong())
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME));
    }

    @Override
    public Mono<Void> removeQuota() {
        return cassandraAsyncExecutor.executeVoid(
            deleteClusterQuotaStatement.bind()
                .setString(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME));
    }

    @Override
    public Mono<Optional<QuotaSizeLimit>> getQuota(Username username) {
        return cassandraAsyncExecutor.executeSingleRowOptional(
                selectUserQuotaStatement.bind()
                    .setString(CassandraSieveQuotaTable.USER_NAME, username.asString()))
            .map(optional -> optional.map(row ->
                QuotaSizeLimit.size(row.getLong(CassandraSieveQuotaTable.QUOTA))));
    }

    @Override
    public Mono<Void> setQuota(Username username, QuotaSizeLimit quota) {
        return cassandraAsyncExecutor.executeVoid(
            updateUserQuotaStatement.bind()
                .setLong(CassandraSieveQuotaTable.QUOTA, quota.asLong())
                .setString(CassandraSieveQuotaTable.USER_NAME, username.asString()));
    }

    @Override
    public Mono<Void> removeQuota(Username username) {
        return cassandraAsyncExecutor.executeVoid(
            deleteUserQuotaStatement.bind()
                .setString(CassandraSieveQuotaTable.USER_NAME, username.asString()));
    }
}
