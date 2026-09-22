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

import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.TABLE_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.compaction.BlobReferenceMappingSource;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;

public class CassandraBlobReferenceMappingSource implements BlobReferenceMappingSource {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement selectAll;

    @Inject
    public CassandraBlobReferenceMappingSource(CqlSession session, BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.selectAll = session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .columns(MESSAGE_ID, HEADER_CONTENT, BODY_CONTENT)
            .build());
    }

    @Override
    public Publisher<BlobIdMessageIdMapping> listBlobIdMessageIdMappings() {
        return cassandraAsyncExecutor.executeRows(selectAll.bind())
            .flatMapIterable(row -> {
                String messageIdStr = row.get(MESSAGE_ID, TypeCodecs.TIMEUUID).toString();
                String headerContent = row.get(HEADER_CONTENT, TypeCodecs.TEXT);
                String bodyContent = row.get(BODY_CONTENT, TypeCodecs.TEXT);

                List<BlobIdMessageIdMapping> mappings = new ArrayList<>(2);
                if (headerContent != null && !headerContent.isEmpty()) {
                    mappings.add(new BlobIdMessageIdMapping(blobIdFactory.parse(headerContent), messageIdStr));
                }
                if (bodyContent != null && !bodyContent.isEmpty()) {
                    mappings.add(new BlobIdMessageIdMapping(blobIdFactory.parse(bodyContent), messageIdStr));
                }
                return mappings;
            });
    }

    @Override
    public Publisher<BlobIdMessageIdMapping> loadReferencesFor(Collection<BlobId> blobIds) {
        if (blobIds.isEmpty()) {
            return Flux.empty();
        }
        Set<BlobId> targetBlobIds = Set.copyOf(blobIds);
        return Flux.from(listBlobIdMessageIdMappings())
            .filter(mapping -> targetBlobIds.contains(mapping.blobId()));
    }
}
