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

package org.apache.james.backends.cassandra.init;

import java.time.Duration;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.util.Host;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.util.concurrency.ConcurrentTestRunner.ConcurrentOperation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.type.DataTypes;

@Disabled("JAMES-3501 Concurrent start is not supported. Instead start a single server then scale.")
class CassandraTableManagerConcurrentTest {
    private static final String TABLE_NAME = "tablename";

    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraSchemaVersionModule.MODULE,
        CassandraModule.table(TABLE_NAME)
            .comment("Testing table")
            .statement(statement -> types -> statement
                .withPartitionKey("id", DataTypes.TIMEUUID)
                .withClusteringColumn("clustering", DataTypes.BIGINT))
            .build());

    @RegisterExtension
    static DockerCassandraExtension cassandraExtension = new DockerCassandraExtension();

    @Test
    void initializeTableShouldCreateAllTheTables() throws Exception {
        Host cassandraHost = cassandraExtension.getDockerCassandra().getHost();
        ConcurrentOperation concurrentOperation = (a, b) -> CassandraCluster.create(MODULE, cassandraHost);


        ConcurrentTestRunner.builder()
            .operation(concurrentOperation)
            .threadCount(2)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}