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

import static org.apache.james.queue.rabbitmq.RabbitMQFixture.AUTO_DELETE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.DIRECT;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.DURABLE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.EXCHANGE_NAME;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.EXCLUSIVE;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.NO_PROPERTIES;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.ROUTING_KEY;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.queue.rabbitmq.DockerClusterRabbitMQExtention.DockerRabbitMQCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.fge.lambdas.Throwing;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@ExtendWith(DockerClusterRabbitMQExtention.class)
class RabbitMQClusterTest {

    private static final String QUEUE = "queue";

    @Nested
    class ClusterSharing {

        private ConnectionFactory node1ConnectionFactory;
        private ConnectionFactory node2ConnectionFactory;
        private Connection node1Connection;
        private Connection node2Connection;
        private Channel node1Channel;
        private Channel node2Channel;

        @BeforeEach
        void setup(DockerRabbitMQCluster cluster) throws IOException, TimeoutException {
            node1ConnectionFactory = cluster.getRabbitMQ1().connectionFactory();
            node2ConnectionFactory = cluster.getRabbitMQ2().connectionFactory();
            node1Connection = node1ConnectionFactory.newConnection();
            node2Connection = node2ConnectionFactory.newConnection();
            node1Channel = node1Connection.createChannel();
            node2Channel = node2Connection.createChannel();
        }

        @AfterEach
        void tearDown() {
            closeQuietly(node1Channel, node2Channel, node1Connection, node2Connection);
        }

        @Test
        void rabbitMQManagerShouldReturnThreeNodesWhenAskingForStatus(DockerRabbitMQCluster cluster) throws Exception {
            String stdout = cluster.getRabbitMQ1().container()
                .execInContainer("rabbitmqctl", "cluster_status")
                .getStdout();

            assertThat(stdout)
                .contains(
                    DockerClusterRabbitMQExtention.RABBIT_1,
                    DockerClusterRabbitMQExtention.RABBIT_2,
                    DockerClusterRabbitMQExtention.RABBIT_3);
        }

        @Test
        void queuesShouldBeShared(DockerRabbitMQCluster cluster) throws Exception {
            node1Channel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
            node1Channel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            node1Channel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 10;
            IntStream.range(0, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> node1Channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));


            InMemoryConsumer consumer2 = new InMemoryConsumer(node2Channel);
            node2Channel.basicConsume(QUEUE, consumer2);

            awaitAtMostOneMinute.until(() -> consumer2.getConsumedMessages().size() == nbMessages);

            List<Integer> expectedResult = IntStream.range(0, nbMessages).boxed().collect(Collectors.toList());
            assertThat(consumer2.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
        }

        @Test
        void queuesShouldBeDeclarableOnAnotherNode(DockerRabbitMQCluster cluster) throws Exception {
            node1Channel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
            node2Channel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            node2Channel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 10;
            IntStream.range(0, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> node1Channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            InMemoryConsumer consumer2 = new InMemoryConsumer(node2Channel);
            node2Channel.basicConsume(QUEUE, consumer2);

            awaitAtMostOneMinute.until(() -> consumer2.getConsumedMessages().size() == nbMessages);

            List<Integer> expectedResult = IntStream.range(0, nbMessages).boxed().collect(Collectors.toList());
            assertThat(consumer2.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
        }

    }

    @Nested
    class ClusterNodesFailure {

        private ConnectionFactory node1ConnectionFactory;
        private Connection resilientConnection;
        private Channel resilientChannel;
        private Connection node2Connection;
        private Channel node2Channel;

        @BeforeEach
        void setup(DockerRabbitMQCluster cluster) throws IOException, TimeoutException {
            node1ConnectionFactory = cluster.getRabbitMQ1().connectionFactory();
            resilientConnection = node1ConnectionFactory.newConnection(cluster.getAddresses());
            resilientChannel = resilientConnection.createChannel();
            ConnectionFactory node2ConnectionFactory = cluster.getRabbitMQ2().connectionFactory();
            node2Connection = node2ConnectionFactory.newConnection();
            node2Channel = node2Connection.createChannel();
        }

        @AfterEach
        void tearDown() {
            closeQuietly(resilientConnection, resilientChannel);
        }

        @Disabled("Not sure Rabbit driver can recover at all in this case, it keeps failing even with retry")
        @RepeatedTest(10)
        void nodeKillingWhenProducing(DockerRabbitMQCluster cluster) throws Exception {
            resilientChannel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
            resilientChannel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            resilientChannel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 20;
            int firstBatchSize = nbMessages / 2;
            IntStream.range(0, firstBatchSize)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            cluster.getRabbitMQ1().stop();

            IntStream.range(firstBatchSize, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> Awaitility.waitAtMost(Duration.TEN_SECONDS).until(() -> tryPublish(bytes))));


            InMemoryConsumer consumer = new InMemoryConsumer(node2Channel);
            node2Channel.basicConsume(QUEUE, consumer);

            awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == nbMessages);

            List<Integer> expectedResult = IntStream.range(0, nbMessages).boxed().collect(Collectors.toList());
            assertThat(consumer.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
        }

        private boolean tryPublish(byte[] bytes) {
            try {
                resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes);
                return true;
            } catch (Exception e) {
                try {
                    System.out.println("trying recover");
                    resilientChannel.basicRecover();
                    System.out.println("recovered");
                } catch (Exception e1) {
                    System.out.println("failed recover");
                    //will return false
                }
                return false;
            }
        }

        @Test
        void connectingToAClusterWithAFailedRabbit(DockerRabbitMQCluster cluster) throws Exception {
            ConnectionFactory node3ConnectionFactory = cluster.getRabbitMQ3().connectionFactory();
            cluster.getRabbitMQ3().stop();

            try (Connection connection = node3ConnectionFactory.newConnection(cluster.getAddresses());
                 Channel channel = connection.createChannel()) {

                channel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
                channel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
                channel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                int nbMessages = 10;
                IntStream.range(0, nbMessages)
                    .mapToObj(i -> asBytes(String.valueOf(i)))
                    .forEach(Throwing.consumer(
                        bytes -> channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

                InMemoryConsumer consumer = new InMemoryConsumer(channel);
                channel.basicConsume(QUEUE, consumer);

                awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == nbMessages);

                List<Integer> expectedResult = IntStream.range(0, nbMessages).boxed().collect(Collectors.toList());
                assertThat(consumer.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
            }
        }

        @Test
        void nodeKillingWhenConsuming(DockerRabbitMQCluster cluster) throws Exception {
            node2Channel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
            node2Channel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            node2Channel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 10;
            IntStream.range(0, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            AtomicInteger counter = new AtomicInteger(0);
            InMemoryConsumer consumer = new InMemoryConsumer(resilientChannel,
                () -> {
                    if (counter.incrementAndGet() == nbMessages / 2) {
                        cluster.getRabbitMQ1().stop();
                    }
                });
            resilientChannel.basicConsume(QUEUE, consumer);

            awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == nbMessages);

            List<Integer> expectedResult = IntStream.range(0, nbMessages).boxed().collect(Collectors.toList());
            assertThat(consumer.getConsumedMessages()).containsOnlyElementsOf(expectedResult);
        }

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

    private byte[] asBytes(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
