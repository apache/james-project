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

import org.assertj.core.api.Assertions;
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
        Assertions.assertThat(getSASLMechanisms(initialGreeting)).containsExactlyInAnyOrder("PLAIN");

        client.sendCommand("CAPABILITY");
        ManageSieveClient.ServerResponse capabilityResponse = client.readResponse();
        Assertions.assertThat(getSASLMechanisms(capabilityResponse)).containsExactlyInAnyOrder("PLAIN");
    }

    @Test
    void shouldAnnouncePlainAndOauthWhenConfigured() throws Exception {
        this.testSystem.setUp("managesieveserver-oidc.xml");

        ManageSieveClient client = new ManageSieveClient();
        client.connect(this.testSystem.getBindedIP(), this.testSystem.getBindedPort());
        ManageSieveClient.ServerResponse initialGreeting = client.readResponse();
        Assertions.assertThat(getSASLMechanisms(initialGreeting)).containsExactlyInAnyOrder("PLAIN", "XOAUTH2", "OAUTHBEARER");

        client.sendCommand("CAPABILITY");
        ManageSieveClient.ServerResponse capabilityResponse = client.readResponse();
        Assertions.assertThat(getSASLMechanisms(capabilityResponse)).containsExactlyInAnyOrder("PLAIN", "XOAUTH2", "OAUTHBEARER");
    }

    private String[] getSASLMechanisms(ManageSieveClient.ServerResponse response) {
        String saslLine = Assertions.assertThat(response.responseLines())
            .filteredOn(line -> line.startsWith("\"SASL\""))
            .hasSize(1)
            .first()
            .actual();
        return saslLine.substring("\"SASL\" \"".length(), saslLine.length() - 1).split(" ");
    }
}
