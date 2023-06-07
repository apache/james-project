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

package org.apache.james.util.docker;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.github.dockerjava.api.model.ContainerNetwork;
import com.google.common.base.Strings;

public class DockerContainer implements TestRule, BeforeAllCallback, AfterAllCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainer.class);
    private static final String DOCKER_CONTAINER = "DOCKER_CONTAINER";

    private GenericContainer<?> container;

    public static DockerContainer fromName(String imageName) {
        disableDockerTestsIfDockerUnavailable();
        return new DockerContainer(new GenericContainer<>(imageName));
    }

    public static DockerContainer fromDockerfile(ImageFromDockerfile imageFromDockerfile) {
        disableDockerTestsIfDockerUnavailable();
        return new DockerContainer(new GenericContainer<>(imageFromDockerfile));
    }

    private static void disableDockerTestsIfDockerUnavailable() {
        try {
            DockerClientFactory.instance().client();
        } catch (IllegalStateException e) {
            LOGGER.error("Cannot connect to docker service", e);
            throw new AssumptionViolatedException("Skipping all docker tests as no Docker environment was found");
        }
    }

    public DockerContainer(GenericContainer<?> container) {
        this.container = container;
    }

    public DockerContainer withAffinityToContainer() {
        String containerEnv = System.getenv(DOCKER_CONTAINER);
        if (Strings.isNullOrEmpty(containerEnv)) {
            LOGGER.warn("'DOCKER_CONTAINER' environment variable not found, dockering without affinity");
            return this;
        }
        List<String> envVariables = container.getEnv();
        envVariables.add("affinity:container==" + container);
        container.setEnv(envVariables);
        return this;
    }

    public DockerContainer withClasspathResourceMapping(String resourcePath, String containerPath, BindMode mode) {
        container.withClasspathResourceMapping(resourcePath, containerPath, mode);
        return this;
    }

    public DockerContainer withStartupCheckStrategy(StartupCheckStrategy strategy) {
        container.withStartupCheckStrategy(strategy);
        return this;
    }

    public DockerContainer withNetwork(Network network) {
        container.withNetwork(network);
        return this;
    }

    public DockerContainer withNetworkAliases(String... aliases) {
        container.withNetworkAliases(aliases);
        return this;
    }

    public DockerContainer withLogConsumer(Consumer<OutputFrame> consumer) {
        container.withLogConsumer(consumer);
        return this;
    }

    public DockerContainer withEnv(String key, String value) {
        container.addEnv(key, value);
        return this;
    }

    public DockerContainer withTmpFs(Map<String, String> mapping) {
        container.withTmpFs(mapping);
        return this;
    }

    public DockerContainer withExposedPorts(Integer... ports) {
        container.withExposedPorts(ports);
        return this;
    }

    public DockerContainer waitingFor(WaitStrategy waitStrategy) {
        container.waitingFor(waitStrategy);
        return this;
    }

    public DockerContainer withStartupTimeout(Duration startupTimeout) {
        container.withStartupTimeout(startupTimeout);
        return this;
    }

    public DockerContainer withCommands(String... commands) {
        container.withCommand(commands);
        return this;
    }

    public DockerContainer withName(String containerName) {
        container.withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName(containerName));
        return this;
    }

    public Container.ExecResult exec(String... command) throws IOException, InterruptedException {
        return container.execInContainer(command);
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        DockerClientFactory.instance().client().pauseContainerCmd(container.getContainerInfo().getId()).exec();
    }

    public boolean isRunning() {
        return container.isRunning();
    }

    public void unpause() {
        DockerClientFactory.instance().client().unpauseContainerCmd(container.getContainerInfo().getId()).exec();
    }

    public Integer getMappedPort(int originalPort) {
        return container.getMappedPort(originalPort);
    }

    public GenericContainer<?> getContainer() {
        return container;
    }

    @SuppressWarnings("deprecation")
    public String getContainerIp() {
        return container.getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .values()
            .stream()
            .map(ContainerNetwork::getIpAddress)
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    }
    
    public String getHostIp() {
        return container.getContainerIpAddress();
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    container.start();
                    statement.evaluate();
                } finally {
                    container.stop();
                }
            }
        };
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        container.stop();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        container.start();
    }
}
