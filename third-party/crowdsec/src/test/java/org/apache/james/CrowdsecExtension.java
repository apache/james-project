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

package org.apache.james;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.apache.james.util.docker.RateLimiters;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.google.common.collect.ImmutableList;

public class CrowdsecExtension implements GuiceModuleTestExtension {
    public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    public static final int CROWDSEC_PORT = 8080;
    public static final int EXPOSED_PORT = 8082;
    public static final String CROWDSEC_IMAGE = "crowdsecurity/crowdsec:v1.5.4";

    private final GenericContainer<?> crowdsecContainer;

    public CrowdsecExtension() {
        this.crowdsecContainer = new GenericContainer<>(CROWDSEC_IMAGE)
            .withCreateContainerCmdModifier(cmd -> cmd.withName("james-crowdsec-test-" + UUID.randomUUID()))
            .withExposedPorts(CROWDSEC_PORT)
            .withStartupTimeout(STARTUP_TIMEOUT)
            .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!crowdsecContainer.isRunning()) {
            crowdsecContainer.start();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws IOException, InterruptedException {
        crowdsecContainer.execInContainer("cscli", "decision", "delete", "--all");
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {

    }

    public GenericContainer<?> getCrowdsecContainer() {
        return crowdsecContainer;
    }
}
