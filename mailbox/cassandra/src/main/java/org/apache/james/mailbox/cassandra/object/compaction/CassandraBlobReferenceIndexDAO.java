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

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.cassandra.object.compaction.CassandraBlobReferenceDAO.Reference;
import org.apache.james.mailbox.cassandra.object.compaction.CassandraBlobReferenceDAO.Type;

import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reads and writes references in one run's index table. */
class CassandraBlobReferenceIndexDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final int pageSize;

    CassandraBlobReferenceIndexDAO(CassandraAsyncExecutor executor, PreparedStatement insert, PreparedStatement select, int pageSize) {
        this.executor = executor;
        this.insert = insert;
        this.select = select;
        this.pageSize = pageSize;
    }

    Mono<Void> store(Reference reference) {
        return executor.executeVoid(insert.bind(reference.blobId().asString(), reference.type().name(), reference.referenceId()));
    }

    Flux<Reference> retrieve(BlobId blobId) {
        return executor.executeRows(select.bind(blobId.asString()).setPageSize(pageSize))
            .map(row -> new Reference(blobId, Type.valueOf(row.getString("reference_type")), row.getUuid("reference_id")));
    }
}
