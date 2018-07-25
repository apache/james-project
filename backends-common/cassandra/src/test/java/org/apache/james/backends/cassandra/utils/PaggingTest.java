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

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.util.CompletableFutureUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.utils.UUIDs;

public class PaggingTest {
    
    private static final String TABLE_NAME = "test";
    private static final String ID = "id";
    private static final String CLUSTERING = "clustering";
    private static final UUID UUID = UUIDs.timeBased();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private static CassandraCluster cassandra;
    private CassandraAsyncExecutor executor;

    @BeforeClass
    public static void setUpClass() {
        CassandraModule modules = CassandraModule.table(TABLE_NAME)
            .statement(statement -> statement
                .addPartitionKey(ID, DataType.timeuuid())
                .addClusteringColumn(CLUSTERING, DataType.bigint()))
            .build();
        cassandra = CassandraCluster.create(modules, cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        executor = new CassandraAsyncExecutor(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void pagingShouldWork() {
        int fetchSize = 200;
        int size = 2 * fetchSize + 50;

        CompletableFutureUtil.allOf(
            IntStream.range(0, size)
                .boxed()
                .map(i ->
                    executor
                        .executeVoid(insertInto(TABLE_NAME)
                            .value(ID, UUID)
                            .value(CLUSTERING, i))))
            .join();

        assertThat(
            executor.execute(select()
                .from(TABLE_NAME)
                .where(eq(ID, UUID))
                .setFetchSize(fetchSize))
                .join())
            .hasSize(size);
    }

}
