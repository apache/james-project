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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import com.github.fge.lambdas.Throwing;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;

import io.lettuce.core.ReadFrom;
import static java.lang.Boolean.TRUE;
import static org.apache.james.backends.redis.DockerRedis.DEFAULT_PORT;
import scala.jdk.javaapi.OptionConverters;

public class RedisMasterReplicaExtension implements GuiceModuleTestExtension {
    private static final String START_REPLICA_COMMAND = "redis-server --appendonly yes --port 6379 --slaveof redis1 6379 --requirepass 123 --masterauth 123";
    private static final String START_MASTER_COMMAND = "redis-server --appendonly yes --port 6379 --requirepass 123 --masterauth 123";

    public static class RedisMasterReplicaContainer extends ArrayList<DockerRedis> {
        public RedisMasterReplicaContainer(Collection<? extends DockerRedis> c) {
            super(c);
        }

        public MasterReplicaRedisConfiguration getRedisConfiguration() {
            return MasterReplicaRedisConfiguration.from(this.stream()
                    .map(DockerRedis::getContainer)
                    .map(redisURIFunction())
                    .toArray(String[]::new),
                ReadFrom.MASTER,
                OptionConverters.toScala(Optional.empty()),
                OptionConverters.toScala(Optional.empty()));
        }

        public void pauseOne() {
            GenericContainer container = this.get(0).getContainer();
            container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
        }

        public void unPauseOne() {
            GenericContainer container = this.get(0).getContainer();
            if (TRUE.equals(container.getDockerClient().inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getPaused())) {
                container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
            }
        }

        private static Function<GenericContainer, String> redisURIFunction() {
            return redisContainer -> "redis://123@" +
                redisContainer.getHost() +
                ":" +
                redisContainer.getMappedPort(DEFAULT_PORT);
        }
    }

    private final DockerRedis redis1;
    private final DockerRedis redis2;
    private final DockerRedis redis3;

    private RedisMasterReplicaContainer redisMasterReplicaContainer;
    private final Network network;

    public RedisMasterReplicaExtension() {
        this.network = Network.newNetwork();
        redis1 = createRedisContainer("redis1", false);
        redis2 = createRedisContainer("redis2", true);
        redis3 = createRedisContainer("redis3", true);
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
        redisMasterReplicaContainer.forEach(Throwing.consumer(container -> container.getContainer().execInContainer("redis-cli", "flushall")));
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

    private DockerRedis createRedisContainer(String alias, boolean isSlave) {
        DockerRedis redis = new DockerRedis(alias);

        if (isSlave) {
            redis.getContainer().withCommand(START_REPLICA_COMMAND);
        } else {
            redis.getContainer().withCommand(START_MASTER_COMMAND);
        }

        return redis;
    }
}