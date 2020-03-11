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

package org.apache.james.jmap.draft.crypto;

import static org.apache.james.jmap.draft.crypto.JamesSignatureHandlerFixture.JWT_PUBLIC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.security.KeyStoreException;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystemFixture;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityKeyLoaderTest {

    @Test
    void loadShouldThrowWhenJMAPIsNotEnabled() throws Exception {
        JMAPDraftConfiguration jmapConfiguration = JMAPDraftConfiguration.builder()
            .disable()
            .jwtPublicKeyPem(Optional.of(JWT_PUBLIC_KEY))
            .keystore("keystore")
            .secret("james72laBalle")
            .build();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapConfiguration);

        assertThatThrownBy(loader::load)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("JMAP is not enabled");
    }

    @Test
    void loadShouldThrowWhenWrongKeystore() throws Exception {
        JMAPDraftConfiguration jmapDraftConfiguration = JMAPDraftConfiguration.builder()
            .enable()
            .jwtPublicKeyPem(Optional.of(JWT_PUBLIC_KEY))
            .keystore("badAliasKeystore")
            .secret("password")
            .build();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapDraftConfiguration);

        assertThatThrownBy(loader::load)
            .isInstanceOf(KeyStoreException.class)
            .hasMessage("Alias 'james' keystore can't be found");
    }

    @Test
    void loadShouldThrowWhenWrongPassword() throws Exception {
        JMAPDraftConfiguration jmapDraftConfiguration = JMAPDraftConfiguration.builder()
            .enable()
            .jwtPublicKeyPem(Optional.of(JWT_PUBLIC_KEY))
            .keystore("keystore")
            .secret("WrongPassword")
            .build();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapDraftConfiguration);

        assertThatThrownBy(loader::load)
            .isInstanceOf(IOException.class)
            .hasMessage("Keystore was tampered with, or password was incorrect");
    }

    @Test
    void loadShouldReturnAsymmetricKeysWhenCorrectPassword() throws Exception {
        JMAPDraftConfiguration jmapDraftConfiguration = JMAPDraftConfiguration.builder()
            .enable()
            .jwtPublicKeyPem(Optional.of(JWT_PUBLIC_KEY))
            .keystore("keystore")
            .secret("james72laBalle")
            .build();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapDraftConfiguration);

        assertThat(loader.load())
            .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "keystoreJava7",
        "keystoreJava11",
    })
    void loadShouldReturnAsymmetricKeysWhenUsingKeyStoreGeneratedByDifferentJavaVersions(
        String keyStoreInDifferentVersion) throws Exception {

        JMAPDraftConfiguration jmapDraftConfiguration = JMAPDraftConfiguration.builder()
            .enable()
            .jwtPublicKeyPem(Optional.of(JWT_PUBLIC_KEY))
            .keystore(keyStoreInDifferentVersion)
            .secret("james72laBalle")
            .build();

        SecurityKeyLoader loader = new SecurityKeyLoader(
            FileSystemFixture.CLASSPATH_FILE_SYSTEM,
            jmapDraftConfiguration);

        assertThat(loader.load())
            .isNotNull();
    }
}