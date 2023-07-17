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

package org.apache.james.rspamd.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Optional;

import org.apache.james.core.healthcheck.Result;
import org.apache.james.rspamd.DockerRspamdExtension;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class RspamdHealthcheckTest {
    @RegisterExtension
    static DockerRspamdExtension rspamdExtension = new DockerRspamdExtension();

    private RspamdHealthCheck rspamdHealthCheck;

    @BeforeEach
    void setUp() {
        if (rspamdExtension.dockerRspamd().isPaused()) {
            rspamdExtension.dockerRspamd().unPause();
        }

        RspamdClientConfiguration configuration = new RspamdClientConfiguration(rspamdExtension.getBaseUrl(), "passwordDoesNotMatter", Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);
        rspamdHealthCheck = new RspamdHealthCheck(client);
    }

    @Test
    void checkShouldReturnHealthyWhenRspamdIsRunning() {
        Result check = Mono.from(rspamdHealthCheck.check()).block();

        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenRspamdIsDown() {
        rspamdExtension.dockerRspamd().pause();
        Result check = Mono.from(rspamdHealthCheck.check()).block();

        assertThat(check.isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenWrongRspamdURL() throws Exception {
        RspamdClientConfiguration configuration = new RspamdClientConfiguration(new URL("http://wrongRspamdURL:11334"), "passwordDoesNotMatter", Optional.empty());
        RspamdHttpClient client = new RspamdHttpClient(configuration);
        rspamdHealthCheck = new RspamdHealthCheck(client);

        Result check = Mono.from(rspamdHealthCheck.check()).block();

        assertThat(check.isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnHealthyWhenRspamdIsRecovered() {
        rspamdExtension.dockerRspamd().pause();
        rspamdExtension.dockerRspamd().unPause();
        Result check = Mono.from(rspamdHealthCheck.check()).block();

        assertThat(check.isHealthy()).isTrue();
    }
}
