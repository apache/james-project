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
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.MESSAGES;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.MESSAGES_AS_BYTES;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.NO_PROPERTIES;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.ROUTING_KEY;
import static org.apache.james.queue.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.james.queue.rabbitmq.DockerClusterRabbitMQExtention.DockerRabbitMQCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import com.github.fge.lambdas.Throwing;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@ExtendWith(DockerClusterRabbitMQExtention.class)
class RabbitMQClusterTest {

    public static final String QUEUE = "queue";

    @AfterEach
    public void tearDown(DockerRabbitMQCluster cluster) throws Exception {
        cluster.getRabbitMQ2()
            .connectionFactory()
            .newConnection()
            .createChannel()
            .queueDelete(QUEUE);
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
    public void queuesShouldBeShared(DockerRabbitMQCluster cluster) throws Exception {
        ConcurrentLinkedDeque<Integer> result = new ConcurrentLinkedDeque<>();

        ConnectionFactory connectionFactory1 = cluster.getRabbitMQ1().connectionFactory();
        ConnectionFactory connectionFactory2 = cluster.getRabbitMQ2().connectionFactory();

        try (Connection connection = connectionFactory1.newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
            channel.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            channel.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            MESSAGES_AS_BYTES.forEach(Throwing.consumer(
                    bytes -> channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));
        }

        try (Connection connection2 = connectionFactory2.newConnection();
             Channel channel2 = connection2.createChannel()) {

            InMemoryConsumer consumer2 = new InMemoryConsumer(channel2);
            channel2.basicConsume(QUEUE, consumer2);

            awaitAtMostOneMinute.until(() -> consumer2.getConsumedMessages().size() == MESSAGES.size());

            assertThat(consumer2.getConsumedMessages())
                .containsOnlyElementsOf(MESSAGES);
        }

        assertThat(result)
            .containsOnlyElementsOf(MESSAGES);
    }

    @Test
    public void queuesShouldBeDeclarableOnAnotherNode(DockerRabbitMQCluster cluster) throws Exception {
        ConnectionFactory connectionFactory1 = cluster.getRabbitMQ1().connectionFactory();
        ConnectionFactory connectionFactory2 = cluster.getRabbitMQ2().connectionFactory();

        try (Connection connection = connectionFactory1.newConnection();
             Channel channel = connection.createChannel();
             Connection connection2 = connectionFactory2.newConnection();
             Channel channel2 = connection2.createChannel()) {

            channel.exchangeDeclare(EXCHANGE_NAME, DIRECT, DURABLE);
            channel2.queueDeclare(QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of()).getQueue();
            channel2.queueBind(QUEUE, EXCHANGE_NAME, ROUTING_KEY);

            MESSAGES_AS_BYTES.forEach(Throwing.consumer(
                    bytes -> channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)));

            InMemoryConsumer consumer2 = new InMemoryConsumer(channel2);
            channel2.basicConsume(QUEUE, consumer2);

            awaitAtMostOneMinute.until(() -> consumer2.getConsumedMessages().size() == MESSAGES.size());

            assertThat(consumer2.getConsumedMessages())
                .containsOnlyElementsOf(MESSAGES);
        }
    }

}
