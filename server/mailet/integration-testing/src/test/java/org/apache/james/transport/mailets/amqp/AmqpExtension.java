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

package org.apache.james.transport.mailets.amqp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

public class AmqpExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback {
    private static final boolean AUTO_ACK = true;
    private static final Logger logger = LoggerFactory.getLogger(AmqpExtension.class);

    private final DockerContainer rabbitMqContainer;
    private final String exchangeName;
    private final String routingKey;
    private Channel channel;
    private String queueName;
    private Connection connection;
    private String amqpUri;

    public AmqpExtension(String exchangeName, String routingKey) {
        this.rabbitMqContainer = DockerContainer.fromName(Images.RABBITMQ)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy()
                .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND))
                .withLogConsumer(AmqpExtension::displayDockerLog);
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
    }

    private static void displayDockerLog(OutputFrame outputFrame) {
        logger.debug(outputFrame.getUtf8String().trim());
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        rabbitMqContainer.start();
        amqpUri = "amqp://" + rabbitMqContainer.getContainerIp();
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(amqpUri);
        waitingForRabbitToBeReady(factory);
        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT);
        queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, exchangeName, routingKey);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        try {
            channel.close();
            connection.close();
            rabbitMqContainer.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        readAll();
    }

    public String getAmqpUri() {
        return amqpUri;
    }

    public void readAll() throws IOException {
        while (channel.basicGet(queueName, AUTO_ACK) != null) {

        }
    }

    public Optional<String> readContent() throws IOException {
        return readContentAsBytes()
            .map(value -> new String(value, StandardCharsets.UTF_8));
    }

    public Optional<byte[]> readContentAsBytes() throws IOException {
        return Optional.ofNullable(channel.basicGet(queueName, AUTO_ACK))
            .map(GetResponse::getBody);
    }

    private void waitingForRabbitToBeReady(ConnectionFactory factory) {
        Awaitility
            .await()
            .atMost(60, TimeUnit.SECONDS)
            .with()
            .pollInterval(10, TimeUnit.MILLISECONDS)
            .until(() -> isReady(factory));
    }

    private boolean isReady(ConnectionFactory factory) {
        try (Connection connection = factory.newConnection()) {
            return true;
        } catch (IOException | TimeoutException e) {
            return false;
        }
    }
}
