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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ResilientClusterProviderTest {

    private static final String KEYSPACE = "my_keyspace";

    @RegisterExtension
    static CassandraClusterExtension cassandraExtension = new CassandraClusterExtension(CassandraModule.NO_MODULE);

    @AfterEach
    void tearDown(DockerCassandra dockerCassandra) {
        dockerCassandra.administrator()
            .dropKeyspace(KEYSPACE);
    }

    @Nested
    class WhenAllowCreatingKeySpace {

        @Test
        void initializationShouldThrowWhenKeyspaceDoesntExist(DockerCassandra dockerCassandra) {
            assertThatThrownBy(() -> new ResilientClusterProvider(
                    dockerCassandra.configurationBuilder()
                        .keyspace(KEYSPACE)
                        .createKeyspace()
                        .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("User james_testing has no CREATE permission on <all keyspaces> or any of its parents");
        }

        @Test
        void initializationWithPrivilegedUserShouldCreateKeySpaceWhenNotExisted(DockerCassandra dockerCassandra) {
            new ResilientClusterProvider(dockerCassandra.configurationBuilderForSuperUser()
                .keyspace(KEYSPACE)
                .createKeyspace()
                .build());

            assertThat(dockerCassandra.administrator()
                    .keyspaceExist(KEYSPACE))
                .isTrue();
        }

        @Test
        void initializationShouldNotThrownWhenKeyspaceAlreadyExisted(DockerCassandra dockerCassandra) {
            ClusterConfiguration configuration = dockerCassandra.configurationBuilder()
                .keyspace(KEYSPACE)
                .createKeyspace()
                .build();
            dockerCassandra.administrator()
                .initializeKeyspace(KEYSPACE);

            assertThatCode(() -> new ResilientClusterProvider(configuration))
                .doesNotThrowAnyException();
        }

        @Test
        void initializationShouldNotImpactToKeyspaceExistentWhenAlreadyExisted(DockerCassandra dockerCassandra) {
            ClusterConfiguration configuration = dockerCassandra.configurationBuilder()
                .keyspace(KEYSPACE)
                .createKeyspace()
                .build();
            dockerCassandra.administrator()
                .initializeKeyspace(KEYSPACE);

            new ResilientClusterProvider(configuration);

            assertThat(dockerCassandra.administrator()
                    .keyspaceExist(KEYSPACE))
                .isTrue();
        }
    }

    @Nested
    class WhenProhibitCreatingKeySpace {

        @Test
        void initializationShouldNotCreateWhenKeyspaceDoesntExist(DockerCassandra dockerCassandra) {
            new ResilientClusterProvider(dockerCassandra.configurationBuilder()
                .keyspace(KEYSPACE)
                .build());

            assertThat(dockerCassandra.administrator()
                    .keyspaceExist(KEYSPACE))
                .isFalse();
        }

        @Test
        void initializationShouldNotThrownWhenKeyspaceAlreadyExisted(DockerCassandra dockerCassandra) {
            ClusterConfiguration configuration = dockerCassandra.configurationBuilder()
                .keyspace(KEYSPACE)
                .build();
            dockerCassandra.administrator()
                .initializeKeyspace(KEYSPACE);

            assertThatCode(() -> new ResilientClusterProvider(configuration))
                .doesNotThrowAnyException();
        }

        @Test
        void initializationShouldNotImpactToKeyspaceExistentWhenAlreadyExisted(DockerCassandra dockerCassandra) {
            ClusterConfiguration configuration = dockerCassandra.configurationBuilder()
                .keyspace(KEYSPACE)
                .build();
            dockerCassandra.administrator()
                .initializeKeyspace(KEYSPACE);

            new ResilientClusterProvider(configuration);

            assertThat(dockerCassandra.administrator()
                    .keyspaceExist(KEYSPACE))
                .isTrue();
        }
    }
}