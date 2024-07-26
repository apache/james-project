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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import io.lettuce.core.ReadFrom;
import scala.Function2;
import scala.jdk.javaapi.OptionConverters;

public class RedisSentinelExtension implements GuiceModuleTestExtension {
    public static final int SENTINEL_PORT = 26379;

    public static class RedisMasterReplicaContainerList extends ArrayList<GenericContainer> {
        public RedisMasterReplicaContainerList(Collection<? extends GenericContainer> c) {
            super(c);
        }

        public void pauseMasterNode() {
            GenericContainer container = this.get(0);
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        }

        public void unPauseMasterNode() {
            GenericContainer container = this.get(0);
            if (TRUE.equals(container.getDockerClient().inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getPaused())) {
                container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            }
        }
    }

    public static class RedisSentinelContainerList extends ArrayList<GenericContainer> {
        public RedisSentinelContainerList(Collection<? extends GenericContainer> c) {
            super(c);
        }

        public MasterReplicaRedisConfiguration getRedisConfiguration() {
            return MasterReplicaRedisConfiguration.from(ImmutableList.of(createRedisSentinelURI(this.stream().toList().subList(0,1)))
                    .toArray(String[]::new),
                ReadFrom.MASTER,
                OptionConverters.toScala(Optional.empty()),
                OptionConverters.toScala(Optional.empty()));
        }
    }

    public record RedisSentinelCluster(RedisMasterReplicaContainerList redisMasterReplicaContainerList,
                                       RedisSentinelContainerList redisSentinelContainerList) {
    }

    public static final Function2<String, Boolean, GenericContainer> redisContainerSupplier = (alias, isSlave) ->
        new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withCommand(Optional.of(isSlave).filter(aBoolean -> aBoolean)
                .map(aBoolean -> "redis-server --appendonly yes --port 6379 --slaveof redis1 6379 --requirepass 1 --masterauth 1")
                .orElse("redis-server --appendonly yes --port 6379 --requirepass 1"))
            .withNetworkAliases(alias)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));

    public static final Function<String, GenericContainer> redisSentinelSupplier = (alias) ->
        new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withExposedPorts(SENTINEL_PORT)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withCommand("redis-sentinel /etc/redis/sentinel.conf")
            .withClasspathResourceMapping("sentinel.conf",
                "/etc/redis/sentinel.conf",
                BindMode.READ_WRITE)
            .withNetworkAliases(alias)
            .waitingFor(Wait.forLogMessage(".*monitor master.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));

    static final GenericContainer redis1 = redisContainerSupplier.apply("redis1", false);
    static final GenericContainer redis2 = redisContainerSupplier.apply("redis2", true);
    static final GenericContainer redis3 = redisContainerSupplier.apply("redis3", true);
    static final GenericContainer sentinel1 = redisSentinelSupplier.apply("sentinel1");
    static final GenericContainer sentinel2 = redisSentinelSupplier.apply("sentinel2");
    static final GenericContainer sentinel3 = redisSentinelSupplier.apply("sentinel3");

    private RedisMasterReplicaContainerList redisMasterReplicaContainerList;
    private RedisSentinelContainerList redisSentinelContainerList;
    private final Network network;

    public RedisSentinelExtension() {
        this(Network.newNetwork());
    }

    public RedisSentinelExtension(Network network) {
        this.network = network;
        redis1.withNetwork(network);
        redis2.withNetwork(network);
        redis3.withNetwork(network);
        sentinel1.withNetwork(network);
        sentinel2.withNetwork(network);
        sentinel3.withNetwork(network);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        redis1.start();
        redis2.start();
        redis3.start();
        sentinel1.start();
        sentinel2.start();
        sentinel3.start();
        redisMasterReplicaContainerList = new RedisMasterReplicaContainerList(List.of(redis1, redis2, redis3));
        redisSentinelContainerList = new RedisSentinelContainerList(List.of(sentinel1, sentinel2, sentinel3));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        Runnables.runParallel(
            sentinel1::stop,
            sentinel2::stop,
            sentinel3::stop,
            redis1::stop,
            redis2::stop,
            redis3::stop);
        network.close();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        redisMasterReplicaContainerList.forEach(Throwing.consumer(container -> container.execInContainer("redis-cli", "flushall")));
    }

    @Override
    public Module getModule() {
        return new AbstractModule() {
            @Provides
            @Singleton
            public RedisConfiguration provideRedisConfiguration() {
                return redisSentinelContainerList.getRedisConfiguration();
            }
        };
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == RedisSentinelCluster.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return new RedisSentinelCluster(redisMasterReplicaContainerList, redisSentinelContainerList);
    }

    private static String createRedisSentinelURI(List<GenericContainer> containers) {
        return new StringBuilder().append("redis-sentinel://1@")
            .append(containers.stream().map(container -> container.getHost() + ":" + container.getMappedPort(SENTINEL_PORT))
                .collect(Collectors.joining(",")))
            .append("/0#mymaster")
            .toString();
    }
}