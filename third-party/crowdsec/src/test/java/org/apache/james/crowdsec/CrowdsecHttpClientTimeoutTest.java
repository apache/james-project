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

package org.apache.james.crowdsec;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.crowdsec.client.CrowdsecHttpClient;
import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrowdsecHttpClientTimeoutTest {
    private static final String IP = "1.2.3.4";

    // Accepts connections and never answers
    private ServerSocket silentServer;

    @BeforeEach
    void setUp() throws Exception {
        silentServer = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    @AfterEach
    void tearDown() throws Exception {
        silentServer.close();
    }

    private CrowdsecHttpClient client(boolean failClosed) throws Exception {
        return new CrowdsecHttpClient(new CrowdsecClientConfiguration(
            new URI("http://127.0.0.1:" + silentServer.getLocalPort() + "/v1").toURL(),
            CrowdsecClientConfiguration.DEFAULT_API_KEY,
            Duration.ofMillis(200),
            failClosed));
    }

    @Test
    void timeoutShouldNotBanByDefault() throws Exception {
        assertThat(client(false).getCrowdsecDecisions(IP).block()).isEmpty();
    }

    @Test
    void timeoutShouldBanTheClientWhenFailClosed() throws Exception {
        assertThat(client(true).getCrowdsecDecisions(IP).block())
            .singleElement()
            .satisfies(decision -> assertThat(CrowdsecUtils.isBanned(decision, IP)).isTrue())
            .extracting(CrowdsecDecision::getType)
            .isEqualTo(CrowdsecDecision.BAN);
    }
}
