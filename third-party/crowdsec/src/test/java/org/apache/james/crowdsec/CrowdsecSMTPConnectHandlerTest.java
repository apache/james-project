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

import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.utils.BaseFakeSMTPSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CrowdsecSMTPConnectHandlerTest {
    @RegisterExtension
    static CrowdsecExtension crowdsecExtension = new CrowdsecExtension();

    private CrowdsecSMTPConnectHandler connectHandler;

    @BeforeEach
    void setUpEach() throws IOException {
        var crowdsecClientConfiguration = new CrowdsecClientConfiguration(crowdsecExtension.getLocalhostCrowdsecUrl(), CrowdsecClientConfiguration.DEFAULT_API_KEY);
        connectHandler = new CrowdsecSMTPConnectHandler(new CrowdsecService(crowdsecClientConfiguration));
    }

    @Test
    void givenIPBannedByCrowdsecDecision() throws IOException, InterruptedException {
        crowdsecExtension.banIP("--ip", "127.0.0.1");
        SMTPSession session = new BaseFakeSMTPSession() {
        };

        assertThat(connectHandler.onConnect(session)).isEqualTo(Response.DISCONNECT);
    }

    @Test
    void givenIPNotBannedByCrowdsecDecision() throws IOException, InterruptedException {
        crowdsecExtension.banIP("--range", "192.182.39.2/24");

        SMTPSession session = new BaseFakeSMTPSession() {
        };

        assertThat(connectHandler.onConnect(session)).isEqualTo(CrowdsecSMTPConnectHandler.NOOP);
    }
}