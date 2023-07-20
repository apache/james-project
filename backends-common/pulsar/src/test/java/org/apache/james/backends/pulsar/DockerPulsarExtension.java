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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

import com.google.inject.Module;

public class DockerPulsarExtension implements GuiceModuleTestExtension {
    private static final Logger logger = LoggerFactory.getLogger(DockerPulsarExtension.class);
    private static PulsarContainer container;
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("apachepulsar/pulsar");

    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.info(outputFrame.getUtf8String().trim());
    }

    private PulsarConfiguration configuration;
    private PulsarAdmin adminClient;
    private DockerPulsar dockerPulsar;

    public DockerPulsarExtension() {
        container = new PulsarContainer(DEFAULT_IMAGE_NAME.withTag("2.10.1"))
                .withLogConsumer(DockerPulsarExtension::displayDockerLog);
    }

    @Override
    public Module getModule() {
        return new TestPulsarModule(dockerPulsar);
    }

    @Override
    public Optional<Class<?>> supportedParameterClass() {
        return GuiceModuleTestExtension.super.supportedParameterClass();
    }

    PulsarConfiguration pulsarConfiguration() {
        return new PulsarConfiguration(
                container.getPulsarBrokerUrl(),
                container.getHttpServiceUrl(),
                new Namespace("test/" + RandomStringUtils.randomAlphabetic(10)),
                Auth.noAuth()
        );
    }

    public PulsarConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws PulsarClientException, PulsarAdminException {
        container.start();
        adminClient = PulsarAdmin.builder().serviceHttpUrl(container.getHttpServiceUrl()).build();
        Set<String> clusters = new HashSet<>(adminClient.clusters().getClusters());
        adminClient.tenants().createTenant("test", TenantInfo.builder().allowedClusters(clusters).build());
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
