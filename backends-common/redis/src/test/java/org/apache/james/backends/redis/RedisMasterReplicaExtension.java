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
import static org.apache.james.backends.redis.DockerRedis.DEFAULT_IMAGE_NAME;
import static org.apache.james.backends.redis.DockerRedis.DEFAULT_PORT;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import io.lettuce.core.ReadFrom;
import scala.jdk.javaapi.OptionConverters;

public class RedisMasterReplicaExtension implements GuiceModuleTestExtension {
    public static final String START_REPLICA_COMMAND = "redis-server --appendonly yes --port 6379 --slaveof redis1 6379 --requirepass 123 --masterauth 123";
    public static final String START_MASTER_COMMAND = "redis-server --appendonly yes --port 6379 --requirepass 123 --masterauth 123";

    public static class RedisMasterReplicaContainer extends ArrayList<GenericContainer> {
        private final MasterReplicaRedisConfiguration masterReplicaRedisConfiguration;

        public RedisMasterReplicaContainer(Collection<? extends GenericContainer> c) {
            super(c);
            String[] redisUris = this.stream()
                .map(redisURIFunction())
                .toArray(String[]::new);
            masterReplicaRedisConfiguration = MasterReplicaRedisConfiguration.from(redisUris,
                ReadFrom.MASTER,
                OptionConverters.toScala(Optional.empty()),
                OptionConverters.toScala(Optional.empty()));

        }

        public MasterReplicaRedisConfiguration getRedisConfiguration() {
            return masterReplicaRedisConfiguration;
        }

        public void pauseOne() {
            GenericContainer container = this.get(0);
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        }

        public void unPauseOne() {
            GenericContainer container = this.get(0);
            if (TRUE.equals(container.getDockerClient().inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getPaused())) {
                container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            }
        }

        private Function<GenericContainer, String> redisURIFunction() {
            return redisContainer -> "redis://123@" +
                redisContainer.getHost() +
                ":" +
                redisContainer.getMappedPort(DEFAULT_PORT)
                + "?timeout=3s";
        }
    }

    final GenericContainer redis1;
    final GenericContainer redis2;
    final GenericContainer redis3;

    private RedisMasterReplicaContainer redisMasterReplicaContainer;
    private final Network network;

    public RedisMasterReplicaExtension() {
        network = Network.newNetwork();;
        redis1 = createRedisContainer("redis1", false);
        redis2 = createRedisContainer("redis2", true);
        redis3 = createRedisContainer("redis3", true);
        redis1.withNetwork(network);
        redis2.withNetwork(network);
        redis3.withNetwork(network);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        redis1.start();
        redis2.start();
        redis3.start();
        redisMasterReplicaContainer = new RedisMasterReplicaContainer(List.of(redis1, redis2, redis3));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        Runnables.runParallel(
            redis1::stop,
            redis2::stop,
            redis3::stop);
        network.close();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        redisMasterReplicaContainer.forEach(Throwing.consumer(container -> container.execInContainer("redis-cli", "flushall")));
    }

    public RedisMasterReplicaContainer getRedisMasterReplicaContainer() {
        return redisMasterReplicaContainer;
    }

    @Override
    public Module getModule() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public RedisConfiguration provideRedisConfiguration() {
                return redisMasterReplicaContainer.getRedisConfiguration();
            }
        };
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == RedisMasterReplicaContainer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return redisMasterReplicaContainer;
    }

    private GenericContainer createRedisContainer(String alias, boolean isSlave) {
        GenericContainer genericContainer = new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withExposedPorts(DEFAULT_PORT)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withNetworkAliases(alias)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));

        if (isSlave) {
            genericContainer.withCommand(START_REPLICA_COMMAND);
        } else {
            genericContainer.withCommand(START_MASTER_COMMAND);
        }

        return genericContainer;
    }
}