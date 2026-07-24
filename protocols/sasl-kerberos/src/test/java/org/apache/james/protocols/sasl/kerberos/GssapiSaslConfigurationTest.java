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

package org.apache.james.protocols.sasl.kerberos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GssapiSaslConfigurationTest {
    private static final String SERVICE_NAME = "imap";
    private static final String SERVER_NAME = "mail.example.com";
    private static final String PRINCIPAL = "imap/mail.example.com@EXAMPLE.COM";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRequireGssapiConfiguration() {
        assertThatThrownBy(() -> GssapiSaslConfiguration.from(new BaseHierarchicalConfiguration()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("GSSAPI SASL mechanism requires an auth.gssapi configuration");
    }

    @Test
    void shouldDefaultToRequiringSsl() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));

        GssapiSaslConfiguration configuration = GssapiSaslConfiguration.from(configuration(keyTab.toString()));

        assertThat(configuration).isEqualTo(new GssapiSaslConfiguration(
            SERVICE_NAME, SERVER_NAME, PRINCIPAL, keyTab.toAbsolutePath(), true));
    }

    @Test
    void shouldReadGlobalSslRequirement() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));
        BaseHierarchicalConfiguration serverConfiguration = configuration(keyTab.toString());
        serverConfiguration.addProperty("auth.requireSSL", false);

        assertThat(GssapiSaslConfiguration.from(serverConfiguration).requireSSL()).isFalse();
    }

    @Test
    void shouldSupportFileUri() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));

        assertThat(GssapiSaslConfiguration.from(configuration(keyTab.toUri().toString())).keyTab())
            .isEqualTo(keyTab.toAbsolutePath());
    }

    @Test
    void shouldRejectMissingRequiredValue() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));
        BaseHierarchicalConfiguration serverConfiguration = configuration(keyTab.toString());
        serverConfiguration.clearProperty("auth.gssapi.serverName");

        assertThatThrownBy(() -> GssapiSaslConfiguration.from(serverConfiguration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.serverName must be specified");
    }

    @Test
    void shouldRejectNonRegularKeyTab() {
        assertThatThrownBy(() -> GssapiSaslConfiguration.from(configuration(temporaryDirectory.toString())))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.keyTab must reference a readable regular file");
    }

    @Test
    void shouldRejectContradictoryServicePrincipal() throws Exception {
        Path keyTab = Files.createFile(temporaryDirectory.resolve("imap.keytab"));
        BaseHierarchicalConfiguration serverConfiguration = configuration(keyTab.toString());
        serverConfiguration.setProperty("auth.gssapi.principal", "smtp/mail.example.com@EXAMPLE.COM");

        assertThatThrownBy(() -> GssapiSaslConfiguration.from(serverConfiguration))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("auth.gssapi.principal must match the configured serviceName and serverName");
    }

    private BaseHierarchicalConfiguration configuration(String keyTab) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.gssapi.serviceName", SERVICE_NAME);
        configuration.addProperty("auth.gssapi.serverName", SERVER_NAME);
        configuration.addProperty("auth.gssapi.principal", PRINCIPAL);
        configuration.addProperty("auth.gssapi.keyTab", keyTab);
        return configuration;
    }
}
