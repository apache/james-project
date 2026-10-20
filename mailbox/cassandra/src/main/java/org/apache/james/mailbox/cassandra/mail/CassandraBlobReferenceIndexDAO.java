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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraBlobReferenceIndexTable.BLOB_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraBlobReferenceIndexTable.REFERENCE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraBlobReferenceIndexTable.REFERENCE_TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraBlobReferenceIndexTable.RUN_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraBlobReferenceIndexTable.TABLE_NAME;

import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.cassandra.mail.CassandraBlobReferenceDAO.Reference;
import org.apache.james.mailbox.cassandra.mail.CassandraBlobReferenceDAO.Type;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Paged, run-scoped reference storage. No TTL: silently losing an index during a pass could leave dead references.
 * Abandoned runs can be cleaned using their run ID; they never participate in another run's lookups.
 */
public class CassandraBlobReferenceIndexDAO {
    private static final int PAGE_SIZE = 500;
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement partitions;
    private final PreparedStatement delete;
    private final int pageSize;

    @Inject
    public CassandraBlobReferenceIndexDAO(CqlSession session) {
        this(session, PAGE_SIZE);
    }

    @VisibleForTesting
    CassandraBlobReferenceIndexDAO(CqlSession session, int pageSize) {
        this.executor = new CassandraAsyncExecutor(session);
        this.pageSize = pageSize;
        insert = session.prepare(insertInto(TABLE_NAME)
            .value(RUN_ID, bindMarker(RUN_ID)).value(BLOB_ID, bindMarker(BLOB_ID))
            .value(REFERENCE_TYPE, bindMarker(REFERENCE_TYPE)).value(REFERENCE_ID, bindMarker(REFERENCE_ID)).build());
        select = session.prepare(selectFrom(TABLE_NAME).columns(REFERENCE_TYPE, REFERENCE_ID)
            .where(column(RUN_ID).isEqualTo(bindMarker(RUN_ID)), column(BLOB_ID).isEqualTo(bindMarker(BLOB_ID))).build());
        partitions = session.prepare(selectFrom(TABLE_NAME).distinct().columns(RUN_ID, BLOB_ID).build());
        delete = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(RUN_ID).isEqualTo(bindMarker(RUN_ID)), column(BLOB_ID).isEqualTo(bindMarker(BLOB_ID))).build());
    }

    Mono<Void> store(UUID runId, Reference reference) {
        return executor.executeVoid(insert.bind()
            .setUuid(RUN_ID, runId).setString(BLOB_ID, reference.blobId().asString())
            .setString(REFERENCE_TYPE, reference.type().name()).setUuid(REFERENCE_ID, reference.referenceId()));
    }

    Flux<Reference> retrieve(UUID runId, BlobId blobId) {
        return executor.executeRows(select.bind()
                .setUuid(RUN_ID, runId).setString(BLOB_ID, blobId.asString()).setPageSize(pageSize))
            .map(row -> new Reference(blobId, Type.valueOf(row.getString(REFERENCE_TYPE)), row.getUuid(REFERENCE_ID)));
    }

    public Mono<Void> cleanup(UUID runId) {
        // One paged scan of index partition keys, never another scan of authoritative mailbox tables.
        // Do not collect keys in memory, and never delete partitions belonging to other runs.
        return executor.executeRows(partitions.bind().setPageSize(pageSize))
            .filter(row -> runId.equals(row.getUuid(RUN_ID)))
            .concatMap(row -> executor.executeVoid(delete.bind()
                .setUuid(RUN_ID, runId).setString(BLOB_ID, row.getString(BLOB_ID))))
            .then();
    }
}
