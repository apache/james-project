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

import static org.apache.james.crowdsec.client.CrowdsecClientConfiguration.DEFAULT_API_KEY;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import javax.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.crowdsec.client.CrowdsecClientConfiguration;
import org.apache.james.crowdsec.client.CrowdsecHttpClient;
import org.apache.james.util.docker.RateLimiters;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.utility.MountableFile;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

public class CrowdsecExtension implements GuiceModuleTestExtension {
    public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    public static final int CROWDSEC_PORT = 8080;
    public static final String CROWDSEC_IMAGE = "crowdsecurity/crowdsec:v1.5.4";

    private final GenericContainer<?> crowdsecContainer;

    public CrowdsecExtension() {
        this.crowdsecContainer = new GenericContainer<>(CROWDSEC_IMAGE)
            .withCreateContainerCmdModifier(cmd -> cmd.withName("james-crowdsec-test-" + UUID.randomUUID()))
            .withExposedPorts(CROWDSEC_PORT)
            .withStartupTimeout(STARTUP_TIMEOUT)
            .withCopyFileToContainer(MountableFile.forClasspathResource("crowdsec/acquis.yaml"), "/etc/crowdsec/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("crowdsec/parsers/syslog-logs.yaml"), "/etc/crowdsec/parsers/s00-raw/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("crowdsec/parsers/james-auth.yaml"), "/etc/crowdsec/parsers/s01-parse/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("crowdsec/scenarios/james-bf-auth.yaml"), "/etc/crowdsec/scenarios/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("crowdsec/scenarios/james-dictionary-attack.yaml"), "/etc/crowdsec/scenarios/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("crowdsec/collections/james.yaml"), "/etc/crowdsec/collections/")
            .withFileSystemBind("src/test/resources/log", "/var/log", BindMode.READ_WRITE)
            .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!crowdsecContainer.isRunning()) {
            crowdsecContainer.start();
        }

        setApiKey();
    }

    private void setApiKey() throws IOException, InterruptedException {
        crowdsecContainer.execInContainer("cscli", "bouncer", "add", "bouncer", "-k", DEFAULT_API_KEY);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws IOException, InterruptedException {
        resetCrowdSecBanDecisions();
        resetJamesLogFile();
    }

    private void resetCrowdSecBanDecisions() throws IOException, InterruptedException {
        crowdsecContainer.execInContainer("cscli", "decision", "delete", "--all");
    }

    private void resetJamesLogFile() throws IOException, InterruptedException {
        crowdsecContainer.execInContainer("truncate", "-s", "0", "/var/log/james.log");
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {

    }

    @Override
    public Module getModule() {
        URL crowdSecUrl = getCrowdSecUrl();

        return new AbstractModule() {
            @Provides
            @Singleton
            public CrowdsecClientConfiguration crowdsecClientConfiguration() {
                return new CrowdsecClientConfiguration(crowdSecUrl, DEFAULT_API_KEY);
            }

            @Provides
            @Singleton
            public CrowdsecHttpClient crowdsecHttpClient(CrowdsecClientConfiguration crowdsecClientConfiguration) {
                return new CrowdsecHttpClient(crowdsecClientConfiguration);
            }
        };
    }

    public URL getCrowdSecUrl() {
        return Throwing.supplier(() -> new URL("http",
            crowdsecContainer.getHost(),
            crowdsecContainer.getMappedPort(CROWDSEC_PORT),
            "/v1")).get();
    }

    public GenericContainer<?> getCrowdsecContainer() {
        return crowdsecContainer;
    }
}
