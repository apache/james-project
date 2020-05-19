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

import static org.apache.james.JamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.DockerCassandra;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.CreationException;

class AuthenticatedCassandraJamesServerTest {
    private static final String CASSANDRA_USER = DockerCassandra.CASSANDRA_TESTING_USER;
    private static final String VALID_PASSWORD = DockerCassandra.CASSANDRA_TESTING_PASSWORD;
    private static final String INVALID_PASSWORD = "bad";

    private final CassandraExtension cassandraExtension = new CassandraExtension();

    @Nested
    class AuthenticationTest implements JamesServerContract {
        @RegisterExtension
        JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
            .extension(new DockerElasticSearchExtension())
            .extension(cassandraExtension)
            .server(configuration -> CassandraJamesServerMain.createServer(configuration)
                .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE))
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                .toInstance(DockerCassandra.configurationBuilder(cassandraExtension.getCassandra().getHost())
                    .username(CASSANDRA_USER)
                    .password(VALID_PASSWORD)
                    .build()))
            .build();
    }

    @Nested
    class SslTest {
        @RegisterExtension
        JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
            .extension(new DockerElasticSearchExtension())
            .extension(cassandraExtension)
            .disableAutoStart()
            .server(configuration -> CassandraJamesServerMain.createServer(configuration)
                .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE))
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                .toInstance(DockerCassandra.configurationBuilder(cassandraExtension.getCassandra().getHost())
                    .username(CASSANDRA_USER)
                    .password(VALID_PASSWORD)
                    .useSsl()
                    .build()))
            .build();

        @Test
        void startShouldFailWhenSslUsedAndNotSupportedByServer(GuiceJamesServer jamesServer) {
            assertThatThrownBy(jamesServer::start)
                .isInstanceOf(CreationException.class)
                .hasStackTraceContaining("Caused by: com.datastax.driver.core.exceptions.NoHostAvailableException: All host(s) tried for query failed");
        }
    }

    @Nested
    class AuthenticationFailureTest {
        @RegisterExtension
        JamesServerExtension testExtension = new JamesServerBuilder<>(JamesServerBuilder.defaultConfigurationProvider())
            .extension(new DockerElasticSearchExtension())
            .extension(cassandraExtension)
            .disableAutoStart()
            .server(configuration -> CassandraJamesServerMain.createServer(configuration)
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                .overrideWith(new TestJMAPServerModule())
                .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE))
            .overrideServerModule(binder -> binder.bind(ClusterConfiguration.class)
                .toInstance(DockerCassandra.configurationBuilder(cassandraExtension.getCassandra().getHost())
                    .username(CASSANDRA_USER)
                    .password(INVALID_PASSWORD)
                    .maxRetry(1)
                    .build()))
            .build();

        @Test
        void startShouldFailOnBadPassword(GuiceJamesServer jamesServer) {
            assertThatThrownBy(jamesServer::start)
                .isInstanceOf(CreationException.class)
                .hasStackTraceContaining("Caused by: com.datastax.driver.core.exceptions.AuthenticationException: Authentication error");
        }
    }
}
