/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.cli;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.util.Port;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class DomainManageTest {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(MemoryJamesServerMain::createServer)
        .build();

    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errorStreamCaptor = new ByteArrayOutputStream();

    Port port;

    @BeforeEach
    void setUp(GuiceJamesServer server) {
        port = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();
    }

    @AfterEach
    void tearDown() {
        System.err.println(new String(errorStreamCaptor.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void domainListCommandShouldWShowOnlyDefaultDomain() {
        int exitCode = executeFluent("domain", "list");

        SoftAssertions.assertSoftly( softly -> {
            assertThat(exitCode).isEqualTo(0);
            assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("localhost".toCharArray());
        });
    }

    @Test
    void domainCreateCommandWithValidNameShouldSuccessfully() {
        int exitCode = executeFluent("domain", "create", "linagora.com");

        executeFluent("domain", "list");

        SoftAssertions.assertSoftly( softly -> {
            assertThat(exitCode).isEqualTo(0);
            assertThat(outputStreamCaptor.toString()).contains("linagora.com");
        });
    }

    @Test
    void domainCreateCommandWithInvalidNameShouldFailed() {
        int exitCode1 = executeFluent("domain", "create", "@linagora.com");

        int exitCode2 = executeFluent("domain", "create", "linagora.com/");

        int exitCode3 = executeFluent("domain", "create", "");

        WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            "--url", "http://127.0.0.1:" + port.getValue(), "domain", "list");

        SoftAssertions.assertSoftly( softly -> {
            assertThat(exitCode1).isEqualTo(1);
            assertThat(exitCode2).isEqualTo(1);
            assertThat(exitCode3).isEqualTo(1);
            assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("localhost".toCharArray());
        });
    }

    @Test
    void domainDeleteCommandWithValidDomainShouldSucceed() {
        executeFluent("domain", "create", "linagora.com");

        int exitCode = executeFluent("domain", "delete", "linagora.com");

        executeFluent("domain", "list");

        SoftAssertions.assertSoftly( softly -> {
            assertThat(exitCode).isEqualTo(0);
            assertThat(outputStreamCaptor.toString().contains("linagora.com")).isFalse();
        });
    }

    @Test
    void domainDeleteCommandWithDefaultDomainShouldFail() {
        int exitCode = executeFluent("domain", "delete", "localhost");

        assertThat(exitCode).isEqualTo(1);
    }

    @Test
    void domainExistCommandWithDefaultDomainShouldExist() {
        int exitCode = executeFluent("domain", "exist", "localhost");

        SoftAssertions.assertSoftly( softly -> {
            assertThat(exitCode).isEqualTo(0);
            assertThat(outputStreamCaptor.toString().trim()).isEqualTo("localhost exists");
        });
    }

    @Test
    void domainExistCommandWithNonExistingDomainShouldFail() {
        int exitCode = executeFluent("domain", "exist", "linagora.com");

        assertThat(exitCode).isEqualTo(0);
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("linagora.com does not exist");
    }

    @Test
    void domainExistCommandWithAddedDomainShouldSucceed() {
        executeFluent("domain", "create", "linagora.com");

        int exitCode = executeFluent("domain", "exist", "linagora.com");

        SoftAssertions.assertSoftly( softly -> {
            assertThat(exitCode).isEqualTo(0);
            assertThat(outputStreamCaptor.toString().trim()).isEqualTo("linagora.com exists");
        });
    }

    @Nested
    class DomainAliases {
        @BeforeEach
        void setUp() {
            executeFluent("domain", "create", "linagora.com");
            executeFluent("domain", "create", "linagora-james.com");
        }

        @Test
        void listDomainAliasShouldReturnEmptyByDefault() {
            int exitCode = executeFluent("domain", "listAliases", "linagora.com");

            SoftAssertions.assertSoftly( softly -> {
                assertThat(exitCode).isEqualTo(0);
                assertThat(outputStreamCaptor.toString().trim()).hasSize(0);
            });
        }

        @Test
        void addDomainAliasShouldBeIdempotent() {
            executeFluent("domain", "addAlias", "linagora.com", "linagora-james.com");
            int exitCode = executeFluent("domain", "addAlias", "linagora.com", "linagora-james.com");

            assertThat(exitCode).isEqualTo(0);
        }

        @Test
        void removeDomainAliasShouldBeIdempotent() {
            int exitCode = executeFluent("domain", "removeAlias", "linagora.com", "linagora-james.com");

            assertThat(exitCode).isEqualTo(0);
        }

        @Test
        void listDomainAliasShouldNotReturnRemovedValues() {
            executeFluent("domain", "addAlias", "linagora.com", "linagora-james.com");
            executeFluent("domain", "removeAlias", "linagora.com", "linagora-james.com");

            int exitCode = executeFluent("domain", "listAliases", "linagora.com");

            SoftAssertions.assertSoftly( softly -> {
                assertThat(exitCode).isEqualTo(0);
                assertThat(outputStreamCaptor.toString().trim()).hasSize(0);
            });
        }

        @Test
        void listDomainAliasShouldReturnAddedValues() {
            executeFluent("domain", "addAlias", "linagora.com", "linagora-james.com");

            int exitCode = executeFluent("domain", "listAliases", "linagora.com");

            SoftAssertions.assertSoftly( softly -> {
                assertThat(exitCode).isEqualTo(0);
                assertThat(outputStreamCaptor.toString().trim()).contains("linagora-james.com");
            });
        }

        @Test
        void addAliasShouldRequireAManageDomain() {
            int exitCode = executeFluent("domain", "addAlias", "linagora.com", "unknown.com");

            SoftAssertions.assertSoftly( softly -> {
                assertThat(exitCode).isEqualTo(1);
                assertThat(errorStreamCaptor.toString().trim()).contains("{\"statusCode\":404,\"type\":\"InvalidArgument\",\"message\":\"The domain list does not contain: unknown.com\",\"details\":null}");
            });
        }
    }

    private int executeFluent(String... args) {
        return WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            ImmutableList.<String>builder().add("--url", "http://127.0.0.1:" + port.getValue())
                .addAll(ImmutableList.copyOf(args))
                .build());
    }
}
