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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.NO_PROPERTIES;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.EXCHANGE_NAME;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.james.backends.rabbitmq.DockerClusterRabbitMQExtension.DockerRabbitMQCluster;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@Disabled("JAMES-2577 RabbitMQClusterTest is not stable and usually reach out of wait timeout when starting up 3 dockers" +
    " or joining them together")
class RabbitMQClusterTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQClusterTest.class);

    private static final String QUEUE = "queue";

    @RegisterExtension
    static DockerClusterRabbitMQExtension testExtension = new DockerClusterRabbitMQExtension();

    @BeforeAll
    static void setup() {
        testExtension.beforeAll();
    }

    @AfterAll
    static void tearDown() throws Exception {
        testExtension.afterAll();
    }

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
                    cluster.getRabbitMQ1().getNodeName(),
                    cluster.getRabbitMQ2().getNodeName(),
                    cluster.getRabbitMQ3().getNodeName());
        }

        @Test
        void queuesShouldBeShared() throws Exception {
            node1Channel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
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

            Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);
            assertThat(consumer2.getConsumedMessages()).containsOnly(expectedResult);
        }

        @Test
        void queuesShouldBeDeclarableOnAnotherNode() throws Exception {
            node1Channel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
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

            Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);
            assertThat(consumer2.getConsumedMessages()).containsOnly(expectedResult);
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
            node1ConnectionFactory.setNetworkRecoveryInterval(100);
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

        @Test
        void connectionShouldBeRecoveredWhenConnectedNodeIsDown(DockerRabbitMQCluster cluster) throws Exception {
            // find the connected node and shutdown the node
            DockerRabbitMQ connectedNode = getConnectedNode(cluster, resilientChannel);
            connectedNode.stop();

            // give sometime to the connection recovered
            Thread.sleep(100L);

            // make sure the connection can be recovered (to other nodes)
            resilientChannel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
            resilientChannel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            resilientChannel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 10;
            IntStream.range(0, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            InMemoryConsumer consumer = new InMemoryConsumer(resilientChannel);
            resilientChannel.basicConsume(QUEUE, consumer);

            awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == nbMessages);

            Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);
            assertThat(consumer.getConsumedMessages()).containsOnly(expectedResult);
        }

        private DockerRabbitMQ getConnectedNode(DockerRabbitMQCluster cluster, Channel resilientChannel) {
            return cluster.getNodes()
                .stream()
                .filter(node -> node.getNodeName().equals(resilientChannel.getConnection().getServerProperties().get("cluster_name").toString()))
                .findFirst()
                .get();
        }


        @Disabled("JAMES-2334 For some reason, we are unable to recover topology when reconnecting" +
            "See https://github.com/rabbitmq/rabbitmq-server/issues/959")
        @Test
        void nodeKillingWhenProducing(DockerRabbitMQCluster cluster) throws Exception {
            resilientChannel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
            resilientChannel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            resilientChannel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 20;
            int firstBatchSize = nbMessages / 2;
            IntStream.range(0, firstBatchSize)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            InMemoryConsumer consumer = new InMemoryConsumer(node2Channel);
            node2Channel.basicConsume(QUEUE, consumer);
            awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == firstBatchSize);

            cluster.getRabbitMQ1().stop();

            IntStream.range(firstBatchSize, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(this::tryPublishWithRetry);

            awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == nbMessages);

            Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);
            assertThat(consumer.getConsumedMessages()).containsOnly(expectedResult);
        }

        private void tryPublishWithRetry(byte[] bytes) {
            Awaitility.waitAtMost(ONE_MINUTE).pollInterval(ONE_SECOND).until(() -> tryPublish(bytes));
        }

        private boolean tryPublish(byte[] bytes) {
            try {
                resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes);
                return true;
            } catch (Exception e) {
                LOGGER.error("failed publish", e);
                return false;
            }
        }

        @Test
        void connectingToAClusterWithAFailedRabbit(DockerRabbitMQCluster cluster) throws Exception {
            ConnectionFactory node3ConnectionFactory = cluster.getRabbitMQ3().connectionFactory();
            ImmutableList<Address> clusterAddresses = cluster.getAddresses();
            cluster.getRabbitMQ3().stop();

            try (Connection connection = node3ConnectionFactory.newConnection(clusterAddresses);
                 Channel channel = connection.createChannel()) {

                channel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
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

                Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);
                assertThat(consumer.getConsumedMessages()).containsOnly(expectedResult);
            }
        }

        @Disabled("JAMES-2334 For some reason, we are unable to recover topology when reconnecting" +
            "See https://github.com/rabbitmq/rabbitmq-server/issues/959" +
            "This test have roughly 4% chance to fail")
        @Test
        void nodeKillingWhenConsuming(DockerRabbitMQCluster cluster) throws Exception {
            resilientChannel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
            resilientChannel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            resilientChannel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            int nbMessages = 10;
            IntStream.range(0, nbMessages)
                .mapToObj(i -> asBytes(String.valueOf(i)))
                .forEach(Throwing.consumer(
                    bytes -> resilientChannel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            AtomicInteger counter = new AtomicInteger(0);
            InMemoryConsumer consumer = new InMemoryConsumer(resilientChannel,
                (envelope, body) -> stopWhenHalfProcessed(cluster, nbMessages, counter));
            resilientChannel.basicConsume(QUEUE, consumer);

            awaitAtMostOneMinute.until(() -> consumer.getConsumedMessages().size() == nbMessages);

            Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);
            assertThat(consumer.getConsumedMessages()).containsOnly(expectedResult);
        }

        private void stopWhenHalfProcessed(DockerRabbitMQCluster cluster, int nbMessages, AtomicInteger counter) {
            if (counter.incrementAndGet() == nbMessages / 2) {
                cluster.getRabbitMQ1().stop();
            }
        }

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

    private byte[] asBytes(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }
}
