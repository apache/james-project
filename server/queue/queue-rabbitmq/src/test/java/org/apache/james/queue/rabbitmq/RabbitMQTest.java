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

import static org.apache.james.queue.rabbitmq.RabbitMQFixture.AUTO_ACK;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.DIRECT;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.DURABLE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.EXCHANGE_NAME;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.NO_PROPERTIES;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.ROUTING_KEY;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@ExtendWith(DockerRabbitMQExtension.class)
class RabbitMQTest {

    private static final byte[] PAYLOAD = "Hello, world!".getBytes(StandardCharsets.UTF_8);

    @Test
    void publishedEventWithoutSubscriberShouldNotBeLost(DockerRabbitMQ rabbitMQ) throws Exception {
        ConnectionFactory connectionFactory = rabbitMQ.connectionFactory();
        try (Connection connection = connectionFactory.newConnection();
                Channel channel = connection.createChannel()) {
            String queueName = createQueue(channel);

            publishAMessage(channel);

            awaitAtMostOneMinute.until(() -> messageReceived(channel, queueName));
        }
    }

    private String createQueue(Channel channel) throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
        return queueName;
    }

    private void publishAMessage(Channel channel) throws IOException {
        channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, PAYLOAD);
    }

    private Boolean messageReceived(Channel channel, String queueName) {
        try {
            return channel.basicGet(queueName, !AUTO_ACK) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
