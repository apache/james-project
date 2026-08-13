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

package org.apache.james.managesieveserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.EOFException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CapabilityTest {
    private final ManageSieveServerTestSystem testSystem;

    public CapabilityTest() throws Exception {
        this.testSystem = new ManageSieveServerTestSystem();
    }

    @AfterEach
    void tearDown() {
        this.testSystem.manageSieveServer.destroy();
    }

    @Test
    void shouldAnnounceOnlyPlainAuthenticationWithDefaultConfig() throws Exception {
        this.testSystem.setUp();

        ManageSieveClient client = new ManageSieveClient();
        client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        ManageSieveClient.ServerResponse initialGreeting = client.readResponse();
        assertThat(getSASLMechanisms(initialGreeting)).containsExactlyInAnyOrder("PLAIN");

        client.sendCommand("CAPABILITY");
        ManageSieveClient.ServerResponse capabilityResponse = client.readResponse();
        assertThat(getSASLMechanisms(capabilityResponse)).containsExactlyInAnyOrder("PLAIN");
    }

    @Test
    void shouldAnnouncePlainAndOauthWhenConfigured() throws Exception {
        this.testSystem.setUp("managesieveserver-oidc.xml");

        ManageSieveClient client = new ManageSieveClient();
        client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        ManageSieveClient.ServerResponse initialGreeting = client.readResponse();
        assertThat(getSASLMechanisms(initialGreeting)).containsExactlyInAnyOrder("PLAIN", "XOAUTH2", "OAUTHBEARER");

        client.sendCommand("CAPABILITY");
        ManageSieveClient.ServerResponse capabilityResponse = client.readResponse();
        assertThat(getSASLMechanisms(capabilityResponse)).containsExactlyInAnyOrder("PLAIN", "XOAUTH2", "OAUTHBEARER");
    }

    @Test
    void shouldNotAnnounceOrAuthPlainOnClearTextWhenSslIsRequired() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = FileConfigurationProvider.getConfig(
            ClassLoader.getSystemResourceAsStream("managesieveserver.xml"));
        configuration.addProperty("auth.requireSSL", true);
        this.testSystem.setUp(configuration);

        ManageSieveClient client = new ManageSieveClient();
        client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        client.readResponse();

        client.sendCommand("CAPABILITY");
        ManageSieveClient.ServerResponse capabilityResponse = client.readResponse();
        // RFC 5804 section 1.7 only permits an empty SASL capability when STARTTLS is advertised (which is not the case here)
        assertThat(capabilityResponse.responseLines()).noneMatch(line -> line.startsWith("\"SASL\""));

        client.sendCommand("AUTHENTICATE \"PLAIN\"");
        ManageSieveClient.ServerResponse authenticationResponse = client.readResponse();
        assertThat(authenticationResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(authenticationResponse.responseCode()).contains("ENCRYPT-NEEDED");
    }

    @Test
    void shouldNotAnnounceOrAcceptStartTlsWhenTlsIsUnavailable() throws Exception {
        this.testSystem.setUp();

        ManageSieveClient client = new ManageSieveClient();
        client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        ManageSieveClient.ServerResponse initialGreeting = client.readResponse();
        assertThat(initialGreeting.responseLines()).doesNotContain("\"STARTTLS\"");

        client.sendCommand("CAPABILITY");
        ManageSieveClient.ServerResponse capabilityResponse = client.readResponse();
        assertThat(capabilityResponse.responseLines()).doesNotContain("\"STARTTLS\"");

        client.sendCommand("STARTTLS");
        ManageSieveClient.ServerResponse startTlsResponse = client.readResponse();
        assertThat(startTlsResponse.responseType()).isEqualTo(ManageSieveClient.ResponseType.NO);
        assertThat(startTlsResponse.explanation()).contains("STARTTLS is not available");
    }

    @Test
    void shouldNotHangConnectionWhenAnotherCommandFollowsRejectedStartTls() throws Exception {
        this.testSystem.setUp();

        ManageSieveClient client = new ManageSieveClient();
        client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        client.readResponse();

        client.sendCommand("STARTTLS\r\nNOOP");

        assertThatThrownBy(client::readResponse).isInstanceOf(EOFException.class);
    }

    private String[] getSASLMechanisms(ManageSieveClient.ServerResponse response) {
        String saslLine = assertThat(response.responseLines())
            .filteredOn(line -> line.startsWith("\"SASL\""))
            .hasSize(1)
            .first()
            .actual();
        return saslLine.substring("\"SASL\" \"".length(), saslLine.length() - 1).split(" ");
    }
}
