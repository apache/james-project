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

package org.apache.james.backends.pulsar;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

public class DockerPulsarExtension implements
        AfterAllCallback,
        BeforeAllCallback,
        BeforeEachCallback,
        ParameterResolver {
    private static final Logger logger = LoggerFactory.getLogger(DockerPulsarExtension.class);
    private static PulsarContainer container;
    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String().trim());
    }
    private PulsarConfiguration configuration;
    private PulsarAdmin adminClient;
    private DockerPulsar dockerPulsar;

    public DockerPulsarExtension() {
        container = new PulsarContainer("2.9.1")
                .withLogConsumer(DockerPulsarExtension::displayDockerLog)
                .waitingFor(
                        new WaitAllStrategy()
                                .withStrategy(
                                        Wait.forHttp(PulsarContainer.METRICS_ENDPOINT)
                                                .forStatusCode(200)
                                                .forPort(PulsarContainer.BROKER_HTTP_PORT))
                                .withStrategy(
                                        Wait.forLogMessage(".*Successfully validated clusters on tenant .public.*\\n", 1))
                );
    }

    PulsarConfiguration pulsarConfiguration() {
        return new PulsarConfiguration(container.getPulsarBrokerUrl(),
                container.getHttpServiceUrl(), new Namespace("public/" + RandomStringUtils.randomAlphabetic(10)));
    }

    public PulsarConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws PulsarClientException {
        container.start();
        adminClient = PulsarAdmin.builder().serviceHttpUrl(container.getHttpServiceUrl()).build();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        adminClient.close();
        container.stop();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        configuration = pulsarConfiguration();
        adminClient.namespaces().createNamespace(configuration.namespace().asString());
        dockerPulsar = new DockerPulsar(container, configuration, adminClient);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerPulsar.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerPulsar;
    }

    public static class DockerPulsar {
        private final PulsarContainer container;
        private final PulsarConfiguration configuration;
        private final PulsarAdmin adminClient;

        private DockerPulsar(PulsarContainer container, PulsarConfiguration configuration, PulsarAdmin adminClient) {
            this.container = container;
            this.configuration = configuration;
            this.adminClient = adminClient;
        }

        public PulsarConfiguration getConfiguration() {
            return configuration;
        }

        public PulsarAdmin getAdminClient() {
            return adminClient;
        }

        public PulsarContainer getContainer() {
            return container;
        }
    }

}
