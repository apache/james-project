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
package org.apache.james.mpt.imapmailbox.external.james;

import java.io.IOException;

import org.apache.james.mpt.imapmailbox.external.james.host.ProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.StaticJamesConfiguration;
import org.apache.james.mpt.imapmailbox.external.james.host.docker.CliProvisioningAPI;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.util.Port;
import org.apache.james.util.docker.DockerContainer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

public class DockerJamesRule implements TestRule, BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerJamesRule.class);

    private static final int IMAP_PORT = 143;
    private static final int SMTP_PORT = 587;
    private static final int WEBADMIN_PORT = 8000;

    private final DockerContainer container;

    public DockerJamesRule(String image) {
        container = DockerContainer.fromName(image)
            .withExposedPorts(SMTP_PORT, IMAP_PORT)
            .waitingFor(new HostPortWaitStrategy())
            .withLogConsumer(frame -> {
                switch (frame.getType()) {
                    case STDOUT:
                        LOGGER.info(frame.getUtf8String());
                        break;
                    case STDERR:
                        LOGGER.error(frame.getUtf8String());
                        break;
                    case END:
                        break; //Ignore
                }
            });
    }

    public ProvisioningAPI cliJarDomainsAndUsersAdder() throws InterruptedException, ProvisioningException, IOException {
        return new CliProvisioningAPI(CliProvisioningAPI.CliType.JAR, container);
    }

    public ProvisioningAPI cliShellDomainsAndUsersAdder() throws InterruptedException, ProvisioningException, IOException {
        return new CliProvisioningAPI(CliProvisioningAPI.CliType.SH, container);
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        container.pause();
    }

    public void unpause() {
        container.unpause();
    }

    public ExternalJamesConfiguration getConfiguration() {
        return new StaticJamesConfiguration("localhost", getMappedPort(IMAP_PORT), getMappedPort(SMTP_PORT));
    }

    public Port getWebadminPort() {
        return getMappedPort(WEBADMIN_PORT);
    }

    private Port getMappedPort(int port) {
        return Port.of(container.getMappedPort(port));
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return statement;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        stop();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        start();
    }
}
