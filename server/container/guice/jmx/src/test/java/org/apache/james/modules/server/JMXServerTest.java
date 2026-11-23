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

package org.apache.james.modules.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JMXServerTest {
    @Test
    void createOwnerOnlyFileShouldCreateAFileReadableByItsOwnerOnly(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("jmxremote.password");

        JMXServer.createOwnerOnlyFile(file, "james-admin secret\n");

        assertThat(Files.getPosixFilePermissions(file))
            .containsOnly(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("james-admin secret\n");
    }

    @Test
    void createOwnerOnlyFileShouldNotFollowASymbolicLink(@TempDir Path directory) throws Exception {
        Path target = Files.createFile(directory.resolve("target"));
        Path link = Files.createSymbolicLink(directory.resolve("jmxremote.password"), target);

        assertThatThrownBy(() -> JMXServer.createOwnerOnlyFile(link, "james-admin secret\n"))
            .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEmpty();
    }
}
