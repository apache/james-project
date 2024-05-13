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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.apache.http.client.utils.URIBuilder;
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
import scala.Function2;
import scala.jdk.javaapi.OptionConverters;

public class RedisMasterReplicaExtension implements GuiceModuleTestExtension {

    public static class RedisMasterReplicaContainer extends ArrayList<GenericContainer> {
        public RedisMasterReplicaContainer(Collection<? extends GenericContainer> c) {
            super(c);
        }

        public MasterReplicaRedisConfiguration getRedisConfiguration() {
            return MasterReplicaRedisConfiguration.from(this.stream()
                    .map(redisURIFunction())
                    .map(URI::toString)
                    .toArray(String[]::new),
                ReadFrom.MASTER,
                OptionConverters.toScala(Optional.empty()),
                OptionConverters.toScala(Optional.empty()));
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
    }

    public static final Function2<String, Boolean, GenericContainer> redisContainerSupplier = (alias, isSlave) ->
        new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withExposedPorts(DEFAULT_PORT)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withCommand(Optional.of(isSlave).filter(aBoolean -> aBoolean)
                .map(aBoolean -> "redis-server --appendonly yes --port 6379 --slaveof redis1 6379")
                .orElse("redis-server --appendonly yes --port 6379"))
            .withNetworkAliases(alias)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));

    static final GenericContainer redis1 = redisContainerSupplier.apply("redis1", false);
    static final GenericContainer redis2 = redisContainerSupplier.apply("redis2", true);
    static final GenericContainer redis3 = redisContainerSupplier.apply("redis3", true);

    private RedisMasterReplicaContainer redisMasterReplicaContainer;
    private final Network network;

    public RedisMasterReplicaExtension() {
        this(Network.newNetwork());
    }

    public RedisMasterReplicaExtension(Network network) {
        this.network = network;
        redis1.withNetwork(network);
        redis2.withNetwork(network);
        redis3.withNetwork(network);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws IOException, InterruptedException {
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
        return new RedisMasterReplicaContainer(List.of(redis1, redis2, redis3));
    }

    private static Function<GenericContainer, URI> redisURIFunction() {
        return redisContainer -> Throwing.supplier(() -> new URIBuilder()
            .setScheme("redis")
            .setHost(redisContainer.getHost())
            .setPort(redisContainer.getMappedPort(DEFAULT_PORT))
            .build()).get();
    }
}