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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.DockerCassandraSingleton;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.modules.mailbox.KeyspacesConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KeyspaceCreationTest {
    @Nested
    class CreateWhenKeyspaceExists {
        @RegisterExtension
        JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.elasticSearch())
            .extension(new DockerElasticSearchExtension())
            .extension(new CassandraExtension())
            .server(CassandraJamesServerMain::createServer)
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                .toInstance(DockerCassandraSingleton.singleton.configurationBuilder()
                    .createKeyspace()
                    .build()))
            .disableAutoStart()
            .build();

        @Test
        void startShouldNotThrowWhenKeyspaceExists(GuiceJamesServer jamesServer) {
            assertThatCode(jamesServer::start)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class CreateWhenDoesNotExistAndHasRights {
        @RegisterExtension
        JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.elasticSearch())
            .extension(new DockerElasticSearchExtension())
            .extension(new CassandraExtension())
            .server(CassandraJamesServerMain::createServer)
            .overrideServerModule(binder -> binder.bind(KeyspacesConfiguration.class).toInstance(KeyspacesConfiguration.builder()
                .keyspace("non_existing_keyspace")
                .cacheKeyspace("cache_non_existing_keyspace")
                .replicationFactor(1)
                .disableDurableWrites()
                .build()))
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                    .toInstance(DockerCassandraSingleton.singleton.superUserConfigurationBuilder()
                        .createKeyspace()
                        .build()))
            .disableAutoStart()
            .build();

        @Test
        void startShouldNotThrowWhenCreateAKeyspaceWithAuthorizedSession(GuiceJamesServer jamesServer) {
            assertThatCode(jamesServer::start)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class CreateWhenDoesNotExistAndDoNotHaveRights {
        @RegisterExtension
        JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.elasticSearch())
            .extension(new DockerElasticSearchExtension())
            .extension(new CassandraExtension())
            .server(CassandraJamesServerMain::createServer)
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                .toInstance(DockerCassandraSingleton.singleton.configurationBuilder()
                    .createKeyspace()
                    .build()))
            .overrideServerModule(binder -> binder.bind(KeyspacesConfiguration.class).toInstance(KeyspacesConfiguration.builder()
                .keyspace("non_existing_keyspace")
                .cacheKeyspace("cache_non_existing_keyspace")
                .replicationFactor(1)
                .disableDurableWrites()
                .build()))
            .disableAutoStart()
            .build();

        @Test
        void startShouldThrowWhenAttemptToCreateAKeyspace(GuiceJamesServer jamesServer) {
            assertThatThrownBy(jamesServer::start)
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class StartWhenKeyspaceDoesNotExist {
        @RegisterExtension
        JamesServerExtension testExtension = TestingDistributedJamesServerBuilder.withSearchConfiguration(SearchConfiguration.elasticSearch())
            .extension(new DockerElasticSearchExtension())
            .extension(new CassandraExtension())
            .server(CassandraJamesServerMain::createServer)
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                .toInstance(DockerCassandraSingleton.singleton.configurationBuilder()
                    .build()))
            .overrideServerModule(binder -> binder.bind(KeyspacesConfiguration.class).toInstance(KeyspacesConfiguration.builder()
                .keyspace("non_existing_keyspace")
                .cacheKeyspace("cache_non_existing_keyspace")
                .replicationFactor(1)
                .disableDurableWrites()
                .build()))
            .disableAutoStart()
            .build();

        @Test
        void startShouldThrowWhenAttemptToUseANonExistingKeyspace(GuiceJamesServer jamesServer) {
            assertThatThrownBy(jamesServer::start)
                .isInstanceOf(Exception.class);
        }
    }
}
