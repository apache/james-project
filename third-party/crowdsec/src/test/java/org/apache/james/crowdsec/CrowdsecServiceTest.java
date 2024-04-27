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

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CrowdsecServiceTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    private final InetSocketAddress remoteAddress = new InetSocketAddress("localhost", 22);

    private CrowdsecService service;

    @BeforeEach
    void setUpEach() throws IOException {
        service = new CrowdsecService(new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), CrowdsecClientConfiguration.DEFAULT_API_KEY));
    }

    @Test
    void givenIPBannedByCrowdsecDecisionIp() throws IOException, InterruptedException {
        banIP("--ip", "127.0.0.1");
        var banDecisions = service.findBanDecisions(remoteAddress).block();
        assertThat(banDecisions).hasSize(1);
        assertThat(banDecisions.get(0).getScope()).isEqualTo("Ip");
        assertThat(banDecisions.get(0).getValue()).isEqualTo("127.0.0.1");
    }

    @Test
    void givenIPBannedByCrowdsecDecisionIpRange() throws IOException, InterruptedException {
        banIP("--range", "127.0.0.1/24");
        var banDecisions = service.findBanDecisions(remoteAddress).block();
        assertThat(banDecisions).hasSize(1);
        assertThat(banDecisions.get(0).getScope()).isEqualTo("Range");
        assertThat(banDecisions.get(0).getValue()).isEqualTo("127.0.0.1/24");
    }

    @Test
    void givenIPNotBannedByCrowdsecDecisionIp() throws IOException, InterruptedException {
        banIP("--ip", "192.182.39.2");
        var banDecisions = service.findBanDecisions(remoteAddress).block();
        assertThat(banDecisions).isEmpty();
    }

    @Test
    void givenIPNotBannedByCrowdsecDecisionIpRange() throws IOException, InterruptedException {
        banIP("--range", "192.182.39.2/24");
        var banDecisions = service.findBanDecisions(remoteAddress).block();
        assertThat(banDecisions).isEmpty();
    }

    private static void banIP(String type, String value) throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli", "decision", "add", type, value);
    }
}