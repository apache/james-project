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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.DOMAIN;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.MAPPING;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.TABLE_NAME;
import static org.apache.james.rrt.cassandra.tables.CassandraRecipientRewriteTableTable.USER;

import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.MappingsImpl;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraRecipientRewriteTableDAO {
    private final CassandraAsyncExecutor executor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement retrieveMappingStatement;
    private final PreparedStatement retrieveAllMappingsStatement;

    @Inject
    public CassandraRecipientRewriteTableDAO(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;
        this.insertStatement = prepareInsertStatement(session);
        this.deleteStatement = prepareDelete(session);
        this.retrieveMappingStatement = prepareRetrieveMappingStatement(session);
        this.retrieveAllMappingsStatement = prepareRetrieveAllMappingStatement(session);
    }

    private PreparedStatement prepareRetrieveAllMappingStatement(Session session) {
        return session.prepare(select(USER, DOMAIN, MAPPING)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareRetrieveMappingStatement(Session session) {
        return session.prepare(select(MAPPING)
            .from(TABLE_NAME)
            .where(eq(USER, bindMarker(USER)))
            .and(eq(DOMAIN, bindMarker(DOMAIN))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(USER, bindMarker(USER)))
            .and(eq(DOMAIN, bindMarker(DOMAIN)))
            .and(eq(MAPPING, bindMarker(MAPPING))));
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(DOMAIN, bindMarker(DOMAIN))
            .value(MAPPING, bindMarker(MAPPING)));
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
        return executor.execute(retrieveMappingStatement.bind()
            .setString(USER, source.getFixedUser())
            .setString(DOMAIN, source.getFixedDomain()))
            .map(resultSet -> cassandraUtils.convertToStream(resultSet)
                .map(row -> row.getString(MAPPING))
                .collect(Guavate.toImmutableList()))
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
