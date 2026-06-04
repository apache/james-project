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

package org.apache.james.imapserver.netty;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import nl.altindag.ssl.exception.GenericKeyStoreException;
import nl.altindag.ssl.pem.exception.PrivateKeyParseException;


@SuppressWarnings("checkstyle:membername")
class IMAPServerSSLConfigurationTest extends AbstractIMAPServerTest {
    IMAPServer imapServer;

    @AfterEach
    void tearDown() {
        if (imapServer != null) {
            imapServer.destroy();
        }
    }

    @Test
    void initShouldAcceptJKSFormat() {
        assertThatCode(() -> imapServer = createImapServer("imapServerSslJKS.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldAcceptPKCS12Format() {
        assertThatCode(() -> imapServer = createImapServer("imapServerSslPKCS12.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldAcceptPEMKeysWithPassword() {
        assertThatCode(() -> imapServer = createImapServer("imapServerSslPEM.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldAcceptPEMKeysWithoutPassword() {
        assertThatCode(() -> imapServer = createImapServer("imapServerSslPEMNoPass.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldAcceptJKSByDefault() {
        assertThatCode(() -> imapServer = createImapServer("imapServerSslDefaultJKS.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldThrowWhenSslEnabledWithoutKeys() {
        assertThatThrownBy(() -> createImapServer("imapServerSslNoKeys.xml"))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("keystore or (privateKey and certificates) needs to get configured");
    }

    @Test
    void initShouldThrowWhenJKSWithBadPassword() {
        assertThatThrownBy(() -> createImapServer("imapServerSslJKSBadPassword.xml"))
            .isInstanceOf(GenericKeyStoreException.class)
            .hasMessageContaining("keystore password was incorrect");
    }

    @Test
    void initShouldThrowWhenPEMWithBadPassword() {
        assertThatThrownBy(() -> createImapServer("imapServerSslPEMBadPass.xml"))
            .isInstanceOf(PrivateKeyParseException.class);
    }

    @Test
    void initShouldThrowWhenPEMWithMissingPassword() {
        assertThatThrownBy(() -> createImapServer("imapServerSslPEMMissingPass.xml"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A password is mandatory with an encrypted key");
    }

    @Test
    void initShouldNotThrowWhenPEMWithExtraPassword() {
        assertThatCode(() -> imapServer = createImapServer("imapServerSslPEMExtraPass.xml"))
            .doesNotThrowAnyException();
    }

    @Test
    void initShouldThrowWhenJKSWenNotFound() {
        assertThatThrownBy(() -> createImapServer("imapServerSslJKSNotFound.xml"))
            .isInstanceOf(FileNotFoundException.class)
            .hasMessage("class path resource [keystore.notfound.jks] cannot be resolved to URL because it does not exist");
    }

    @Test
    void initShouldThrowWhenPKCS12WithBadPassword() {
        assertThatThrownBy(() -> createImapServer("imapServerSslPKCS12WrongPassword.xml"))
            .isInstanceOf(GenericKeyStoreException.class)
            .hasMessageContaining("keystore password was incorrect");
    }

    @Test
    void initShouldThrowWhenPKCS12WithMissingPassword() {
        assertThatThrownBy(() -> createImapServer("imapServerSslPKCS12MissingPassword.xml"))
            .isInstanceOf(GenericKeyStoreException.class)
            .hasMessageContaining("keystore password was incorrect");
    }
}
