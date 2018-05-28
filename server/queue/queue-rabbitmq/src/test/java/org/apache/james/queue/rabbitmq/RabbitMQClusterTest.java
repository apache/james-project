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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.james.queue.rabbitmq.DockerClusterRabbitMQExtention.DockerRabbitMQCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@ExtendWith(DockerClusterRabbitMQExtention.class)
class RabbitMQClusterTest {

    private static final String QUEUE = "queue";

    private Connection node1Connection;
    private Channel node1Channel;
    private Connection node2Connection;
    private Channel node2Channel;

    @BeforeEach
    void setup(DockerRabbitMQCluster cluster) throws IOException, TimeoutException {
        ConnectionFactory node1ConnectionFactory = cluster.getRabbitMQ1().connectionFactory();
        ConnectionFactory node2ConnectionFactory = cluster.getRabbitMQ2().connectionFactory();
        node1Connection = node1ConnectionFactory.newConnection();
        node2Connection = node2ConnectionFactory.newConnection();
        node1Channel = node1Connection.createChannel();
        node2Channel = node2Connection.createChannel();
    }

    @AfterEach
    void tearDown() {
        closeQuietly(node1Channel, node2Channel, node1Connection, node2Connection);
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

    private byte[] asBytes(String message) {
        return message.getBytes(StandardCharsets.UTF_8);
    }

}
