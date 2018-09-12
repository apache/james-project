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

package org.apache.james.backends.cassandra.utils;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

import net.javacrumbs.futureconverter.java8guava.FutureConverter;

public class CassandraAsyncExecutor {

    private final Session session;

    @Inject
    public CassandraAsyncExecutor(Session session) {
        this.session = session;
    }

    public CompletableFuture<ResultSet> execute(Statement statement) {
        return FutureConverter.toCompletableFuture(session.executeAsync(statement));
    }


    public CompletableFuture<Boolean> executeReturnApplied(Statement statement) {
        return FutureConverter.toCompletableFuture(session.executeAsync(statement))
            .thenApply(ResultSet::one)
            .thenApply(row -> row.getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED));
    }

    public CompletableFuture<Void> executeVoid(Statement statement) {
        return FutureConverter.toCompletableFuture(session.executeAsync(statement))
            .thenAccept(result -> { });
    }

    public CompletableFuture<Optional<Row>> executeSingleRow(Statement statement) {
        return execute(statement)
            .thenApply(ResultSet::one)
            .thenApply(Optional::ofNullable);
    }

    public CompletableFuture<Boolean> executeReturnExists(Statement statement) {
        return executeSingleRow(statement)
            .thenApply(Optional::isPresent);
    }
}
