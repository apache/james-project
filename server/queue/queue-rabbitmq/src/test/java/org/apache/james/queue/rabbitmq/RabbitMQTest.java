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
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

@ExtendWith(DockerRabbitMQExtension.class)
class RabbitMQTest {

    private static final byte[] PAYLOAD = "Hello, world!".getBytes(StandardCharsets.UTF_8);

    @Nested
    class SingleConsumerTest {

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

    @Nested
    class BroadcastTest {

        private ConnectionFactory connectionFactory1;
        private ConnectionFactory connectionFactory2;
        private ConnectionFactory connectionFactory3;
        private ConnectionFactory connectionFactory4;

        @BeforeEach
        public void setup(DockerRabbitMQ rabbitMQ) {
            connectionFactory1 = rabbitMQ.connectionFactory();
            connectionFactory2 = rabbitMQ.connectionFactory();
            connectionFactory3 = rabbitMQ.connectionFactory();
            connectionFactory4 = rabbitMQ.connectionFactory();
        }

        // In the following case, each consumer will receive the messages produced by the
        // producer
        // To do so, each consumer will bind it's queue to the producer exchange.
        @Test
        public void rabbitMQShouldSupportTheBroadcastCase() throws Exception {
            ImmutableList<Integer> expectedResult = IntStream.range(0, 10).boxed().collect(Guavate.toImmutableList());
            ConcurrentLinkedQueue<Integer> results2 = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Integer> results3 = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Integer> results4 = new ConcurrentLinkedQueue<>();

            try (Connection connection1 = connectionFactory1.newConnection();
                 Channel publisherChannel = connection1.createChannel();
                 Connection connection2 = connectionFactory2.newConnection();
                 Channel subscriberChannel2 = connection2.createChannel();
                 Connection connection3 = connectionFactory3.newConnection();
                 Channel subscriberChannel3 = connection3.createChannel();
                 Connection connection4 = connectionFactory4.newConnection();
                 Channel subscriberChannel4 = connection4.createChannel()) {

                // Declare a single exchange and three queues attached to it.
                publisherChannel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);

                String queue2 = subscriberChannel2.queueDeclare().getQueue();
                subscriberChannel2.queueBind(queue2, EXCHANGE_NAME, ROUTING_KEY);
                String queue3 = subscriberChannel3.queueDeclare().getQueue();
                subscriberChannel3.queueBind(queue3, EXCHANGE_NAME, ROUTING_KEY);
                String queue4 = subscriberChannel4.queueDeclare().getQueue();
                subscriberChannel4.queueBind(queue4, EXCHANGE_NAME, ROUTING_KEY);

                subscriberChannel2.basicConsume(queue2, storeInResultCallBack(subscriberChannel2, results2));
                subscriberChannel3.basicConsume(queue3, storeInResultCallBack(subscriberChannel3, results3));
                subscriberChannel4.basicConsume(queue4, storeInResultCallBack(subscriberChannel4, results4));

                // the publisher will produce 10 messages
                IntStream.range(0, 10)
                    .mapToObj(String::valueOf)
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .forEach(Throwing.consumer(
                        bytes -> publisherChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

                awaitAtMostOneMinute.until(() -> allMessageReceived(expectedResult, results2, results3, results4));

                // Check every subscriber have receive all the messages.
                assertThat(results2).containsOnlyElementsOf(expectedResult);
                assertThat(results3).containsOnlyElementsOf(expectedResult);
                assertThat(results4).containsOnlyElementsOf(expectedResult);
            }
        }

        private boolean allMessageReceived(ImmutableList<Integer> expectedResult, ConcurrentLinkedQueue<Integer> results2, ConcurrentLinkedQueue<Integer> results3, ConcurrentLinkedQueue<Integer> results4) {
            return Iterables.size(
                Iterables.concat(results2, results3, results4))
                == expectedResult.size() * 3;
        }

        private DefaultConsumer storeInResultCallBack(Channel channel, ConcurrentLinkedQueue<Integer> results) {
            return new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    Integer payload = Integer.valueOf(new String(body, StandardCharsets.UTF_8));
                    results.add(payload);
                }
            };
        }
    }


}
