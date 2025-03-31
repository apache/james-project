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


package org.apache.james.backends.redis;

import static java.lang.Boolean.TRUE;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.apache.http.client.utils.URIBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import com.github.fge.lambdas.Throwing;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.sync.RedisCommands;

public class DockerKvrocks {
    public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("apache/kvrocks")
        .withTag("2.11.1");
    public static final int DEFAULT_PORT = 6666;

    private final GenericContainer<?> container;

    public DockerKvrocks() {
        this.container = getContainer();
    }

    public DockerKvrocks(Network network) {
        this.container = getContainer()
            .withNetwork(network);
    }

    private GenericContainer<?> getContainer() {
        return new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withExposedPorts(DEFAULT_PORT)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-kvrocks-test-" + UUID.randomUUID()))
            .withNetworkAliases("kvrocks")
            .waitingFor(new DockerHealthcheckWaitStrategy()
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public URI redisURI() {
        return Throwing.supplier(() -> new URIBuilder()
            .setScheme("redis")
            .setHost(container.getHost())
            .setPort(container.getMappedPort(DEFAULT_PORT))
            .build()).get();
    }

    public void start() {
        if (!container.isRunning()) {
            container.start();
        }
    }

    public void stop() {
        container.stop();
    }

    public void pause() {
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    public void unPause() {
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }

    public boolean isPaused() {
        return TRUE.equals(container.getDockerClient().inspectContainerCmd(container.getContainerId())
            .exec()
            .getState()
            .getPaused());
    }

    public RedisCommands<String, String> createClient() {
        return RedisClient.create(redisURI().toString())
            .connect().sync();
    }

    public void flushAll() {
        createClient().flushall();
    }
}