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

import org.apache.james.util.docker.SwarmGenericContainer;
import org.junit.rules.ExternalResource;

import com.jayway.awaitility.Awaitility;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

public class AmqpRule extends ExternalResource {

    private static final boolean AUTO_ACK = true;

    private final SwarmGenericContainer rabbitMqContainer;
    private final String exchangeName;
    private final String routingKey;
    private Channel channel;
    private String queueName;
    private Connection connection;
    private String amqpUri;

    public AmqpRule(SwarmGenericContainer rabbitMqContainer, String exchangeName, String routingKey) {
        this.rabbitMqContainer = rabbitMqContainer;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
    }

    @Override
    protected void before() throws Throwable {
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

    @Override
    protected void after() {
        try {
            channel.close();
            connection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void waitingForRabbitToBeReady(ConnectionFactory factory) {
        Awaitility
            .await()
            .atMost(30, TimeUnit.SECONDS)
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
