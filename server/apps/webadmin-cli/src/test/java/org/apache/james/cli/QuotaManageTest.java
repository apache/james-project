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

class QuotaManageTest {
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

    @Nested
    class Global {
        @Nested
        class Count {
            @Test
            void getShouldReturnNoneByDefault() {
                int exitCode = executeFluent("quota", "global", "count", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("No global quota defined".toCharArray());
                });
            }

            @Test
            void getShouldReturnSetValue() {
                executeFluent("quota", "global", "count", "set", "128");

                int exitCode = executeFluent("quota", "global", "count", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("128".toCharArray());
                });
            }

            @Test
            void getShouldNotReturnDeletedValue() {
                executeFluent("quota", "global", "count", "set", "128");

                executeFluent("quota", "global", "count", "delete");

                int exitCode = executeFluent("quota", "global", "count", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("No global quota defined".toCharArray());
                });
            }

            @Test
            void deleteShouldBeIdempotent() {
                executeFluent("quota", "global", "count", "delete");
                int exitCode = executeFluent("quota", "global", "count", "delete");

                assertThat(exitCode).isEqualTo(0);
            }

            @Test
            void setShouldRespectLastWriteWin() {
                executeFluent("quota", "global", "count", "set", "128");
                executeFluent("quota", "global", "count", "set", "256");

                int exitCode = executeFluent("quota", "global", "count", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("256".toCharArray());
                });
            }
        }

        @Nested
        class Size {
            @Test
            void getShouldReturnNoneByDefault() {
                int exitCode = executeFluent("quota", "global", "size", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("No global quota defined".toCharArray());
                });
            }

            @Test
            void getShouldReturnSetValue() {
                executeFluent("quota", "global", "size", "set", "128");

                int exitCode = executeFluent("quota", "global", "size", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("128 bytes".toCharArray());
                });
            }

            @Test
            void unitsShouldBeSupported() {
                executeFluent("quota", "global", "size", "set", "128M");

                int exitCode = executeFluent("quota", "global", "size", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("128 MB".toCharArray());
                });
            }

            @Test
            void getShouldNotReturnDeletedValue() {
                executeFluent("quota", "global", "size", "set", "128");

                executeFluent("quota", "global", "size", "delete");

                int exitCode = executeFluent("quota", "global", "size", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("No global quota defined".toCharArray());
                });
            }

            @Test
            void deleteShouldBeIdempotent() {
                executeFluent("quota", "global", "size", "delete");
                int exitCode = executeFluent("quota", "global", "size", "delete");

                assertThat(exitCode).isEqualTo(0);
            }

            @Test
            void setShouldRespectLastWriteWin() {
                executeFluent("quota", "global", "size", "set", "128");
                executeFluent("quota", "global", "size", "set", "256");

                int exitCode = executeFluent("quota", "global", "size", "get");

                SoftAssertions.assertSoftly(softly -> {
                    assertThat(exitCode).isEqualTo(0);
                    assertThat(outputStreamCaptor.toString().trim().toCharArray()).containsOnly("256 bytes".toCharArray());
                });
            }
        }
    }

    private int executeFluent(String... args) {
        return WebAdminCli.executeFluent(new PrintStream(outputStreamCaptor), new PrintStream(errorStreamCaptor),
            ImmutableList.<String>builder().add("--url", "http://127.0.0.1:" + port.getValue())
                .addAll(ImmutableList.copyOf(args))
                .build());
    }
}
