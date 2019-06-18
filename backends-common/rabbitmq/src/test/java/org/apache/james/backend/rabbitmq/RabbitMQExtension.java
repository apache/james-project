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
package org.apache.james.backend.rabbitmq;

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.util.function.Consumer;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class RabbitMQExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private static final Consumer<DockerRabbitMQ> DO_NOTHING = dockerRabbitMQ -> {};

    public enum DockerRestartPolicy {
        PER_TEST(DockerRabbitMQ::start, DockerRabbitMQ::start, DockerRabbitMQ::stop, DockerRabbitMQ::stop),
        PER_CLASS(DockerRabbitMQ::start, DO_NOTHING, DO_NOTHING, DockerRabbitMQ::stop),
        NEVER(DockerRabbitMQ::start, DO_NOTHING, DO_NOTHING, DO_NOTHING);

        private final Consumer<DockerRabbitMQ> beforeAllCallback;
        private final Consumer<DockerRabbitMQ> beforeEachCallback;
        private final Consumer<DockerRabbitMQ> afterEachCallback;
        private final Consumer<DockerRabbitMQ> afterAllCallback;

        DockerRestartPolicy(Consumer<DockerRabbitMQ> beforeAllCallback,
                            Consumer<DockerRabbitMQ> beforeEachCallback,
                            Consumer<DockerRabbitMQ> afterEachCallback,
                            Consumer<DockerRabbitMQ> afterAllCallback) {
            this.beforeAllCallback = beforeAllCallback;
            this.beforeEachCallback = beforeEachCallback;
            this.afterEachCallback = afterEachCallback;
            this.afterAllCallback = afterAllCallback;
        }

        public void beforeAll(DockerRabbitMQ dockerRabbitMQ) {
            beforeAllCallback.accept(dockerRabbitMQ);
        }

        public void afterAll(DockerRabbitMQ dockerRabbitMQ) {
            afterAllCallback.accept(dockerRabbitMQ);
        }

        public void afterEach(DockerRabbitMQ dockerRabbitMQ) {
            afterEachCallback.accept(dockerRabbitMQ);
        }

        public void beforeEach(DockerRabbitMQ dockerRabbitMQ) {
            beforeEachCallback.accept(dockerRabbitMQ);
        }
    }

    @FunctionalInterface
    public interface RequireRestartPolicy {
        RabbitMQExtension restartPolicy(DockerRestartPolicy dockerRestartPolicy);
    }

    public static RabbitMQExtension singletonRabbitMQ() {
        return new RabbitMQExtension(DockerRabbitMQSingleton.SINGLETON, DockerRestartPolicy.NEVER);
    }

    public static RequireRestartPolicy defaultRabbitMQ() {
        return dockerRabbitMQ(DockerRabbitMQ.withoutCookie());
    }

    public static RequireRestartPolicy dockerRabbitMQ(DockerRabbitMQ dockerRabbitMQ) {
        return dockerRestartPolicy -> new RabbitMQExtension(dockerRabbitMQ, dockerRestartPolicy);
    }

    private final DockerRabbitMQ rabbitMQ;
    private final DockerRestartPolicy dockerRestartPolicy;

    private SimpleChannelPool simpleChannelPool;
    private RabbitMQConnectionFactory connectionFactory;
    private SimpleConnectionPool connectionPool;

    public RabbitMQExtension(DockerRabbitMQ rabbitMQ,
                             DockerRestartPolicy dockerRestartPolicy) {
        this.rabbitMQ = rabbitMQ;
        this.dockerRestartPolicy = dockerRestartPolicy;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        dockerRestartPolicy.beforeAll(rabbitMQ);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        dockerRestartPolicy.beforeEach(rabbitMQ);

        connectionFactory = createRabbitConnectionFactory();
        connectionPool = new SimpleConnectionPool(connectionFactory);
        this.simpleChannelPool = new SimpleChannelPool(connectionPool);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        simpleChannelPool.close();
        connectionPool.close();

        dockerRestartPolicy.afterEach(rabbitMQ);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        dockerRestartPolicy.afterAll(rabbitMQ);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerRabbitMQ.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return rabbitMQ;
    }

    public RabbitMQChannelPool getRabbitChannelPool() {
        return simpleChannelPool;
    }

    public SimpleConnectionPool getRabbitConnectionPool() {
        return connectionPool;
    }

    public DockerRabbitMQ getRabbitMQ() {
        return rabbitMQ;
    }

    public RabbitMQManagementAPI managementAPI() throws Exception {
        return RabbitMQManagementAPI.from(RabbitMQConfiguration.builder()
                .amqpUri(rabbitMQ.amqpUri())
                .managementUri(rabbitMQ.managementUri())
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .build());
    }

    private RabbitMQConnectionFactory createRabbitConnectionFactory() throws URISyntaxException {
        return rabbitMQ.createRabbitConnectionFactory();
    }
}