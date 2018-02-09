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
package org.apache.james.queue.rabbitmq;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.Network;

import com.google.common.collect.ImmutableList;

public class DockerClusterRabbitMQExtention implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    public static final String RABBIT_1 = "rabbit1";
    public static final String RABBIT_2 = "rabbit2";
    public static final String RABBIT_3 = "rabbit3";
    private DockerRabbitMQCluster cluster;
    private Network network;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        String cookie = DigestUtils.sha1Hex("secret cookie here");

        network = Network.NetworkImpl.builder()
            .enableIpv6(false)
            .createNetworkCmdModifiers(ImmutableList.of())
            .build();

        DockerRabbitMQ rabbitMQ1 = DockerRabbitMQ.withCookieAndNodeName(RABBIT_1, cookie, "rabbit@rabbit1", network);
        DockerRabbitMQ rabbitMQ2 = DockerRabbitMQ.withCookieAndNodeName(RABBIT_2, cookie, "rabbit@rabbit2", network);
        DockerRabbitMQ rabbitMQ3 = DockerRabbitMQ.withCookieAndNodeName(RABBIT_3, cookie, "rabbit@rabbit3", network);

        rabbitMQ1.start();
        rabbitMQ2.start();
        rabbitMQ3.start();

        rabbitMQ2.join(rabbitMQ1);
        rabbitMQ3.join(rabbitMQ1);

        rabbitMQ2.startApp();
        rabbitMQ3.startApp();

        cluster = new DockerRabbitMQCluster(rabbitMQ1, rabbitMQ2, rabbitMQ3);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
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
            rabbitMQ1.stop();
            rabbitMQ2.stop();
            rabbitMQ3.stop();
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
    }
}
