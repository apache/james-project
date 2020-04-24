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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DockerCassandraExtension.class)
class CassandraHealthCheckTest {

    private CassandraHealthCheck healthCheck;
    private CassandraCluster cassandra;

    @BeforeEach
    void setUp(DockerCassandraExtension.DockerCassandra cassandraServer) {
        cassandra = CassandraCluster.create(CassandraModule.builder().build(), cassandraServer.getHost());
        healthCheck = new CassandraHealthCheck(cassandra.getConf());
    }

    @AfterEach
    void tearDown() {
        cassandra.close();
    }

    @Test
    void checkShouldReturnHealthyWhenCassandraIsRunning() {
        Result check = healthCheck.check().block();

        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenCassandraIsNotRunning(DockerCassandraExtension.DockerCassandra cassandraServer) {
        try {
            cassandraServer.getContainer().pause();
            Result check = healthCheck.check().block();

            assertThat(check.isUnHealthy()).isTrue();
        } finally {
            cassandraServer.getContainer().unpause();
        }

    }

    @Test
    void checkShouldDetectWhenCassandraRecovered(DockerCassandraExtension.DockerCassandra cassandraServer) {
        try {
            cassandraServer.getContainer().pause();

            healthCheck.check().block();
        } finally {
            cassandraServer.getContainer().unpause();
        }

        Result check = healthCheck.check().block();

        assertThat(check.isHealthy()).isTrue();
    }


}