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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.uuid.Uuids;

import reactor.core.publisher.Flux;

class PaggingTest {
    
    private static final String TABLE_NAME = "test";
    private static final String ID = "id";
    private static final String CLUSTERING = "clustering";
    private static final UUID UUID = Uuids.timeBased();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.table(TABLE_NAME)
        .comment("Testing table")
        .statement(statement -> types -> statement
            .withPartitionKey(ID, DataTypes.TIMEUUID)
            .withClusteringColumn(CLUSTERING,  DataTypes.BIGINT))
        .build());

    private CassandraAsyncExecutor executor;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        executor = new CassandraAsyncExecutor(cassandra.getConf());
    }

    @Test
    void pagingShouldWork() {
        int fetchSize = 200;
        int size = 2 * fetchSize + 50;

        Flux.range(0, size)
            .flatMap(i -> executor
                .executeVoid(insertInto(TABLE_NAME)
                    .value(ID, literal(UUID))
                    .value(CLUSTERING, literal(i))
                    .build()))
            .blockLast();

        assertThat(
            executor.executeRows(selectFrom(TABLE_NAME)
                    .all()
                    .whereColumn(ID).isEqualTo(literal(UUID))
                    .build()
                    .setFetchSize(fetchSize))
                .count()
                .block())
            .isEqualTo(size);
    }

}
