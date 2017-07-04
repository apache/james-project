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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.KEY;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.KEY_FOR_VERSION;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.TABLE_NAME;
import static org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable.VALUE;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import javax.inject.Inject;

public class CassandraSchemaVersionDAO {
    private final PreparedStatement readVersionStatement;
    private final PreparedStatement writeVersionStatement;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;

    @Inject
    public CassandraSchemaVersionDAO(Session session) {
        cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        readVersionStatement = prepareReadVersionStatement(session);
        writeVersionStatement = prepareWriteVersionStatement(session);
    }

    private PreparedStatement prepareReadVersionStatement(Session session) {
        return session.prepare(
            QueryBuilder.select(VALUE)
                .from(TABLE_NAME)
                .where(QueryBuilder.eq(KEY, KEY_FOR_VERSION)));
    }

    private PreparedStatement prepareWriteVersionStatement(Session session) {
        return session.prepare(
            QueryBuilder.insertInto(CassandraSchemaVersionTable.TABLE_NAME).value(KEY, KEY_FOR_VERSION)
                .value(VALUE, bindMarker(VALUE)));
    }

    public CompletableFuture<Optional<Integer>> getCurrentSchemaVersion() {
        return cassandraAsyncExecutor.executeSingleRow(readVersionStatement.bind())
            .thenApply(rowOptional ->
                rowOptional
                    .map(row -> row.getInt(VALUE)));
    }

    public CompletableFuture<Void> updateVersion(int newVersion) {
        return cassandraAsyncExecutor.executeVoid(
            writeVersionStatement.bind()
                .setInt(VALUE, newVersion));
    }
}

