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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsKeystoreTest {
    private static void copyShippedImapConfiguration(Path confDirectory) throws Exception {
        // The shipped configuration, not the one of the tests
        Files.copy(Path.of("src/main/resources/imapserver.xml"), confDirectory.resolve("imapserver.xml"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void ensureImapKeystoresShouldGenerateTheKeystoreOfTheShippedConfiguration(@TempDir Path confDirectory) throws Exception {
        copyShippedImapConfiguration(confDirectory);

        TlsKeystore.ensureImapKeystores(confDirectory);

        Path keystoreFile = confDirectory.resolve("keystore");
        assertThat(Files.getPosixFilePermissions(keystoreFile))
            .containsOnly(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keystoreFile)) {
            keyStore.load(in, "yoursecret".toCharArray());
        }
        assertThat(keyStore.isKeyEntry("james")).isTrue();
    }

    @Test
    void ensureImapKeystoresShouldKeepAnExistingKeystore(@TempDir Path confDirectory) throws Exception {
        copyShippedImapConfiguration(confDirectory);
        Path keystoreFile = Files.writeString(confDirectory.resolve("keystore"), "mine");

        TlsKeystore.ensureImapKeystores(confDirectory);

        assertThat(Files.readString(keystoreFile)).isEqualTo("mine");
    }
}
