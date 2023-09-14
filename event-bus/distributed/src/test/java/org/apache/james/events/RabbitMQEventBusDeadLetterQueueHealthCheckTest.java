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

package org.apache.james.events;

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.EXCHANGE_NAME;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

class RabbitMQEventBusDeadLetterQueueHealthCheckTest {
    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    public static final ImmutableMap<String, Object> NO_QUEUE_DECLARE_ARGUMENTS = ImmutableMap.of();
    public static final NamingStrategy MAILBOX_EVENTS_NAMING_STRATEGY = new NamingStrategy(new EventBusName("mailboxEvents"));
    public static final String ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS = "mailboxEventsRoutingKey";
    public static final String ROUTING_KEY_JMAP_EVENTS_EVENT_BUS = "mailboxEventsRoutingKey";

    private Connection connection;
    private Channel channel;
    private RabbitMQEventBusDeadLetterQueueHealthCheck testee;

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ) throws IOException, TimeoutException, URISyntaxException {
        ConnectionFactory connectionFactory = rabbitMQ.connectionFactory();
        connectionFactory.setNetworkRecoveryInterval(1000);
        connection = connectionFactory.newConnection();
        channel = connection.createChannel();
        testee = new RabbitMQEventBusDeadLetterQueueHealthCheck(rabbitMQ.getConfiguration(), MAILBOX_EVENTS_NAMING_STRATEGY);
    }

    @AfterEach
    void tearDown(DockerRabbitMQ rabbitMQ) throws Exception {
        closeQuietly(connection, channel);
        rabbitMQ.reset();
    }

    @Test
    void healthCheckShouldReturnUnhealthyWhenRabbitMQIsDown() throws Exception {
        rabbitMQExtension.getRabbitMQ().stopApp();

        assertThat(testee.check().block().isUnHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnHealthyWhenDeadLetterQueuesAreEmpty() throws Exception {
        createDeadLetterQueue(channel, MAILBOX_EVENTS_NAMING_STRATEGY, ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS);
        createDeadLetterQueue(channel, NamingStrategy.JMAP_NAMING_STRATEGY, ROUTING_KEY_JMAP_EVENTS_EVENT_BUS);

        assertThat(testee.check().block().isHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnUnhealthyWhenThereIsNoDeadLetterQueue() {
        assertThat(testee.check().block().isUnHealthy()).isTrue();
    }

    @Test
    void healthCheckShouldReturnDegradedWhenMailboxEventBusDeadLetterQueueIsNotEmpty() throws Exception {
        createDeadLetterQueue(channel, MAILBOX_EVENTS_NAMING_STRATEGY, ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS);
        createDeadLetterQueue(channel, NamingStrategy.JMAP_NAMING_STRATEGY, ROUTING_KEY_JMAP_EVENTS_EVENT_BUS);
        publishAMessage(channel, ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS);

        awaitAtMostOneMinute.until(() -> testee.check().block().isDegraded());
    }

    @Test
    void healthCheckShouldReturnDegradedWhenJmapEventBusDeadLetterQueueIsNotEmpty() throws Exception {
        createDeadLetterQueue(channel, MAILBOX_EVENTS_NAMING_STRATEGY, ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS);
        createDeadLetterQueue(channel, NamingStrategy.JMAP_NAMING_STRATEGY, ROUTING_KEY_JMAP_EVENTS_EVENT_BUS);
        publishAMessage(channel, ROUTING_KEY_JMAP_EVENTS_EVENT_BUS);

        awaitAtMostOneMinute.until(() -> testee.check().block().isDegraded());
    }

    @Test
    void healthCheckShouldReturnDegradedWhenDeadLetterQueuesAreNotEmpty() throws Exception {
        createDeadLetterQueue(channel, MAILBOX_EVENTS_NAMING_STRATEGY, ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS);
        createDeadLetterQueue(channel, NamingStrategy.JMAP_NAMING_STRATEGY, ROUTING_KEY_JMAP_EVENTS_EVENT_BUS);

        publishAMessage(channel, ROUTING_KEY_MAILBOX_EVENTS_EVENT_BUS);
        publishAMessage(channel, ROUTING_KEY_JMAP_EVENTS_EVENT_BUS);

        awaitAtMostOneMinute.until(() -> testee.check().block().isDegraded());
    }

    private void createDeadLetterQueue(Channel channel, NamingStrategy namingStrategy, String routingKey) throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
        channel.queueDeclare(namingStrategy.deadLetterQueue().getName(), DURABLE, !EXCLUSIVE, AUTO_DELETE, NO_QUEUE_DECLARE_ARGUMENTS).getQueue();
        channel.queueBind(namingStrategy.deadLetterQueue().getName(), EXCHANGE_NAME, routingKey);
    }

    private void publishAMessage(Channel channel, String routingKey) throws IOException {
        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .priority(PERSISTENT_TEXT_PLAIN.getPriority())
            .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
            .build();

        channel.basicPublish(EXCHANGE_NAME, routingKey, basicProperties, "Hello, world!".getBytes(StandardCharsets.UTF_8));
    }

    private void closeQuietly(AutoCloseable... closeables) {
        Arrays.stream(closeables).forEach(this::closeQuietly);
    }

    private void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            //ignore error
        }
    }
}
