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

package org.apache.james.backends.cassandra.init;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.util.FluentFutureStream;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraTableManager {

    private final Session session;
    private final CassandraModule module;

    @Inject
    public CassandraTableManager(CassandraModule module, Session session) {
        this.session = session;
        this.module = module;
    }

    public CassandraTableManager ensureAllTables() {
        KeyspaceMetadata keyspaceMetadata = session.getCluster()
            .getMetadata()
            .getKeyspace(session.getLoggedKeyspace());

        module.moduleTables()
            .stream()
            .filter(table -> keyspaceMetadata.getTable(table.getName()) == null)
            .forEach(table -> session.execute(table.getCreateStatement()));

        return this;
    }

    public void clearAllTables() {
        CassandraAsyncExecutor executor = new CassandraAsyncExecutor(session);
        FluentFutureStream.of(
            module.moduleTables()
                .stream()
                .map(CassandraTable::getName)
                .map(name -> truncate(executor, name)))
            .join();
    }

    private CompletableFuture<?> truncate(CassandraAsyncExecutor executor, String name) {
        return executor.execute(
            QueryBuilder.select().from(name).limit(1))
            .thenCompose(resultSet -> truncateIfNeeded(executor, name, resultSet));
    }

    private CompletionStage<ResultSet> truncateIfNeeded(CassandraAsyncExecutor executor, String name, ResultSet resultSet) {
        if (resultSet.isExhausted()) {
            return CompletableFuture.completedFuture(null);
        }
        return executor.execute(QueryBuilder.truncate(name));
    }
}
