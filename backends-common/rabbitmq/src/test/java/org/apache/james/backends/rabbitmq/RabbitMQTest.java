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

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_ACK;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.MULTIPLE;
import static org.apache.james.backends.rabbitmq.Constants.NO_LOCAL;
import static org.apache.james.backends.rabbitmq.Constants.NO_PROPERTIES;
import static org.apache.james.backends.rabbitmq.Constants.REQUEUE;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.EXCHANGE_NAME;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.WORK_QUEUE;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.WORK_QUEUE_2;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

class RabbitMQTest {

    public static final ImmutableMap<String, Object> NO_QUEUE_DECLARE_ARGUMENTS = ImmutableMap.of();
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    @Nested
    class SingleConsumerTest {

        private ConnectionFactory connectionFactory;
        private Connection connection;
        private Channel channel;

        @BeforeEach
        void setup(DockerRabbitMQ rabbitMQ) throws IOException, TimeoutException {
            connectionFactory = rabbitMQ.connectionFactory();
            connectionFactory.setNetworkRecoveryInterval(1000);
            connection = connectionFactory.newConnection();
            channel = connection.createChannel();
        }

        @AfterEach
        void tearDown(DockerRabbitMQ rabbitMQ) throws Exception {
            closeQuietly(connection, channel);
            rabbitMQ.reset();
        }

        @Test
        void publishedEventWithoutSubscriberShouldNotBeLost() throws Exception {
            String queueName = createQueue(channel);
            publishAMessage(channel);
            awaitAtMostOneMinute.until(() -> messageReceived(channel, queueName));
        }

        @Test
        void getQueueLengthShouldReturnEmptyWhenEmptyQueue() throws Exception {
            String queueName = createQueue(channel);

            awaitAtMostOneMinute.until(() -> rabbitMQExtension.managementAPI()
                .queueDetails("/", queueName)
                .getQueueLength() == 0);
        }

        @Test
        void getQueueLengthShouldReturnExactlyNumberOfMessagesInQueue() throws Exception {
            String queueName = createQueue(channel);
            publishAMessage(channel);
            publishAMessage(channel);

            awaitAtMostOneMinute.until(() -> rabbitMQExtension.managementAPI()
                .queueDetails("/", queueName)
                .getQueueLength() == 2);
        }

        @Test
        void demonstrateDurability(DockerRabbitMQ rabbitMQ) throws Exception {
            String queueName = createQueue(channel);
            publishAMessage(channel);

            //wait for message to be effectively published
            Thread.sleep(200);
            rabbitMQ.restart();
            awaitAtMostOneMinute.until(() -> containerIsRestarted(rabbitMQ));

            Thread.sleep(connectionFactory.getNetworkRecoveryInterval());
            assertThat(channel.basicGet(queueName, !AUTO_ACK)).isNotNull();
        }

        private Boolean containerIsRestarted(DockerRabbitMQ rabbitMQ) {
            try {
                rabbitMQ.connectionFactory().newConnection();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private String createQueue(Channel channel) throws IOException {
            channel.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);
            String queueName = UUID.randomUUID().toString();
            channel.queueDeclare(queueName, DURABLE, !EXCLUSIVE, AUTO_DELETE, NO_QUEUE_DECLARE_ARGUMENTS).getQueue();
            channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
            return queueName;
        }

        private void publishAMessage(Channel channel) throws IOException {
            AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
                .priority(PERSISTENT_TEXT_PLAIN.getPriority())
                .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
                .build();

            channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, basicProperties, asBytes("Hello, world!"));
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
        private Channel channel1;
        private Channel channel2;
        private Channel channel3;
        private Channel channel4;

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
            channel1 = connection1.createChannel();
            channel2 = connection2.createChannel();
            channel3 = connection3.createChannel();
            channel4 = connection4.createChannel();
        }

        @AfterEach
        void tearDown() {
            closeQuietly(
                channel1, channel2, channel3, channel4,
                connection1, connection2, connection3, connection4);
        }

        @Nested
        class BroadCast {

            // In the following case, each consumer will receive the messages produced by the
            // producer
            // To do so, each consumer will bind it's queue to the producer exchange.
            @Test
            void rabbitMQShouldSupportTheBroadcastCase() throws Exception {
                // Declare a single exchange and three queues attached to it.
                channel1.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);

