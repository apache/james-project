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

package org.apache.james.mailbox.cassandra.object.compaction;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

/** Run-specific reference index. An abandoned table can be dropped by its run ID once the run has stopped. */
public class CassandraBlobReferenceIndex {
    static final String TABLE_PREFIX = "blob_ref_";
    private static final int PAGE_SIZE = 500;
    private final CqlSession session;
    private final CassandraAsyncExecutor executor;
    private final int pageSize;

    @Inject
    public CassandraBlobReferenceIndex(CqlSession session) {
        this(session, PAGE_SIZE);
    }

    @VisibleForTesting
    CassandraBlobReferenceIndex(CqlSession session, int pageSize) {
        this.session = session;
        this.executor = new CassandraAsyncExecutor(session);
        this.pageSize = pageSize;
    }

    static String tableName(RunId runId) {
        return TABLE_PREFIX + runId.value().toString().replace("-", "");
    }

    Mono<CassandraBlobReferenceIndexDAO> create(RunId runId) {
        String table = tableName(runId);
        return changeSchema("CREATE TABLE IF NOT EXISTS " + table
            + " (blob_id text, reference_type text, reference_id uuid, PRIMARY KEY (blob_id, reference_type, reference_id))")
            .then(Mono.defer(() -> prepare(table)));
    }

    private Mono<CassandraBlobReferenceIndexDAO> prepare(String table) {
        return Mono.fromCompletionStage(() -> session.prepareAsync(
                "INSERT INTO " + table + " (blob_id, reference_type, reference_id) VALUES (?, ?, ?)"))
            .flatMap(insert -> Mono.fromCompletionStage(() -> session.prepareAsync(
                    "SELECT reference_type, reference_id FROM " + table + " WHERE blob_id = ?"))
                .map(select -> new CassandraBlobReferenceIndexDAO(executor, insert, select, pageSize)));
    }

    public Mono<Void> cleanup(RunId runId) {
        return changeSchema("DROP TABLE IF EXISTS " + tableName(runId));
    }

    private Mono<Void> changeSchema(String query) {
        return executor.executeVoid(SimpleStatement.newInstance(query)
                .setExecutionProfile(JamesExecutionProfiles.getTableCreationProfile(session)))
            .then(Mono.fromCompletionStage(session::checkSchemaAgreementAsync))
            .flatMap(agreed -> agreed ? Mono.empty()
                : Mono.error(new IllegalStateException("No Cassandra schema agreement after " + query)));
    }
}
