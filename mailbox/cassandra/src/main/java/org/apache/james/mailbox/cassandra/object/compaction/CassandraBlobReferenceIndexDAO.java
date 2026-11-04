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
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.cassandra.object.compaction.CassandraBlobReferenceDAO.Reference;
import org.apache.james.mailbox.cassandra.object.compaction.CassandraBlobReferenceDAO.Type;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Run-specific reference index. An abandoned table can be dropped by its run ID once the run has stopped. */
public class CassandraBlobReferenceIndexDAO {
    static final String TABLE_PREFIX = "blob_ref_";
    private static final int PAGE_SIZE = 500;
    private final CqlSession session;
    private final CassandraAsyncExecutor executor;
    private final int pageSize;

    @Inject
    public CassandraBlobReferenceIndexDAO(CqlSession session) {
        this(session, PAGE_SIZE);
    }

    @VisibleForTesting
    CassandraBlobReferenceIndexDAO(CqlSession session, int pageSize) {
        this.session = session;
        this.executor = new CassandraAsyncExecutor(session);
        this.pageSize = pageSize;
    }

    static String tableName(RunId runId) {
        return TABLE_PREFIX + runId.value().toString().replace("-", "");
    }

    Mono<Void> create(RunId runId) {
        return changeSchema("CREATE TABLE IF NOT EXISTS " + tableName(runId)
            + " (blob_id text, reference_type text, reference_id uuid, PRIMARY KEY (blob_id, reference_type, reference_id))");
    }

    Mono<Void> store(RunId runId, Reference reference) {
        return executor.executeVoid(SimpleStatement.newInstance(
            "INSERT INTO " + tableName(runId) + " (blob_id, reference_type, reference_id) VALUES (?, ?, ?)",
            reference.blobId().asString(), reference.type().name(), reference.referenceId()));
    }

    Flux<Reference> retrieve(RunId runId, BlobId blobId) {
        return executor.executeRows(SimpleStatement.newInstance(
                "SELECT reference_type, reference_id FROM " + tableName(runId) + " WHERE blob_id = ?", blobId.asString())
                .setPageSize(pageSize))
            .map(row -> new Reference(blobId, Type.valueOf(row.getString("reference_type")), row.getUuid("reference_id")));
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
