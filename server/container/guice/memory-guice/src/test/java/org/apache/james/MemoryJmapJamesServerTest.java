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

import static org.apache.james.JmapJamesServerContract.DOMAIN_LIST_CONFIGURATION_MODULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.JMAPConfigurationStartUpCheck;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryJmapJamesServerTest {

    private static final int LIMIT_TO_10_MESSAGES = 10;

    private static JamesServerBuilder extensionBuilder() {
        return new JamesServerBuilder()
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(DOMAIN_LIST_CONFIGURATION_MODULE));
    }

    @Nested
    class JmapJamesServerTest implements JmapJamesServerContract {
        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder().build();
    }

    @Nested
    class JamesServerJmapConfigurationTest {

        @Nested
        class BadAliasKeyStore {

            @RegisterExtension
            JamesServerExtension jamesServerExtension = extensionBuilder()
                .disableAutoStart()
                .overrideServerModule(binder -> binder.bind(JMAPConfiguration.class)
                    .toInstance(TestJMAPServerModule
                        .jmapConfigurationBuilder()
                        .keystore("badAliasKeystore")
                        .secret("password")
                        .build()))
                .build();

            @Test
            void jamesShouldNotStartWhenBadAliasKeyStore(GuiceJamesServer server) {
                assertThatThrownBy(server::start)
                    .isInstanceOfSatisfying(
                        StartUpChecksPerformer.StartUpChecksException.class,
                        ex -> assertThat(ex.badCheckNames())
                            .containsOnly(JMAPConfigurationStartUpCheck.CHECK_NAME))
                    .hasMessageContaining("Alias 'james' keystore can't be found");
            }
        }

        @Nested
        class BadSecretKeyStore {

            @RegisterExtension
            JamesServerExtension jamesServerExtension = extensionBuilder()
                .disableAutoStart()
                .overrideServerModule(binder -> binder.bind(JMAPConfiguration.class)
                    .toInstance(TestJMAPServerModule
                        .jmapConfigurationBuilder()
                        .secret("WrongSecret")
                        .build()))
                .build();

            @Test
            void jamesShouldNotStartWhenBadSecret(GuiceJamesServer server) {
                assertThatThrownBy(server::start)
                    .isInstanceOfSatisfying(
                        StartUpChecksPerformer.StartUpChecksException.class,
                        ex -> assertThat(ex.badCheckNames())
                            .containsOnly(JMAPConfigurationStartUpCheck.CHECK_NAME))
                    .hasMessageContaining("Keystore was tampered with, or password was incorrect");
            }
        }
    }
}
