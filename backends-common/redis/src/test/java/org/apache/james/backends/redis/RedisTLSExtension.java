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

import static org.apache.james.backends.redis.DockerRedis.DEFAULT_IMAGE_NAME;
import static org.apache.james.backends.redis.DockerRedis.DEFAULT_PORT;

import java.time.Duration;
import java.util.UUID;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.filesystem.api.FileSystem;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class RedisTLSExtension implements GuiceModuleTestExtension {
    public record RedisContainer(GenericContainer container) {
        public StandaloneRedisConfiguration getConfiguration() {
            return StandaloneRedisConfiguration.from(redisURI(),
                SSLConfiguration.from(FileSystem.CLASSPATH_PROTOCOL + "keystore.p12", "secret"));
        }

        public void pause() {
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        }

        public void unPause() {
            if (container.getDockerClient().inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getPaused()) {
                container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            }
        }

        private String redisURI() {
            return new StringBuilder()
                .append("rediss://123@")
                .append(container.getHost())
                .append(":")
                .append(container.getMappedPort(DEFAULT_PORT))
                .append("?verifyPeer=NONE")
                .append("&timeout=3s")
                .toString();
        }
    }
    public static final String START_SERVER_COMMAND = "redis-server --appendonly yes --port 0 --requirepass 123 --tls-port 6379 --tls-cert-file /etc/redis/certificate.crt --tls-key-file /etc/redis/private.key --tls-ca-cert-file /etc/redis/rootCA.crt";

    private static final GenericContainer DOCKER_REDIS_CONTAINER = new GenericContainer<>(DEFAULT_IMAGE_NAME)
        .withExposedPorts(DEFAULT_PORT)
        .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-redis-test-" + UUID.randomUUID()))
        .withCommand(START_SERVER_COMMAND)
        .withClasspathResourceMapping("certificate.crt",
            "/etc/redis/certificate.crt",
            BindMode.READ_ONLY)
        .withClasspathResourceMapping("private.key",
            "/etc/redis/private.key",
            BindMode.READ_ONLY)
        .withClasspathResourceMapping("rootCA.crt",
            "/etc/redis/rootCA.crt",
            BindMode.READ_ONLY)
        .withNetworkAliases("redis")
        .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
            .withStartupTimeout(Duration.ofMinutes(2)));

    private RedisContainer redisContainer;

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        DOCKER_REDIS_CONTAINER.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        DOCKER_REDIS_CONTAINER.stop();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        DOCKER_REDIS_CONTAINER.execInContainer("redis-cli", "flushall");
        redisContainer = new RedisContainer(DOCKER_REDIS_CONTAINER);
    }

    public RedisContainer getRedisContainer() {
        return redisContainer;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == RedisContainer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return redisContainer;
    }
}
