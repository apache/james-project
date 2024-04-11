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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class CrowdsecImapConnectionCheckTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    @Test
    void givenIPBannedByCrowdsecDecisionIp() throws IOException, InterruptedException {
        banIP("--ip", "127.0.0.3");
        CrowdsecClientConfiguration crowdsecClientConfiguration = new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), DEFAULT_API_KEY);

        CrowdsecImapConnectionCheck connectionCheck = new CrowdsecImapConnectionCheck(crowdsecClientConfiguration);
        connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800));
        assertThatThrownBy(() -> Mono.from(connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800))).block())
            .hasMessage("Ip 127.0.0.3 is not allowed to connect to IMAP server by Crowdsec");
    }

    @Test
    void givenIPBannedByCrowdsecDecisionIpRange() throws IOException, InterruptedException {
        banIP("--range", "127.0.0.1/24");
        CrowdsecClientConfiguration crowdsecClientConfiguration = new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), DEFAULT_API_KEY);

        CrowdsecImapConnectionCheck connectionCheck = new CrowdsecImapConnectionCheck(crowdsecClientConfiguration);
        connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800));
        assertThatThrownBy(() -> Mono.from(connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800))).block())
            .hasMessage("Ip 127.0.0.3 is not allowed to connect to IMAP server by Crowdsec");
    }

    @Test
    void givenIPNotBannedByCrowdsecDecisionIp() throws IOException, InterruptedException {
        banIP("--ip", "192.182.39.2");
        CrowdsecClientConfiguration crowdsecClientConfiguration = new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), DEFAULT_API_KEY);

        CrowdsecImapConnectionCheck connectionCheck = new CrowdsecImapConnectionCheck(crowdsecClientConfiguration);
        connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800));
        Mono.from(connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800))).block();
    }

    @Test
    void givenIPNotBannedByCrowdsecDecisionIpRange() throws IOException, InterruptedException {
        banIP("--range", "192.182.39.2/24");

        CrowdsecClientConfiguration crowdsecClientConfiguration = new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), DEFAULT_API_KEY);

        CrowdsecImapConnectionCheck connectionCheck = new CrowdsecImapConnectionCheck(crowdsecClientConfiguration);
        connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800));
        Mono.from(connectionCheck.validate(new InetSocketAddress("127.0.0.3", 8800))).block();
    }

    private static void banIP(String type, String value) throws IOException, InterruptedException {
        crowdsecExtension.getCrowdsecContainer().execInContainer("cscli", "decision", "add", type, value);
    }
}
