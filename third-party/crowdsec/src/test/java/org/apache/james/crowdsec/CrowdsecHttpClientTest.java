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

import static org.apache.james.crowdsec.CrowdsecExtension.CROWDSEC_PORT;
import static org.apache.james.crowdsec.client.CrowdsecClientConfiguration.DEFAULT_API_KEY;
import static org.apache.james.crowdsec.model.CrowdsecDecision.BAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.crowdsec.client.CrowdsecHttpClient;
import org.apache.james.crowdsec.model.CrowdsecDecision;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CrowdsecHttpClientTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    @Test
    void getDecisionsWhenBanningAnIP() throws IOException, InterruptedException {
        banIP("--ip", "192.168.0.4");
        int port = crowdsecExtension.getCrowdsecContainer().getMappedPort(CROWDSEC_PORT);
        CrowdsecClientConfiguration config = new CrowdsecClientConfiguration(new URL("http://localhost:" + port + "/v1"), DEFAULT_API_KEY);
        CrowdsecHttpClient httpClient = new CrowdsecHttpClient(config);
        List<CrowdsecDecision> decisions = httpClient.getCrowdsecDecisions().block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(decisions).hasSize(1);
            softly.assertThat(decisions.get(0).getValue()).isEqualTo("192.168.0.4");
            softly.assertThat(decisions.get(0).getType()).isEqualTo(BAN);
        });
    }

    @Test
    void getDecisionsWhenBanningAnIPRange() throws IOException, InterruptedException {
        banIP("--range", "192.168.0.0/16");
        int port = crowdsecExtension.getCrowdsecContainer().getMappedPort(CROWDSEC_PORT);
        CrowdsecClientConfiguration config = new CrowdsecClientConfiguration(new URL("http://localhost:" + port + "/v1"), DEFAULT_API_KEY);
        CrowdsecHttpClient httpClient = new CrowdsecHttpClient(config);
        List<CrowdsecDecision> decisions = httpClient.getCrowdsecDecisions().block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(decisions).hasSize(1);
            softly.assertThat(decisions.get(0).getValue()).isEqualTo("192.168.0.0/16");
            softly.assertThat(decisions.get(0).getType()).isEqualTo(BAN);
        });
    }

    @Test
    void getDecisionsWithWrongApiKey() throws IOException, InterruptedException {
        banIP("--range", "192.168.0.0/16");
        int port = crowdsecExtension.getCrowdsecContainer().getMappedPort(CROWDSEC_PORT);
        CrowdsecClientConfiguration config = new CrowdsecClientConfiguration(new URL("http://localhost:" + port + "/v1"), "wrong-key");
        CrowdsecHttpClient httpClient = new CrowdsecHttpClient(config);

        assertThatThrownBy(() -> httpClient.getCrowdsecDecisions().block())
            .hasMessage("Invalid api-key bouncer");
    }

    @Test
    void getDecisionsWhenNoBanning() throws IOException {
        int port = crowdsecExtension.getCrowdsecContainer().getMappedPort(CROWDSEC_PORT);
        CrowdsecClientConfiguration config = new CrowdsecClientConfiguration(new URL("http://localhost:" + port + "/v1"), DEFAULT_API_KEY);
        CrowdsecHttpClient httpClient = new CrowdsecHttpClient(config);
        List<CrowdsecDecision> decisions = httpClient.getCrowdsecDecisions().block();

        assertThat(decisions).isEmpty();
    }

    @Test
    void getDecisionsWhenBanningMultipleIP() throws IOException, InterruptedException {
        banIP("--ip", "192.168.0.4");
        banIP("--ip", "192.168.0.5");
        int port = crowdsecExtension.getCrowdsecContainer().getMappedPort(CROWDSEC_PORT);
        CrowdsecClientConfiguration config = new CrowdsecClientConfiguration(new URL("http://localhost:" + port + "/v1"), DEFAULT_API_KEY);
        CrowdsecHttpClient httpClient = new CrowdsecHttpClient(config);
        List<CrowdsecDecision> decisions = httpClient.getCrowdsecDecisions().block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(decisions).hasSize(2);
            softly.assertThat(decisions).extracting(CrowdsecDecision::getValue)
                .containsExactlyInAnyOrder("192.168.0.4", "192.168.0.5");
            softly.assertThat(decisions).extracting(CrowdsecDecision::getType)
                .containsOnly(BAN);
        });
    }

    private static void banIP(String type, String value) throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli", "decision", "add", type, value);
    }
}