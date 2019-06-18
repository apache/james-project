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
import static com.datastax.driver.core.querybuilder.QueryBuilder.truncate;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.MAPPING_TYPE;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.MAPPING_VALUE;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.SOURCE;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMappingsSourcesDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement retrieveSourcesStatement;
    private final PreparedStatement truncateStatement;

    @Inject
    public CassandraMappingsSourcesDAO(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertStatement = prepareInsertStatement(session);
        this.deleteStatement = prepareDelete(session);
        this.retrieveSourcesStatement = prepareRetrieveSourcesStatement(session);
        this.truncateStatement = prepareTruncateStatement(session);
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(MAPPING_TYPE, bindMarker(MAPPING_TYPE))
            .value(MAPPING_VALUE, bindMarker(MAPPING_VALUE))
            .value(SOURCE, bindMarker(SOURCE)));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(TABLE_NAME)
            .where(eq(MAPPING_TYPE, bindMarker(MAPPING_TYPE)))
            .and(eq(MAPPING_VALUE, bindMarker(MAPPING_VALUE)))
            .and(eq(SOURCE, bindMarker(SOURCE))));
    }

    private PreparedStatement prepareRetrieveSourcesStatement(Session session) {
        return session.prepare(select(SOURCE)
            .from(TABLE_NAME)
            .where(eq(MAPPING_TYPE, bindMarker(MAPPING_TYPE)))
            .and(eq(MAPPING_VALUE, bindMarker(MAPPING_VALUE))));
    }

    private PreparedStatement prepareTruncateStatement(Session session) {
        return session.prepare(truncate(TABLE_NAME));
    }

    public Mono<Void> addMapping(Mapping mapping, MappingSource source) {
        return executor.executeVoid(insertStatement.bind()
            .setString(MAPPING_TYPE, mapping.getType().asPrefix())
            .setString(MAPPING_VALUE, mapping.getMappingValue())
            .setString(SOURCE, source.asMailAddressString()));
    }

    Mono<Void> removeMapping(Mapping mapping, MappingSource source) {
        return executor.executeVoid(deleteStatement.bind()
            .setString(MAPPING_TYPE, mapping.getType().asPrefix())
            .setString(MAPPING_VALUE, mapping.getMappingValue())
            .setString(SOURCE, source.asMailAddressString()));
    }

    public Flux<MappingSource> retrieveSources(Mapping mapping) {
        return executor.executeRows(retrieveSourcesStatement.bind()
            .setString(MAPPING_TYPE, mapping.getType().asPrefix())
            .setString(MAPPING_VALUE, mapping.getMappingValue()))
            .map(row -> MappingSource.parse(row.getString(SOURCE)));
    }

    public Mono<Void> removeAllData() {
        return executor.executeVoid(truncateStatement.bind());
    }
}
