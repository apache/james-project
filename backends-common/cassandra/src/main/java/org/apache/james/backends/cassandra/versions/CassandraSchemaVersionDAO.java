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

package org.apache.james.backends.cassandra.versions;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.truncate;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.KEY;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.TABLE_NAME;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.VALUE;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class CassandraSchemaVersionDAO {
    private final PreparedStatement readVersionStatement;
    private final PreparedStatement writeVersionStatement;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;

    @Inject
    public CassandraSchemaVersionDAO(CqlSession session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readVersionStatement = prepareReadVersionStatement(session);
        writeVersionStatement = prepareWriteVersionStatement(session);
    }

    private PreparedStatement prepareReadVersionStatement(CqlSession session) {
        return session.prepare(
            selectFrom(TABLE_NAME)
                .column(VALUE)
                .build());
    }

    private PreparedStatement prepareWriteVersionStatement(CqlSession session) {
        return session.prepare(
            insertInto(CassandraSchemaVersionTable.TABLE_NAME)
                .value(KEY, bindMarker(KEY))
                .value(VALUE, bindMarker(VALUE))
                .build());
    }

    public Mono<Optional<SchemaVersion>> getCurrentSchemaVersion() {
        return cassandraAsyncExecutor.executeRows(readVersionStatement.bind())
            .map(row -> row.getInt(VALUE))
            .reduce(Math::max)
            .map(SchemaVersion::new)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }

    public Mono<Void> updateVersion(SchemaVersion newVersion) {
        return cassandraAsyncExecutor.executeVoid(
            writeVersionStatement.bind()
                .setUuid(KEY, Uuids.timeBased())
                .setInt(VALUE, newVersion.getValue()));
    }

    @VisibleForTesting
    public Mono<Void> truncateVersion() {
        return cassandraAsyncExecutor.executeVoid(truncate(TABLE_NAME).build());
    }
}

