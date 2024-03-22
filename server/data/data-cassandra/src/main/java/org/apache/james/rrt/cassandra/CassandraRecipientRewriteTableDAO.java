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

package org.apache.james.rrt.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.DOMAIN;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.MAPPING;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.TABLE_NAME;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.USER;

import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.MappingsImpl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraRecipientRewriteTableDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement retrieveMappingStatement;
    private final PreparedStatement retrieveAllMappingsStatement;

    @Inject
    public CassandraRecipientRewriteTableDAO(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(DOMAIN, bindMarker(DOMAIN))
            .value(MAPPING, bindMarker(MAPPING))
            .build());

        this.retrieveMappingStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(MAPPING)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .build());

        this.retrieveAllMappingsStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(USER, DOMAIN, MAPPING)
            .build());

        this.deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .whereColumn(MAPPING).isEqualTo(bindMarker(MAPPING))
            .build());
    }

    public Mono<Void> addMapping(MappingSource source, Mapping mapping) {
        return executor.executeVoid(insertStatement.bind()
            .setString(USER, source.getFixedUser())
            .setString(DOMAIN, source.getFixedDomain())
            .setString(MAPPING, mapping.asString()));
    }

    Mono<Void> removeMapping(MappingSource source, Mapping mapping) {
        return executor.executeVoid(deleteStatement.bind()
            .setString(USER, source.getFixedUser())
            .setString(DOMAIN, source.getFixedDomain())
            .setString(MAPPING, mapping.asString()));
    }

    Mono<MappingsImpl> retrieveMappings(MappingSource source) {
        return executor.executeRows(retrieveMappingStatement.bind()
                .setString(USER, source.getFixedUser())
                .setString(DOMAIN, source.getFixedDomain()))
            .mapNotNull(row -> row.getString(MAPPING))
            .collect(ImmutableList.toImmutableList())
            .map(MappingsImpl::fromCollection)
            .filter(Predicate.not(MappingsImpl::isEmpty));
    }

    public Flux<Pair<MappingSource, Mapping>> getAllMappings() {
        return executor.executeRows(retrieveAllMappingsStatement.bind())
            .map(row -> Pair.of(
                MappingSource.fromUser(row.getString(USER), row.getString(DOMAIN)),
                Mapping.of(row.getString(MAPPING))));
    }
}
