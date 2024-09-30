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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import io.lettuce.core.ReadFrom;

public class RedisSentinelExtension implements GuiceModuleTestExtension {
    public static final int SENTINEL_PORT = 26379;
    public static final String SENTINEL_PASSWORD = "321";
    public static final String START_REPLICA_COMMAND = "redis-server --appendonly yes --port 6379 --slaveof redis1 6379 --requirepass 123 --masterauth 123";
    public static final String START_MASTER_COMMAND = "redis-server --appendonly yes --port 6379 --requirepass 123 --masterauth 123";
    public static final String TLS_REPLICA_COMMAND = "redis-server --appendonly yes --port 0 --slaveof redis1 6379 --requirepass 123 --masterauth 123" +
        " --tls-port 6379 --tls-cert-file /etc/redis/certificate.crt --tls-key-file /etc/redis/private.key --tls-ca-cert-file /etc/redis/rootCA.crt --tls-replication yes";
    public static final String TLS_MASTER_COMMAND = "redis-server --appendonly yes --port 0 --requirepass 123 --masterauth 123" +
        " --tls-port 6379 --tls-cert-file /etc/redis/certificate.crt --tls-key-file /etc/redis/private.key --tls-ca-cert-file /etc/redis/rootCA.crt --tls-replication yes";

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

        public RedisSentinelContainerList(Collection<? extends GenericContainer> c, boolean tlsEnabled) {
            super(c);
            String sentinelURI = createRedisSentinelURI(tlsEnabled);
            if (tlsEnabled) {
                sentinelRedisConfiguration = SentinelRedisConfiguration.from(sentinelURI,
                    SSLConfiguration.from(FileSystem.CLASSPATH_PROTOCOL + "keystore.p12", "secret"),
                    ReadFrom.MASTER);
            } else {
                sentinelRedisConfiguration = SentinelRedisConfiguration.from(sentinelURI,
                    ReadFrom.MASTER,
                    SENTINEL_PASSWORD);
            }
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

        private String createRedisSentinelURI(boolean tlsEnabled) {
            StringBuilder sb = new StringBuilder();
            if (tlsEnabled) {
                sb.append("rediss-sentinel://123@");
            } else {
                sb.append("redis-sentinel://123@");
            }
            sb.append(this.stream().map(container -> container.getHost() + ":" + container.getMappedPort(SENTINEL_PORT))
                .collect(Collectors.joining(",")));
            if (tlsEnabled) {
                sb.append("?sentinelMasterId=mymaster&verifyPeer=NONE");
            } else {
                sb.append("?sentinelMasterId=mymaster");
            }
            return sb.toString();
        }
    }

    public record RedisSentinelCluster(RedisMasterReplicaContainerList redisMasterReplicaContainerList,
                                       RedisSentinelContainerList redisSentinelContainerList) {
    }

    final GenericContainer redis1;
    final GenericContainer redis2;
    final GenericContainer redis3;
    final GenericContainer sentinel1;
    final GenericContainer sentinel2;
    final GenericContainer sentinel3;

    private RedisMasterReplicaContainerList redisMasterReplicaContainerList;
    private RedisSentinelContainerList redisSentinelContainerList;
    private RedisSentinelCluster redisSentinelCluster;
    private final boolean tlsEnabled;
    private final Network network;

    public RedisSentinelExtension() {
        this(false, Network.newNetwork());
    }

    public RedisSentinelExtension(boolean tlsEnable) {
        this(tlsEnable, Network.newNetwork());
    }

    public RedisSentinelExtension(boolean tlsEnable, Network network) {
        this.tlsEnabled = tlsEnable;
        this.network = network;
        redis1 = createRedisContainer("redis1", false);
        redis2 = createRedisContainer("redis2", true);
        redis3 = createRedisContainer("redis3", true);
        sentinel1 = createSentinelContainer("sentinel1");
        sentinel2 = createSentinelContainer("sentinel2");
        sentinel3 = createSentinelContainer("sentinel3");
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
        redisSentinelContainerList = new RedisSentinelContainerList(List.of(sentinel1, sentinel2, sentinel3), tlsEnabled);
        redisSentinelCluster = new RedisSentinelCluster(redisMasterReplicaContainerList, redisSentinelContainerList);
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

    public RedisSentinelCluster getRedisSentinelCluster() {
        return redisSentinelCluster;
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
        return redisSentinelCluster;
    }

    private GenericContainer createRedisContainer(String alias, boolean isSlave) {
        GenericContainer genericContainer = new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-" + alias + "-test-" + UUID.randomUUID()))
            .withNetworkAliases(alias)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        if (tlsEnabled) {
            genericContainer.withClasspathResourceMapping("certificate.crt",
                    "/etc/redis/certificate.crt",
                    BindMode.READ_WRITE)
                .withClasspathResourceMapping("private.key",
                    "/etc/redis/private.key",
                    BindMode.READ_WRITE)
                .withClasspathResourceMapping("rootCA.crt",
                    "/etc/redis/rootCA.crt",
                    BindMode.READ_WRITE);
            if (isSlave) {
                genericContainer.withCommand(TLS_REPLICA_COMMAND);
            } else {
                genericContainer.withCommand(TLS_MASTER_COMMAND);
            }
        } else {
            if (isSlave) {
                genericContainer.withCommand(START_REPLICA_COMMAND);
            } else {
                genericContainer.withCommand(START_MASTER_COMMAND);
            }
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
        if (tlsEnabled) {
            genericContainer.withClasspathResourceMapping("sentinel_tls.conf",
                    "/etc/redis/sentinel.conf",
                    BindMode.READ_WRITE)
                .withClasspathResourceMapping("certificate.crt",
                    "/etc/redis/certificate.crt",
                    BindMode.READ_WRITE)
                .withClasspathResourceMapping("private.key",
                    "/etc/redis/private.key",
                    BindMode.READ_WRITE)
                .withClasspathResourceMapping("rootCA.crt",
                    "/etc/redis/rootCA.crt",
                    BindMode.READ_WRITE);
        } else {
            genericContainer.withClasspathResourceMapping("sentinel.conf",
                "/etc/redis/sentinel.conf",
                BindMode.READ_WRITE);
        }
        return genericContainer;
    }
}