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
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.AUTO_DELETE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.DIRECT;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.DURABLE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.EXCHANGE_NAME;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.EXCLUSIVE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.NO_PROPERTIES;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.ROUTING_KEY;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.WORK_QUEUE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

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
    class FourConnections {

        private ConnectionFactory connectionFactory1;
        private ConnectionFactory connectionFactory2;
        private ConnectionFactory connectionFactory3;
        private ConnectionFactory connectionFactory4;
        private Connection connection1;
        private Connection connection2;
        private Connection connection3;
        private Connection connection4;
        private Channel publisherChannel;
        private Channel subscriberChannel2;
        private Channel subscriberChannel3;
        private Channel subscriberChannel4;

        @BeforeEach
        void setup(DockerRabbitMQ rabbitMQ) throws IOException, TimeoutException {
            connectionFactory1 = rabbitMQ.connectionFactory();
            connectionFactory2 = rabbitMQ.connectionFactory();
            connectionFactory3 = rabbitMQ.connectionFactory();
            connectionFactory4 = rabbitMQ.connectionFactory();
            connection1 = connectionFactory1.newConnection();
            connection2 = connectionFactory2.newConnection();
            connection3 = connectionFactory3.newConnection();
            connection4 = connectionFactory4.newConnection();
            publisherChannel = connection1.createChannel();
            subscriberChannel2 = connection2.createChannel();
            subscriberChannel3 = connection3.createChannel();
            subscriberChannel4 = connection4.createChannel();
        }

        @AfterEach
        void tearDown() {
            closeQuietly(
                publisherChannel, subscriberChannel2, subscriberChannel3, subscriberChannel4,
                connection1, connection2, connection3, connection4);
        }

        private void closeQuietly(AutoCloseable... closeables) {
            for (AutoCloseable closeable : closeables) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    //ignoring exception
                }
            }
        }

        @Nested
        class BroadCast {

            // In the following case, each consumer will receive the messages produced by the
            // producer
            // To do so, each consumer will bind it's queue to the producer exchange.
            @Test
            void rabbitMQShouldSupportTheBroadcastCase() throws Exception {
                // Declare a single exchange and three queues attached to it.
                publisherChannel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);

                String queue2 = subscriberChannel2.queueDeclare().getQueue();
                subscriberChannel2.queueBind(queue2, EXCHANGE_NAME, ROUTING_KEY);
                String queue3 = subscriberChannel3.queueDeclare().getQueue();
                subscriberChannel3.queueBind(queue3, EXCHANGE_NAME, ROUTING_KEY);
                String queue4 = subscriberChannel4.queueDeclare().getQueue();
                subscriberChannel4.queueBind(queue4, EXCHANGE_NAME, ROUTING_KEY);

                InMemoryConsumer consumer2 = new InMemoryConsumer(subscriberChannel2);
                InMemoryConsumer consumer3 = new InMemoryConsumer(subscriberChannel3);
                InMemoryConsumer consumer4 = new InMemoryConsumer(subscriberChannel4);
                subscriberChannel2.basicConsume(queue2, consumer2);
                subscriberChannel3.basicConsume(queue3, consumer3);
                subscriberChannel4.basicConsume(queue4, consumer4);

                // the publisher will produce 10 messages
                IntStream.range(0, 10)
                    .mapToObj(String::valueOf)
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .forEach(Throwing.consumer(
                        bytes -> publisherChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

                awaitAtMostOneMinute.until(
                    () -> countReceivedMessages(consumer2, consumer3, consumer4) == 30);

                ImmutableList<Integer> expectedResult = IntStream.range(0, 10).boxed().collect(Guavate.toImmutableList());
                // Check every subscriber have receive all the messages.
                assertThat(consumer2.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
                assertThat(consumer3.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
                assertThat(consumer4.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
            }
        }

        @Nested
        class WorkQueue {

            // In the following case, consumers will receive the messages produced by the
            // producer but will share them.
            // To do so, we will bind a single queue to the producer exchange.
            @Test
            void rabbitMQShouldSupportTheWorkQueueCase() throws Exception {
                int nbMessages = 100;

                // Declare the exchange and a single queue attached to it.
                publisherChannel.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                publisherChannel.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, AUTO_DELETE, ImmutableMap.of());
                publisherChannel.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                // Publisher will produce 100 messages
                IntStream.range(0, nbMessages)
                    .mapToObj(String::valueOf)
                    .map(s -> s.getBytes(StandardCharsets.UTF_8))
                    .forEach(Throwing.consumer(
                        bytes -> publisherChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

                InMemoryConsumer consumer2 = new InMemoryConsumer(subscriberChannel2);
                InMemoryConsumer consumer3 = new InMemoryConsumer(subscriberChannel3);
                InMemoryConsumer consumer4 = new InMemoryConsumer(subscriberChannel4);
                subscriberChannel2.basicConsume(WORK_QUEUE, consumer2);
                subscriberChannel3.basicConsume(WORK_QUEUE, consumer3);
                subscriberChannel4.basicConsume(WORK_QUEUE, consumer4);

                awaitAtMostOneMinute.until(
                    () -> countReceivedMessages(consumer2, consumer3, consumer4) == nbMessages);

                ImmutableList<Integer> expectedResult = IntStream.range(0, nbMessages).boxed().collect(Guavate.toImmutableList());

                assertThat(
                    Iterables.concat(
                        consumer2.getConsumedMessages(),
                        consumer3.getConsumedMessages(),
                        consumer4.getConsumedMessages()))
                    .containsOnlyElementsOf(expectedResult);
            }

        }

        private long countReceivedMessages(InMemoryConsumer... consumers) {
            return Arrays.stream(consumers)
                .map(InMemoryConsumer::getConsumedMessages)
                .mapToLong(Queue::size)
                .sum();
        }

    }


}
