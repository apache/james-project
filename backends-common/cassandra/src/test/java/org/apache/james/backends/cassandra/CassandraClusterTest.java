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
package org.apache.james.backends.cassandra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraClusterTest {

    @RegisterExtension
    static DockerCassandraExtension cassandraExtension = new DockerCassandraExtension();
    CassandraCluster connection;

    @BeforeEach
    void setUp() {
        connection = methodToDetectInStackTrace();
    }

    CassandraCluster methodToDetectInStackTrace() {
        return createCluster();
    }

    @AfterEach
    void tearDown() {
        connection.close();
    }

    private CassandraCluster createCluster() {
        return CassandraCluster.create(CassandraModule.builder().build(), cassandraExtension.getDockerCassandra().getHost());
    }

    @Test
    void creatingTwoClustersShouldThrow() {
        assertThatThrownBy(this::createCluster).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void creatingTwoClustersSequentiallyShouldNotThrow() {
        connection.close();
        assertThatCode(() -> {
            try (CassandraCluster cluster = createCluster()) {
                // Trigger autoclose
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void creatingTwoClustersShouldProvideFirstCreationStacktrace() {
        assertThatThrownBy(this::createCluster).hasStackTraceContaining("methodToDetectInStackTrace");
    }
}