/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.DockerHealthcheckWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.model.HealthCheck;
import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import io.lettuce.core.ReadFrom;

public class KvrocksSentinelExtension implements GuiceModuleTestExtension {
    public static final int SENTINEL_PORT = 26379;
    public static final String SENTINEL_PASSWORD = "321";

    public static class KvrocksMasterReplicaContainerList extends ArrayList<GenericContainer> {
        public KvrocksMasterReplicaContainerList(Collection<? extends GenericContainer> c) {
            super(c);
        }

        public void pauseMasterNode() {
            GenericContainer container = this.get(0);
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        }

        public void unPauseMasterNode() {
            GenericContainer container = this.get(0);
            if (container.getDockerClient().inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getPaused()) {
                container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            }
        }
    }

    public static class RedisSentinelContainerList extends ArrayList<GenericContainer> {
        private final SentinelRedisConfiguration sentinelRedisConfiguration;

        public RedisSentinelContainerList(Collection<? extends GenericContainer> c) {
            super(c);
            String sentinelURI = createRedisSentinelURI();
            sentinelRedisConfiguration = SentinelRedisConfiguration.from(sentinelURI,
                ReadFrom.MASTER,
                SENTINEL_PASSWORD);
        }

        public SentinelRedisConfiguration getRedisConfiguration() {
            return sentinelRedisConfiguration;
        }

        public void pauseFirstNode() {
            GenericContainer container = this.get(0);
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        }

        public void unPauseFirstNode() {
            GenericContainer container = this.get(0);
            if (container.getDockerClient().inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getPaused()) {
                container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            }
        }

        private String createRedisSentinelURI() {
            StringBuilder sb = new StringBuilder();
            sb.append("redis-sentinel://123@");
            sb.append(this.stream().map(container -> container.getHost() + ":" + container.getMappedPort(SENTINEL_PORT))
                .collect(Collectors.joining(",")));
            sb.append("?sentinelMasterId=mymaster");
            return sb.toString();
        }
    }

    public record KvrocksSentinel(KvrocksMasterReplicaContainerList kvrocksMasterReplicaContainerList,
                                  RedisSentinelContainerList redisSentinelContainerList) {
    }

    final GenericContainer kvrocks1;
    final GenericContainer kvrocks2;
    final GenericContainer kvrocks3;
    final GenericContainer sentinel1;
    final GenericContainer sentinel2;
    final GenericContainer sentinel3;

    private KvrocksMasterReplicaContainerList kvrocksMasterReplicaContainerList;
    private RedisSentinelContainerList redisSentinelContainerList;
    private KvrocksSentinel kvrocksSentinel;
    private final Network network;

    public KvrocksSentinelExtension() {
        this.network = Network.newNetwork();
        kvrocks1 = createKvrocksContainer("kvrocks1", false);
        kvrocks2 = createKvrocksContainer("kvrocks2", true);
        kvrocks3 = createKvrocksContainer("kvrocks3", true);
        sentinel1 = createSentinelContainer("sentinel1");
        sentinel2 = createSentinelContainer("sentinel2");
        sentinel3 = createSentinelContainer("sentinel3");
        kvrocks1.withNetwork(network);
        kvrocks2.withNetwork(network);
        kvrocks3.withNetwork(network);
        sentinel1.withNetwork(network);
        sentinel2.withNetwork(network);
        sentinel3.withNetwork(network);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        kvrocks1.start();
        kvrocks2.start();
        kvrocks3.start();
        sentinel1.start();
        sentinel2.start();
        sentinel3.start();
        kvrocksMasterReplicaContainerList = new KvrocksMasterReplicaContainerList(List.of(kvrocks1, kvrocks2, kvrocks3));
        redisSentinelContainerList = new RedisSentinelContainerList(List.of(sentinel1, sentinel2, sentinel3));
        kvrocksSentinel = new KvrocksSentinel(kvrocksMasterReplicaContainerList, redisSentinelContainerList);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        Runnables.runParallel(
            sentinel1::stop,
            sentinel2::stop,
            sentinel3::stop,
            kvrocks1::stop,
            kvrocks2::stop,
            kvrocks3::stop);
        network.close();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        kvrocksMasterReplicaContainerList.forEach(Throwing.consumer(container -> container.execInContainer("redis-cli", "flushall")));
    }

    public KvrocksSentinel getRedisSentinelCluster() {
        return kvrocksSentinel;
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
        return parameterContext.getParameter().getType() == KvrocksSentinel.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return kvrocksSentinel;
    }

    private GenericContainer createKvrocksContainer(String alias, boolean isReplica) {
        GenericContainer genericContainer = new GenericContainer<>(DockerKvrocks.DEFAULT_IMAGE_NAME)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withCreateContainerCmdModifier(cmd -> cmd.withHealthcheck(new HealthCheck()
                .withTest(List.of("CMD-SHELL", "redis-cli -p 6379 PING | grep -E '(PONG|NOAUTH)' || exit 1"))
                .withInterval(Duration.ofSeconds(2).toNanos())
                .withTimeout(Duration.ofSeconds(3).toNanos())
                .withStartPeriod(Duration.ofSeconds(30).toNanos())
                .withRetries(10)))
            .withNetworkAliases(alias)
            .waitingFor(new DockerHealthcheckWaitStrategy()
                .withStartupTimeout(Duration.ofMinutes(2)));

        if (isReplica) {
            genericContainer.withCopyFileToContainer(MountableFile.forClasspathResource("kvrocks/replica/kvrocks.conf"),
                "/var/lib/kvrocks/kvrocks.conf");
        } else {
            genericContainer.withCopyFileToContainer(MountableFile.forClasspathResource("kvrocks/master/kvrocks.conf"),
                "/var/lib/kvrocks/kvrocks.conf");
        }

        return genericContainer;
    }

    private GenericContainer createSentinelContainer(String alias) {
        GenericContainer genericContainer = new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withExposedPorts(SENTINEL_PORT)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withCommand("redis-sentinel /etc/redis/sentinel.conf")
            .withNetworkAliases(alias)
            .waitingFor(Wait.forLogMessage(".*monitor master.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        genericContainer.withCopyFileToContainer(MountableFile.forClasspathResource("kvrocks/sentinel/sentinel.conf"),
            "/etc/redis/sentinel.conf");
        return genericContainer;
    }
}