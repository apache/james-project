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

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.function.Consumer;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.github.fge.lambdas.consumers.ThrowingConsumer;

import reactor.rabbitmq.Sender;

public class RabbitMQExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private static final Consumer<DockerRabbitMQ> DO_NOTHING = dockerRabbitMQ -> {
    };

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

    public enum IsolationPolicy {
        WEAK(any -> {
        }),
        STRONG(DockerRabbitMQ::reset);

        private final ThrowingConsumer<DockerRabbitMQ> isolationCall;

        IsolationPolicy(ThrowingConsumer<DockerRabbitMQ> isolationCall) {
            this.isolationCall = isolationCall;
        }

        void enforceIsolation(DockerRabbitMQ container) {
            isolationCall.accept(container);
        }
    }

    @FunctionalInterface
    public interface RequireRestartPolicy {
        RequireIsolationPolicy restartPolicy(DockerRestartPolicy dockerRestartPolicy);
    }

    @FunctionalInterface
    public interface RequireIsolationPolicy {
        RabbitMQExtension isolationPolicy(IsolationPolicy isolationPolicy);
    }

    public static RequireIsolationPolicy singletonRabbitMQ() {
        return isolationPolicy -> new RabbitMQExtension(DockerRabbitMQSingleton.SINGLETON, DockerRestartPolicy.NEVER, isolationPolicy);
    }

    public static RequireRestartPolicy defaultRabbitMQ() {
        return dockerRabbitMQ(DockerRabbitMQ.withoutCookie());
    }

    public static RequireRestartPolicy dockerRabbitMQ(DockerRabbitMQ dockerRabbitMQ) {
        return dockerRestartPolicy -> isolationPolicy -> new RabbitMQExtension(dockerRabbitMQ, dockerRestartPolicy, isolationPolicy);
    }

    private final DockerRabbitMQ rabbitMQ;
    private final DockerRestartPolicy dockerRestartPolicy;
    private final IsolationPolicy isolationPolicy;

    private ReactorRabbitMQChannelPool channelPool;
    private SimpleConnectionPool connectionPool;

    public RabbitMQExtension(DockerRabbitMQ rabbitMQ,
                             DockerRestartPolicy dockerRestartPolicy, IsolationPolicy isolationPolicy) {
        this.rabbitMQ = rabbitMQ;
        this.dockerRestartPolicy = dockerRestartPolicy;
        this.isolationPolicy = isolationPolicy;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        dockerRestartPolicy.beforeAll(rabbitMQ);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        dockerRestartPolicy.beforeEach(rabbitMQ);

        RabbitMQConnectionFactory connectionFactory = createRabbitConnectionFactory();
        connectionPool = new SimpleConnectionPool(connectionFactory,
            SimpleConnectionPool.Configuration.builder()
                .retries(2)
                .initialDelay(Duration.ofMillis(5)));
        channelPool = new ReactorRabbitMQChannelPool(connectionPool.getResilientConnection(),
            ReactorRabbitMQChannelPool.Configuration.builder()
                .retries(2)
                .maxBorrowDelay(Duration.ofMillis(250))
                .maxChannel(10),
            new RecordingMetricFactory());
        channelPool.start();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        channelPool.close();
        connectionPool.close();
        isolationPolicy.enforceIsolation(rabbitMQ);
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

    public ReactorRabbitMQChannelPool getRabbitChannelPool() {
        return channelPool;
    }

    public Sender getSender() {
        return channelPool.getSender();
    }

    public ReceiverProvider getReceiverProvider() {
        return channelPool::createReceiver;
    }

    public SimpleConnectionPool getConnectionPool() {
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