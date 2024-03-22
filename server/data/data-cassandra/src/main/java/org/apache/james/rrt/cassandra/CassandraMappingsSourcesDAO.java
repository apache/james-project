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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.truncate;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.MAPPING_TYPE;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.MAPPING_VALUE;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.SOURCE;
import static org.apache.james.rrt.cassandra.tables.CassandraMappingsSourcesTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMappingsSourcesDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement retrieveSourcesStatement;
    private final PreparedStatement truncateStatement;

    @Inject
    public CassandraMappingsSourcesDAO(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(MAPPING_TYPE, bindMarker(MAPPING_TYPE))
            .value(MAPPING_VALUE, bindMarker(MAPPING_VALUE))
            .value(SOURCE, bindMarker(SOURCE))
            .build());

        this.deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(MAPPING_TYPE).isEqualTo(bindMarker(MAPPING_TYPE))
            .whereColumn(MAPPING_VALUE).isEqualTo(bindMarker(MAPPING_VALUE))
            .whereColumn(SOURCE).isEqualTo(bindMarker(SOURCE))
            .build());

        this.retrieveSourcesStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(SOURCE)
            .whereColumn(MAPPING_TYPE).isEqualTo(bindMarker(MAPPING_TYPE))
            .whereColumn(MAPPING_VALUE).isEqualTo(bindMarker(MAPPING_VALUE))
            .build());

        this.truncateStatement = session.prepare(truncate(TABLE_NAME).build());
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
