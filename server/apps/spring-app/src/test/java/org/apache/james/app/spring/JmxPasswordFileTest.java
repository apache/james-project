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

package org.apache.james.app.spring;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JmxPasswordFileTest {
    @Test
    void ensurePasswordShouldGenerateAPasswordWhenTheFileHoldsNone(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("jmxremote.password"), "# james-admin <password>\n", UTF_8);

        JmxPasswordFile.ensurePassword(file);

        assertThat(Files.readString(file, UTF_8)).matches("# james-admin <password>\njames-admin [a-zA-Z0-9]{20}\n");
        assertThat(Files.getPosixFilePermissions(file))
            .containsOnly(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test
    void ensurePasswordShouldReplaceThePublishedPassword(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("jmxremote.password"), "james-admin changeme\n", UTF_8);

        JmxPasswordFile.ensurePassword(file);

        assertThat(Files.readString(file, UTF_8)).doesNotContain("changeme");
    }

    @Test
    void ensurePasswordShouldKeepAPasswordOfTheInstallation(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("jmxremote.password"), "james-admin my-own-password\n", UTF_8);

        JmxPasswordFile.ensurePassword(file);

        assertThat(Files.readString(file, UTF_8)).isEqualTo("james-admin my-own-password\n");
    }

    @Test
    void tryEnsurePasswordShouldNotFailWhenTheConfDirectoryIsReadOnly(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("jmxremote.password"), "# james-admin <password>\n", UTF_8);
        Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            assertThat(JmxPasswordFile.tryEnsurePassword(file)).isFalse();
        } finally {
            Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    void ensurePasswordShouldKeepTheOtherLinesWhenReplacingThePublishedPassword(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("jmxremote.password"),
            "# JMX credentials\njames-admin changeme\nmonitor monitor-password\n", UTF_8);

        JmxPasswordFile.ensurePassword(file);

        assertThat(Files.readAllLines(file, UTF_8))
            .contains("# JMX credentials", "monitor monitor-password")
            .doesNotContain("james-admin changeme")
            .anyMatch(line -> line.matches("james-admin [a-zA-Z0-9]{20}"));
    }
}
