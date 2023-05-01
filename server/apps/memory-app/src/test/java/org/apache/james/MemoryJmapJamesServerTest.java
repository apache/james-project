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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.jmap.draft.JMAPConfigurationStartUpCheck;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryJmapJamesServerTest {

    private static JamesServerBuilder extensionBuilder() {
        return new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
            MemoryJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .build())
            .server(configuration -> MemoryJamesServerMain.createServer(configuration)
                .overrideWith(new TestJMAPServerModule()));
    }

    @Nested
    class JmapJamesServerTest implements JmapJamesServerContract, MailsShouldBeWellReceived {
        @Override
        public int imapPort(GuiceJamesServer server) {
            return server.getProbe(ImapGuiceProbe.class).getImapPort();
        }

        @Override
        public int smtpPort(GuiceJamesServer server) {
            return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
        }

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
                .overrideServerModule(binder -> binder.bind(JMAPDraftConfiguration.class)
                    .toInstance(TestJMAPServerModule
                        .jmapDraftConfigurationBuilder()
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
                .overrideServerModule(binder -> binder.bind(JMAPDraftConfiguration.class)
                    .toInstance(TestJMAPServerModule
                        .jmapDraftConfigurationBuilder()
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
