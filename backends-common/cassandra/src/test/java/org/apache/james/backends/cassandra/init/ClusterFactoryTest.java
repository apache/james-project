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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;

class ClusterFactoryTest {

    public static final KeyspaceConfiguration KEYSPACE_CONFIGURATION = KeyspaceConfiguration.builder()
        .keyspace(DockerCassandra.KEYSPACE)
        .replicationFactor(1)
        .disableDurableWrites();

    @RegisterExtension
    static CassandraClusterExtension cassandraExtension = new CassandraClusterExtension(CassandraModule.EMPTY_MODULE);

    @AfterEach
    void tearDown(DockerCassandra dockerCassandra) {
        dockerCassandra.unpause();
    }

    @Test
    void createShouldThrowWhenContactableCluster(DockerCassandra dockerCassandra) {
        dockerCassandra.pause();

        assertThatThrownBy(() -> ClusterFactory.create(
            dockerCassandra.configurationBuilder()
                .build(), KEYSPACE_CONFIGURATION))
            .isInstanceOf(AllNodesFailedException.class);
    }

    @Test
    void createShouldReturnAContactableCluster(DockerCassandra dockerCassandra) {
        CqlSession cluster = ClusterFactory.create(dockerCassandra.configurationBuilder()
            .build(), KeyspaceConfiguration.builder().keyspace(DockerCassandra.KEYSPACE).replicationFactor(1).disableDurableWrites());

        assertThatClusterIsContactable(cluster);
    }

    void assertThatClusterIsContactable(CqlSession session) {
        try {
            session.execute("SELECT dateof(now()) FROM system.local ;");
        } catch (Exception e) {
            throw new AssertionError("expecting cluster can be connected but actually not", e);
        }
    }
}