                String queue2 = channel2.queueDeclare().getQueue();
                channel2.queueBind(queue2, EXCHANGE_NAME, ROUTING_KEY);
                String queue3 = channel3.queueDeclare().getQueue();
                channel3.queueBind(queue3, EXCHANGE_NAME, ROUTING_KEY);
                String queue4 = channel4.queueDeclare().getQueue();
                channel4.queueBind(queue4, EXCHANGE_NAME, ROUTING_KEY);

                InMemoryConsumer consumer2 = new InMemoryConsumer(channel2);
                InMemoryConsumer consumer3 = new InMemoryConsumer(channel3);
                InMemoryConsumer consumer4 = new InMemoryConsumer(channel4);
                channel2.basicConsume(queue2, consumer2);
                channel3.basicConsume(queue3, consumer3);
                channel4.basicConsume(queue4, consumer4);

                // the publisher will produce 10 messages
                IntStream.range(0, 10)
                    .mapToObj(String::valueOf)
                    .map(RabbitMQTest.this::asBytes)
                    .forEach(Throwing.<byte[]>consumer(
                        bytes -> channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)).sneakyThrow());

                awaitAtMostOneMinute.until(
                    () -> countReceivedMessages(consumer2, consumer3, consumer4) == 30);

                Integer[] expectedResult = IntStream.range(0, 10).boxed().toArray(Integer[]::new);
                // Check every subscriber have received all the messages.
                assertThat(consumer2.getConsumedMessages()).containsOnly(expectedResult);
                assertThat(consumer3.getConsumedMessages()).containsOnly(expectedResult);
                assertThat(consumer4.getConsumedMessages()).containsOnly(expectedResult);
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
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, AUTO_DELETE, ImmutableMap.of());
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                // Publisher will produce 100 messages
                IntStream.range(0, nbMessages)
                    .mapToObj(String::valueOf)
                    .map(RabbitMQTest.this::asBytes)
                    .forEach(Throwing.<byte[]>consumer(
                        bytes -> channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)).sneakyThrow());

                InMemoryConsumer consumer2 = new InMemoryConsumer(channel2);
                InMemoryConsumer consumer3 = new InMemoryConsumer(channel3);
                InMemoryConsumer consumer4 = new InMemoryConsumer(channel4);
                channel2.basicConsume(WORK_QUEUE, consumer2);
                channel3.basicConsume(WORK_QUEUE, consumer3);
                channel4.basicConsume(WORK_QUEUE, consumer4);

                awaitAtMostOneMinute.until(
                    () -> countReceivedMessages(consumer2, consumer3, consumer4) == nbMessages);

                Integer[] expectedResult = IntStream.range(0, nbMessages).boxed().toArray(Integer[]::new);

                assertThat(
                    Iterables.concat(
                        consumer2.getConsumedMessages(),
                        consumer3.getConsumedMessages(),
                        consumer4.getConsumedMessages()))
                    .containsOnly(expectedResult);
            }

            @Test
            void rabbitMQShouldRejectSecondConsumerInExclusiveWorkQueueCase() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of());
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                IntStream.range(0, 10)
                        .mapToObj(String::valueOf)
                        .map(RabbitMQTest.this::asBytes)
                        .forEach(Throwing.<byte[]>consumer(
                                bytes -> channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)).sneakyThrow());

                ConcurrentLinkedQueue<Integer> receivedMessages = new ConcurrentLinkedQueue<>();
                String dyingConsumerTag = "dyingConsumer";
                ImmutableMap<String, Object> arguments = ImmutableMap.of();
                channel2.basicConsume(WORK_QUEUE, AUTO_ACK, dyingConsumerTag, !NO_LOCAL, EXCLUSIVE, arguments,
                        (consumerTag, message) -> {
                            try {
                                TimeUnit.SECONDS.sleep(1);
                            } catch (InterruptedException e) {
                                //do nothing
                            }
                        },
                        (consumerTag -> { }));
                assertThatThrownBy(() ->
                        channel3.basicConsume(WORK_QUEUE, AUTO_ACK, "fallbackConsumer", !NO_LOCAL, EXCLUSIVE, arguments,
                                (consumerTag, message) -> { },
                                consumerTag -> { }))
                    .isInstanceOf(IOException.class)
                    .hasStackTraceContaining("ACCESS_REFUSED");
            }

            @Test
            void rabbitMQShouldSupportTheExclusiveWorkQueueCase() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of());
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                IntStream.range(0, 10)
                        .mapToObj(String::valueOf)
                        .map(RabbitMQTest.this::asBytes)
                        .forEach(Throwing.<byte[]>consumer(
                                bytes -> channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)).sneakyThrow());

                String dyingConsumerTag = "dyingConsumer";
                ImmutableMap<String, Object> arguments = ImmutableMap.of();
                ConcurrentLinkedQueue<Integer> receivedMessages = new ConcurrentLinkedQueue<>();
                CancelCallback doNothingOnCancel = consumerTag -> { };
                DeliverCallback ackFirstMessageOnly = (consumerTag, message) -> {
                    if (receivedMessages.size() == 0) {
                        receivedMessages.add(Integer.valueOf(new String(message.getBody(), StandardCharsets.UTF_8)));
                        channel2.basicAck(message.getEnvelope().getDeliveryTag(), !MULTIPLE);
                    } else {
                        channel2.basicNack(message.getEnvelope().getDeliveryTag(), !MULTIPLE, REQUEUE);
                    }
                };
                channel2.basicConsume(WORK_QUEUE, !AUTO_ACK, dyingConsumerTag, !NO_LOCAL, EXCLUSIVE, arguments, ackFirstMessageOnly, doNothingOnCancel);

                awaitAtMostOneMinute.until(() -> receivedMessages.size() == 1);

                channel2.basicCancel(dyingConsumerTag);

                InMemoryConsumer fallbackConsumer = new InMemoryConsumer(channel3);
                channel3.basicConsume(WORK_QUEUE, AUTO_ACK, "fallbackConsumer", !NO_LOCAL, EXCLUSIVE, arguments, fallbackConsumer);

                awaitAtMostOneMinute.until(() -> countReceivedMessages(fallbackConsumer) >= 1);

                assertThat(receivedMessages).containsExactly(0);
                assertThat(fallbackConsumer.getConsumedMessages()).contains(1, 2).doesNotContain(0);
            }

            @Test
            void rabbitMQShouldDeliverMessageToSingleActiveConsumer() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, QueueArguments.builder()
                    .put("x-single-active-consumer", true)
                    .build());
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                IntStream.range(0, 10)
                    .mapToObj(String::valueOf)
                    .map(RabbitMQTest.this::asBytes)
                    .forEach(Throwing.<byte[]>consumer(
                        bytes -> channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)).sneakyThrow());

                channel2.basicQos(1);
                channel3.basicQos(1);

                AtomicInteger firstRegisteredConsumerMessageCount = new AtomicInteger(0);
                AtomicInteger secondRegisteredConsumerMessageCount = new AtomicInteger(0);

                String firstRegisteredConsumer = "firstRegisteredConsumer";
                ImmutableMap<String, Object> arguments = ImmutableMap.of();
                channel2.basicConsume(WORK_QUEUE, !AUTO_ACK, firstRegisteredConsumer, !NO_LOCAL, !EXCLUSIVE, arguments,
                    (consumerTag, message) -> incrementCountForConsumerAndAckMessage(firstRegisteredConsumerMessageCount, message, channel2),
                    (consumerTag -> {
                    }));
                channel3.basicConsume(WORK_QUEUE, !AUTO_ACK, "starvingConsumer", !NO_LOCAL, !EXCLUSIVE, arguments,
                    (consumerTag, message) -> incrementCountForConsumerAndAckMessage(secondRegisteredConsumerMessageCount, message, channel3),
                    consumerTag -> { });

                awaitAtMostOneMinute.until(() -> (firstRegisteredConsumerMessageCount.get() + secondRegisteredConsumerMessageCount.get()) == 10);

                assertThat(firstRegisteredConsumerMessageCount.get()).isEqualTo(10);
                assertThat(secondRegisteredConsumerMessageCount.get()).isEqualTo(0);
            }

            private void incrementCountForConsumerAndAckMessage(AtomicInteger firstRegisteredConsumerMessageCount, Delivery message, Channel channel2) throws IOException {
                try {
                    firstRegisteredConsumerMessageCount.incrementAndGet();
                    TimeUnit.SECONDS.sleep(1);
                    channel2.basicAck(message.getEnvelope().getDeliveryTag(), false);
                } catch (InterruptedException e) {
                    //do nothing
                }
            }

            @Test
            void rabbitMQShouldProvideSingleActiveConsumerName() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, Constants.WITH_SINGLE_ACTIVE_CONSUMER);
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, "foo".getBytes(StandardCharsets.UTF_8));

                AtomicInteger deliveredMessagesCount = new AtomicInteger(0);

                String firstRegisteredConsumer = "firstRegisteredConsumer";
                ImmutableMap<String, Object> arguments = ImmutableMap.of();
                channel2.basicConsume(WORK_QUEUE, AUTO_ACK, firstRegisteredConsumer, !NO_LOCAL, !EXCLUSIVE, arguments,
                    (consumerTag, message) -> deliveredMessagesCount.incrementAndGet(),
                    (consumerTag -> { }));
                channel3.basicConsume(WORK_QUEUE, AUTO_ACK, "starvingConsumer", !NO_LOCAL, !EXCLUSIVE, arguments,
                    (consumerTag, message) -> deliveredMessagesCount.incrementAndGet(),
                    consumerTag -> { });

                awaitAtMostOneMinute.until(() -> deliveredMessagesCount.get() > 0);
                awaitAtMostOneMinute.until(() -> rabbitMQExtension.managementAPI()
                    .queueDetails("/", WORK_QUEUE)
                    .consumerDetails.isEmpty() == false);

                List<String> currentConsumerName = rabbitMQExtension.managementAPI()
                    .queueDetails("/", WORK_QUEUE)
                    .consumerDetails
                    .stream()
                    .filter(consumer -> consumer.status == RabbitMQManagementAPI.ActivityStatus.SingleActive)
                    .map(RabbitMQManagementAPI.ConsumerDetails::getTag)
                    .collect(Collectors.toList());

                assertThat(currentConsumerName)
                    .hasSize(1)
                    .first()
                    .isEqualTo(firstRegisteredConsumer);
            }

            @Test
            void bindingSourceShouldMatchBeanContract() {
                EqualsVerifier.forClass(RabbitMQManagementAPI.BindingSource.class)
                    .verify();
            }

            @Test
            void listBindingsShouldReturnEmptyWhenNone() throws Exception {
                assertThat(rabbitMQExtension.managementAPI()
                        .listBindings("/", EXCHANGE_NAME)
                        .stream()
                        .map(RabbitMQManagementAPI.BindingSource::getDestination))
                    .isEmpty();
            }

            @Test
            void listBindingsShouldAllowRetrievingDestination() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, Constants.WITH_SINGLE_ACTIVE_CONSUMER);
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                assertThat(rabbitMQExtension.managementAPI()
                        .listBindings("/", EXCHANGE_NAME)
                        .stream()
                        .map(RabbitMQManagementAPI.BindingSource::getDestination))
                    .containsExactly(WORK_QUEUE);
            }

            @Test
            void listBindingsShouldAllowRetrievingDestinations() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, Constants.WITH_SINGLE_ACTIVE_CONSUMER);
                channel1.queueDeclare(WORK_QUEUE_2, DURABLE, !EXCLUSIVE, !AUTO_DELETE, Constants.WITH_SINGLE_ACTIVE_CONSUMER);
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);
                channel1.queueBind(WORK_QUEUE_2, EXCHANGE_NAME, ROUTING_KEY);

                assertThat(rabbitMQExtension.managementAPI()
                        .listBindings("/", EXCHANGE_NAME)
                        .stream()
                        .map(RabbitMQManagementAPI.BindingSource::getDestination))
                    .containsExactly(WORK_QUEUE, WORK_QUEUE_2);
            }

            @Test
            void rabbitMQShouldDeliverMessageToFallbackSingleActiveConsumer() throws Exception {
                channel1.exchangeDeclare(EXCHANGE_NAME, "direct", DURABLE);
                channel1.queueDeclare(WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, Constants.WITH_SINGLE_ACTIVE_CONSUMER);
                channel1.queueBind(WORK_QUEUE, EXCHANGE_NAME, ROUTING_KEY);

                IntStream.range(0, 10)
                    .mapToObj(String::valueOf)
                    .map(RabbitMQTest.this::asBytes)
                    .forEach(Throwing.<byte[]>consumer(
                        bytes -> channel1.basicPublish(EXCHANGE_NAME, ROUTING_KEY, NO_PROPERTIES, bytes)).sneakyThrow());

                AtomicInteger firstRegisteredConsumerMessageCount = new AtomicInteger(0);
                AtomicInteger secondRegisteredConsumerMessageCount = new AtomicInteger(0);

                String firstRegisteredConsumer = "firstRegisteredConsumer";
                ImmutableMap<String, Object> arguments = ImmutableMap.of();
                channel2.basicConsume(WORK_QUEUE, !AUTO_ACK, firstRegisteredConsumer, !NO_LOCAL, !EXCLUSIVE, arguments,
                    (consumerTag, message) -> {
                        try {
                            if (firstRegisteredConsumerMessageCount.get() < 5) {
                                channel2.basicAck(message.getEnvelope().getDeliveryTag(), !MULTIPLE);
                                firstRegisteredConsumerMessageCount.incrementAndGet();
                            } else {
                                channel2.basicNack(message.getEnvelope().getDeliveryTag(), !MULTIPLE, REQUEUE);
                            }
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            //do nothing
                        }
                    },
                    (consumerTag -> { }));
                channel3.basicConsume(WORK_QUEUE, AUTO_ACK, "fallbackConsumer", !NO_LOCAL, !EXCLUSIVE, arguments,
                    (consumerTag, message) -> {
                        secondRegisteredConsumerMessageCount.incrementAndGet();
                    },
                    consumerTag -> { });

                awaitAtMostOneMinute.until(() -> firstRegisteredConsumerMessageCount.get() == 5);

                channel2.basicCancel(firstRegisteredConsumer);

                awaitAtMostOneMinute.until(() -> (firstRegisteredConsumerMessageCount.get() + secondRegisteredConsumerMessageCount.get()) == 10);

                assertThat(firstRegisteredConsumerMessageCount.get()).isEqualTo(5);
                assertThat(secondRegisteredConsumerMessageCount.get()).isEqualTo(5);
            }
        }

        @Nested
        class Routing {
            @Test
            void rabbitMQShouldSupportRouting() throws Exception {
                String conversation1 = "c1";
                String conversation2 = "c2";
                String conversation3 = "c3";
                String conversation4 = "c4";

                // Declare the exchange and a single queue attached to it.
                channel1.exchangeDeclare(EXCHANGE_NAME, DIRECT_EXCHANGE, DURABLE);

                String queue1 = channel1.queueDeclare().getQueue();
                // 1 will follow conversation 1 and 2
                channel1.queueBind(queue1, EXCHANGE_NAME, conversation1);
                channel1.queueBind(queue1, EXCHANGE_NAME, conversation2);

                String queue2 = channel2.queueDeclare().getQueue();
                // 2 will follow conversation 2 and 3
                channel2.queueBind(queue2, EXCHANGE_NAME, conversation2);
                channel2.queueBind(queue2, EXCHANGE_NAME, conversation3);

                String queue3 = channel3.queueDeclare().getQueue();
                // 3 will follow conversation 3 and 4
                channel3.queueBind(queue3, EXCHANGE_NAME, conversation3);
                channel3.queueBind(queue3, EXCHANGE_NAME, conversation4);

                String queue4 = channel4.queueDeclare().getQueue();
                // 4 will follow conversation 1 and 4
                channel4.queueBind(queue4, EXCHANGE_NAME, conversation1);
                channel4.queueBind(queue4, EXCHANGE_NAME, conversation4);

                channel1.basicPublish(EXCHANGE_NAME, conversation1, NO_PROPERTIES, asBytes("1"));
                channel2.basicPublish(EXCHANGE_NAME, conversation2, NO_PROPERTIES, asBytes("2"));
                channel3.basicPublish(EXCHANGE_NAME, conversation3, NO_PROPERTIES, asBytes("3"));
                channel4.basicPublish(EXCHANGE_NAME, conversation4, NO_PROPERTIES, asBytes("4"));

                InMemoryConsumer consumer1 = new InMemoryConsumer(channel1);
                InMemoryConsumer consumer2 = new InMemoryConsumer(channel2);
                InMemoryConsumer consumer3 = new InMemoryConsumer(channel3);
                InMemoryConsumer consumer4 = new InMemoryConsumer(channel4);
                channel1.basicConsume(queue1, consumer1);
                channel2.basicConsume(queue2, consumer2);
                channel3.basicConsume(queue3, consumer3);
                channel4.basicConsume(queue4, consumer4);

                awaitAtMostOneMinute.until(() -> countReceivedMessages(consumer1, consumer2, consumer3, consumer4) == 8);

                assertThat(consumer1.getConsumedMessages()).containsOnly(1, 2);
                assertThat(consumer2.getConsumedMessages()).containsOnly(2, 3);
                assertThat(consumer3.getConsumedMessages()).containsOnly(3, 4);
                assertThat(consumer4.getConsumedMessages()).containsOnly(1, 4);
            }
        }

        private long countReceivedMessages(InMemoryConsumer... consumers) {
            return Arrays.stream(consumers)
                .map(InMemoryConsumer::getConsumedMessages)
                .mapToLong(Queue::size)
                .sum();
        }

    }

    @Nested
    class ConcurrencyTest {
        private static final String QUEUE_NAME_1 = "TEST1";
        private static final String EXCHANGE_NAME_1 = "EXCHANGE1";

        @BeforeEach
        void setup() {
            Sender sender = rabbitMQExtension.getSender();

            Flux.concat(
                    sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME_1)
                        .durable(true)
                        .type("direct")),
                    sender.declareQueue(QueueSpecification.queue(QUEUE_NAME_1)
                        .durable(DURABLE)
                        .exclusive(!EXCLUSIVE)
                        .autoDelete(!AUTO_DELETE)),
                    sender.bind(BindingSpecification.binding()
                        .exchange(EXCHANGE_NAME_1)
                        .queue(QUEUE_NAME_1)
                        .routingKey(EMPTY_ROUTING_KEY)))
                .then()
                .block();

            IntStream.rangeClosed(1, 5)
                .forEach(i -> sender.send(Mono.just(new OutboundMessage(EXCHANGE_NAME_1, "", String.format("Message + %s", UUID.randomUUID()).getBytes(StandardCharsets.UTF_8))))
                    .block());
        }

        @Test
        void consumingShouldSuccessWhenAckConcurrent() throws Exception {
            ReceiverProvider receiverProvider = rabbitMQExtension.getReceiverProvider();
            CountDownLatch countDownLatch = new CountDownLatch(5);

            Flux.using(receiverProvider::createReceiver,
                    receiver -> receiver.consumeManualAck(QUEUE_NAME_1, new ConsumeOptions()),
                    Receiver::close)
                .filter(getResponse -> getResponse.getBody() != null)
                .concatMap(acknowledgableDelivery -> Mono.fromCallable(() -> {
                    acknowledgableDelivery.ack(true);
                    countDownLatch.countDown();
                    return acknowledgableDelivery;
                }).subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribe();

            assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @Disabled("Now, it fail, Because using Flux.take and concatMap")
        // See https://github.com/reactor/reactor-rabbitmq/issues/176
        void consumingShouldSuccessWhenAckConcurrentWithFluxTake() throws Exception {
            ReceiverProvider receiverProvider = rabbitMQExtension.getReceiverProvider();
            int counter = 5;
            CountDownLatch countDownLatch = new CountDownLatch(counter);

            Flux.using(receiverProvider::createReceiver,
                    receiver -> receiver.consumeManualAck(QUEUE_NAME_1, new ConsumeOptions()),
                    Receiver::close)
                .filter(getResponse -> getResponse.getBody() != null)
                .take(counter)
                .concatMap(acknowledgableDelivery -> Mono.fromCallable(() -> {
                    acknowledgableDelivery.ack(true);
                    countDownLatch.countDown();
                    System.out.println(Thread.currentThread().getName() + ": " + countDownLatch.getCount());
                    return acknowledgableDelivery;
                }).subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribe();

            assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();
        }


        @Test
        @Disabled("Now, sometimes pass, sometimes fail. Because using Flux.take and flatMap, It can be re-produce it by try 'Repeat until failure' of Intellij")
        // See https://github.com/reactor/reactor-rabbitmq/issues/176
        void consumingShouldSuccessWhenAckConcurrentWithFluxTakeAndFlatMap() throws Exception {
            ReceiverProvider receiverProvider = rabbitMQExtension.getReceiverProvider();
            int counter = 5;
            CountDownLatch countDownLatch = new CountDownLatch(counter);

            Flux.using(receiverProvider::createReceiver,
                    receiver -> receiver.consumeManualAck(QUEUE_NAME_1, new ConsumeOptions()),
                    Receiver::close)
                .filter(getResponse -> getResponse.getBody() != null)
                .take(counter)
                .flatMap(acknowledgableDelivery -> Mono.fromCallable(() -> {
                    acknowledgableDelivery.ack(true);
                    countDownLatch.countDown();
                    System.out.println(Thread.currentThread().getName() + ": " + countDownLatch.getCount());
                    return acknowledgableDelivery;
                }).subscribeOn(Schedulers.fromExecutor(EXECUTOR)))
                .subscribe();

            assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue();
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
