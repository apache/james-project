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
package org.apache.james.backends.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.Network;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.rabbitmq.client.Address;

public class DockerClusterRabbitMQExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String RABBIT_1 = "rabbit1";
    private static final String RABBIT_2 = "rabbit2";
    private static final String RABBIT_3 = "rabbit3";
    private DockerRabbitMQCluster cluster;
    private Network network;
    private DockerRabbitMQ rabbitMQ1;
    private DockerRabbitMQ rabbitMQ2;
    private DockerRabbitMQ rabbitMQ3;

    public void beforeAll() {
        String cookie = Hashing.sha256().hashString("secret cookie here", StandardCharsets.UTF_8).toString();

        network = Network.NetworkImpl.builder()
            .enableIpv6(false)
            .createNetworkCmdModifiers(ImmutableList.of())
            .build();

        String clusterIdentity = UUID.randomUUID().toString();
        rabbitMQ1 = DockerRabbitMQ.withCookieAndHostName(RABBIT_1, clusterIdentity, cookie, network);
        rabbitMQ2 = DockerRabbitMQ.withCookieAndHostName(RABBIT_2, clusterIdentity, cookie, network);
        rabbitMQ3 = DockerRabbitMQ.withCookieAndHostName(RABBIT_3, clusterIdentity, cookie, network);

        startDockerRabbits();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        startDockerRabbits();

        Runnables.runParallel(
            Throwing.runnable(() -> rabbitMQ2.join(rabbitMQ1)),
            Throwing.runnable(() -> rabbitMQ3.join(rabbitMQ1)));

        cluster = new DockerRabbitMQCluster(rabbitMQ1, rabbitMQ2, rabbitMQ3);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cluster.detach();
    }

    public void afterAll() throws Exception {
        cluster.stop();
        network.close();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerRabbitMQCluster.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return cluster;
    }

    private void startDockerRabbits() {
        Runnables.runParallel(
            rabbitMQ1::start,
            rabbitMQ2::start,
            rabbitMQ3::start);
    }

    public static class DockerRabbitMQCluster {

        private final DockerRabbitMQ rabbitMQ1;
        private final DockerRabbitMQ rabbitMQ2;
        private final DockerRabbitMQ rabbitMQ3;

        public DockerRabbitMQCluster(DockerRabbitMQ rabbitMQ1, DockerRabbitMQ rabbitMQ2, DockerRabbitMQ rabbitMQ3) {
            this.rabbitMQ1 = rabbitMQ1;
            this.rabbitMQ2 = rabbitMQ2;
            this.rabbitMQ3 = rabbitMQ3;
        }

        public void stop() {
            Runnables.runParallel(
                Throwing.runnable(rabbitMQ1::stop).orDoNothing(),
                Throwing.runnable(rabbitMQ2::stop).orDoNothing(),
                Throwing.runnable(rabbitMQ3::stop).orDoNothing());
        }

        public DockerRabbitMQ getRabbitMQ1() {
            return rabbitMQ1;
        }

        public DockerRabbitMQ getRabbitMQ2() {
            return rabbitMQ2;
        }

        public DockerRabbitMQ getRabbitMQ3() {
            return rabbitMQ3;
        }

        public ImmutableList<Address> getAddresses() {
            return ImmutableList.of(
                rabbitMQ1.address(), rabbitMQ2.address(), rabbitMQ3.address());
        }

        public ImmutableList<DockerRabbitMQ> getNodes() {
            return ImmutableList.of(rabbitMQ1, rabbitMQ2, rabbitMQ3);
        }

        public void detach() {
            rabbitMQ3.performIfRunning(DockerRabbitMQ::reset);
            rabbitMQ1.performIfRunning(rabbitMQ -> rabbitMQ.forgetNode(rabbitMQ3.getNodeName()));
            rabbitMQ2.performIfRunning(DockerRabbitMQ::reset);
            rabbitMQ1.performIfRunning(rabbitMQ -> rabbitMQ.forgetNode(rabbitMQ2.getNodeName()));
            rabbitMQ1.performIfRunning(DockerRabbitMQ::reset);
        }
    }
}
