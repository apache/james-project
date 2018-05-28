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

import java.net.Socket;
import java.time.Duration;
import java.util.List;

import javax.net.SocketFactory;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.common.base.Strings;

public class SwarmGenericContainer implements TestRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwarmGenericContainer.class);
    private static final String DOCKER_CONTAINER = "DOCKER_CONTAINER";
    private static final String NO_DOCKER_ENVIRONMENT = "Could not find a valid Docker environment.";
    private static final String SKIPPING_TEST_CAUTION = "Skipping all docker tests as no Docker environment was found";

    private GenericContainer<?> container;

    public SwarmGenericContainer(String dockerImageName) {
        try {
            this.container = new GenericContainer<>(dockerImageName);
        } catch (IllegalStateException e) {
            logAndCheckSkipTest(e);
        }
    }

    public SwarmGenericContainer(ImageFromDockerfile imageFromDockerfile) {
        try {
            this.container = new GenericContainer<>(imageFromDockerfile);
        } catch (IllegalStateException e) {
            logAndCheckSkipTest(e);
        }
    }
    
    private void logAndCheckSkipTest(IllegalStateException e) {
        LOGGER.error("Cannot initial a docker container", e);
        if (e.getMessage().startsWith(NO_DOCKER_ENVIRONMENT)) {
            Assume.assumeTrue(SKIPPING_TEST_CAUTION, false);
        }
    }

    public SwarmGenericContainer withAffinityToContainer() {
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

    public SwarmGenericContainer withEnv(String key, String value) {
        container.addEnv(key, value);
        return this;
    }

    public SwarmGenericContainer withExposedPorts(Integer... ports) {
        container.withExposedPorts(ports);
        return this;
    }

    public SwarmGenericContainer portBinding(int hostPort, int dockerPort) {
        container.setPortBindings(ImmutableList.of("0.0.0.0:" + hostPort + ":" + dockerPort));
        return this;
    }

    public SwarmGenericContainer waitingFor(WaitStrategy waitStrategy) {
        container.waitingFor(waitStrategy);
        return this;
    }

    public SwarmGenericContainer withStartupTimeout(Duration startupTimeout) {
        container.withStartupTimeout(startupTimeout);
        return this;
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        DockerClientFactory.instance().client().pauseContainerCmd(container.getContainerInfo().getId());
    }

    public void unpause() {
        DockerClientFactory.instance().client().unpauseContainerCmd(container.getContainerInfo().getId());
    }

    public Integer getMappedPort(int originalPort) {
        return container.getMappedPort(originalPort);
    }

    @SuppressWarnings("deprecation")
    public String getContainerIp() {
        return container.getContainerInfo().getNetworkSettings().getIpAddress();
    }
    
    public String getHostIp() {
        return container.getContainerIpAddress();
    }

    public InspectContainerResponse getContainerInfo() {
        return container.getContainerInfo();
    }

    public boolean tryConnect(int port) {
        try {
            Socket socket = SocketFactory.getDefault().createSocket(getContainerIp(), port);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return container.apply(statement, description);
    }

}
