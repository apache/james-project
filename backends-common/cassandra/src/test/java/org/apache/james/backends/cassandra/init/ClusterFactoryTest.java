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

import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;

class ClusterFactoryTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraExtension = new CassandraClusterExtension(CassandraModule.EMPTY_MODULE);

    @AfterEach
    void tearDown(DockerCassandra dockerCassandra) {
        dockerCassandra.unpause();
    }

    @Test
    void consistencyLevelShouldBeEqualToQuorum(DockerCassandra dockerCassandra) {
        Cluster cluster = ClusterFactory.create(dockerCassandra.configurationBuilder()
            .build());

        ConsistencyLevel consistencyLevel = cluster.getConfiguration()
            .getQueryOptions()
            .getConsistencyLevel();

        assertThat(consistencyLevel).isEqualTo(ConsistencyLevel.QUORUM);
    }

    @Test
    void createShouldThrowWhenContactableCluster(DockerCassandra dockerCassandra) {
        dockerCassandra.pause();

        assertThatThrownBy(() -> ClusterFactory.create(
            dockerCassandra.configurationBuilder()
                .build()))
            .isInstanceOf(NoHostAvailableException.class);
    }

    @Test
    void createShouldReturnAContactableCluster(DockerCassandra dockerCassandra) {
        Cluster cluster = ClusterFactory.create(dockerCassandra.configurationBuilder()
            .build());

        assertThatClusterIsContactable(cluster);
    }

    void assertThatClusterIsContactable(Cluster cluster) {
        try (Session session = cluster.connect("system")) {
            session.execute(
                session.prepare(select()
                    .fcall("NOW")
                    .from("local"))
                .bind());
        } catch (Exception e) {
            throw new AssertionError("expecting cluster can be connected but actually not", e);
        }
    }
}
