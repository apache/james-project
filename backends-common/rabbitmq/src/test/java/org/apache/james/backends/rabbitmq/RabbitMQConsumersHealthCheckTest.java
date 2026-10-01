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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_ACK;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class RabbitMQConsumersHealthCheckTest {
    private static final String SECOND_VHOST = "second";
    private static final String FIRST_QUEUE = "first-queue";
    private static final String SECOND_QUEUE = "second-queue";

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private final List<String> firstQueues = new CopyOnWriteArrayList<>(List.of(FIRST_QUEUE));
    private final AtomicInteger firstRestarts = new AtomicInteger();
    private final AtomicInteger secondRestarts = new AtomicInteger();
    private final AtomicReference<Connection> firstRestartConnection = new AtomicReference<>();
    private final AtomicReference<Connection> secondRestartConnection = new AtomicReference<>();
    private SimpleConnectionPool secondVhostConnectionPool;
    private Channel firstChannel;
    private Channel secondChannel;
    private MonitoredRabbitMQConsumers first;
    private MonitoredRabbitMQConsumers second;

    /**
     * The second consumers' queue only exists in the second vhost: checking it with the connection pool of the first
     * consumers fails.
     */
    @BeforeEach
    void setUp() throws Exception {
        DockerRabbitMQ rabbitMQ = rabbitMQExtension.getRabbitMQ();
        rabbitMQ.container().execInContainer("rabbitmqctl", "add_vhost", SECOND_VHOST);
        rabbitMQ.container().execInContainer("rabbitmqctl", "set_permissions", "-p", SECOND_VHOST, rabbitMQ.getUsername(), ".*", ".*", ".*");
        secondVhostConnectionPool = new SimpleConnectionPool(new RabbitMQConnectionFactory(RabbitMQConfiguration.builder()
                .amqpUri(URI.create(rabbitMQ.amqpUri() + "/" + SECOND_VHOST))
                .managementUri(rabbitMQ.managementUri())
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .vhost(Optional.of(SECOND_VHOST))
                .build()),
            SimpleConnectionPool.Configuration.DEFAULT);

        firstChannel = rabbitMQExtension.getConnectionPool().getResilientConnection().block().createChannel();
        secondChannel = secondVhostConnectionPool.getResilientConnection().block().createChannel();
        declareQueue(firstChannel, FIRST_QUEUE);
        declareQueue(secondChannel, SECOND_QUEUE);

        first = MonitoredRabbitMQConsumers.of("first consumers", rabbitMQExtension.getConnectionPool(), () -> List.copyOf(firstQueues),
            connection -> Mono.fromRunnable(() -> {
                firstRestarts.incrementAndGet();
                firstRestartConnection.set(connection);
            }));
        second = MonitoredRabbitMQConsumers.of("second consumers", secondVhostConnectionPool, () -> List.of(SECOND_QUEUE),
            connection -> Mono.fromRunnable(() -> {
                secondRestarts.incrementAndGet();
                secondRestartConnection.set(connection);
            }));
    }

    @AfterEach
    void tearDown() throws Exception {
        firstChannel.close();
        secondChannel.close();
        secondVhostConnectionPool.close();
    }

    @Test
    void checkShouldReturnHealthyWhenNoConsumerIsMonitored() {
        Result result = new RabbitMQConsumersHealthCheck(ImmutableSet.of()).check().block();

        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnHealthyWithoutRestartWhenAllQueuesHaveConsumers() throws Exception {
        consume(firstChannel, FIRST_QUEUE);
        consume(secondChannel, SECOND_QUEUE);

        Result result = new RabbitMQConsumersHealthCheck(ImmutableSet.of(first, second)).check().block();

        assertThat(result.isHealthy()).isTrue();
        assertThat(result.getComponentName()).isEqualTo(RabbitMQConsumersHealthCheck.COMPONENT_NAME);
        assertThat(firstRestarts).hasValue(0);
        assertThat(secondRestarts).hasValue(0);
    }

    @Test
    void checkShouldRestartOnlyTheConsumersWhoseQueueHasNoConsumer() throws Exception {
        consume(secondChannel, SECOND_QUEUE);

        Result result = new RabbitMQConsumersHealthCheck(ImmutableSet.of(first, second)).check().block();

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getCause()).contains("No consumers on " + FIRST_QUEUE + " of first consumers");
        assertThat(firstRestarts).hasValue(1);
        assertThat(secondRestarts).hasValue(0);
    }

    @Test
    void checkShouldRestartConsumersWithAConnectionOfTheirOwnPool() {
        new RabbitMQConsumersHealthCheck(ImmutableSet.of(first, second)).check().block();

        assertThat(firstRestartConnection.get()).isSameAs(rabbitMQExtension.getConnectionPool().getResilientConnection().block());
        assertThat(secondRestartConnection.get()).isSameAs(secondVhostConnectionPool.getResilientConnection().block());
    }

    @Test
    void checkShouldStillCheckOtherConsumersWhenARestartFails() {
        MonitoredRabbitMQConsumers failingRestart = MonitoredRabbitMQConsumers.of("failing consumers", rabbitMQExtension.getConnectionPool(),
            () -> List.of(FIRST_QUEUE), connection -> Mono.error(new RuntimeException("Restart failure")));

        Result result = new RabbitMQConsumersHealthCheck(ImmutableSet.of(failingRestart, second)).check().block();

        assertThat(result.isUnHealthy()).isTrue();
        assertThat(result.getCause()).hasValueSatisfying(cause -> assertThat(cause)
            .contains("Error checking the consumers of failing consumers")
            .contains("No consumers on " + SECOND_QUEUE + " of second consumers"));
        assertThat(secondRestarts).hasValue(1);
    }

    @Test
    void checkShouldRestartOtherConsumersWhenARabbitMQServerIsUnresponsive() throws Exception {
        try (UnresponsiveServer unresponsiveServer = new UnresponsiveServer();
             SimpleConnectionPool unresponsivePool = new SimpleConnectionPool(new RabbitMQConnectionFactory(unresponsiveServer.configuration()),
                 SimpleConnectionPool.Configuration.DEFAULT)) {
            MonitoredRabbitMQConsumers unresponsive = MonitoredRabbitMQConsumers.of("unresponsive consumers", unresponsivePool,
                () -> List.of(FIRST_QUEUE), connection -> Mono.empty());

            Disposable check = new RabbitMQConsumersHealthCheck(ImmutableSet.of(unresponsive, second)).check().subscribe();

            try {
                awaitAtMostOneMinute.untilAsserted(() -> {
                    assertThat(unresponsiveServer.acceptedConnections()).isPositive();
                    assertThat(secondRestarts).hasValue(1);
                });
            } finally {
                check.dispose();
            }
        }
    }

    @Test
    void checkShouldNotStartARestartOnANonBlockingThread() {
        List<Boolean> restartsStartedOnNonBlockingThread = new CopyOnWriteArrayList<>();
        AtomicInteger listedQueues = new AtomicInteger();
        CompletableFuture<Void> bothDetectionsDone = new CompletableFuture<>();
        Supplier<List<String>> firstQueue = () -> listQueue(FIRST_QUEUE, listedQueues, bothDetectionsDone);
        Supplier<List<String>> secondQueue = () -> listQueue(SECOND_QUEUE, listedQueues, bothDetectionsDone);
        // Completes on a non-blocking thread once both detections are done: the second restart then starts from there
        Function<Connection, Publisher<Void>> asynchronousRestart = connection -> Mono.fromRunnable(
                () -> restartsStartedOnNonBlockingThread.add(Schedulers.isInNonBlockingThread()))
            .then(Mono.fromFuture(bothDetectionsDone))
            .then(Mono.delay(Duration.ofMillis(200)))
            .then();
        MonitoredRabbitMQConsumers asynchronousFirst = MonitoredRabbitMQConsumers.of("asynchronous first consumers",
            rabbitMQExtension.getConnectionPool(), firstQueue, asynchronousRestart);
        MonitoredRabbitMQConsumers asynchronousSecond = MonitoredRabbitMQConsumers.of("asynchronous second consumers",
            secondVhostConnectionPool, secondQueue, asynchronousRestart);

        new RabbitMQConsumersHealthCheck(ImmutableSet.of(asynchronousFirst, asynchronousSecond)).check().block();

        assertThat(restartsStartedOnNonBlockingThread).containsExactly(false, false);
    }

    @Test
    void checkShouldNotRestartConsumersConcurrently() {
        AtomicInteger runningRestarts = new AtomicInteger();
        AtomicInteger maxRunningRestarts = new AtomicInteger();
        MonitoredRabbitMQConsumers slowFirst = MonitoredRabbitMQConsumers.of("slow first consumers", rabbitMQExtension.getConnectionPool(),
            () -> List.of(FIRST_QUEUE), connection -> slowRestart(runningRestarts, maxRunningRestarts));
        MonitoredRabbitMQConsumers slowSecond = MonitoredRabbitMQConsumers.of("slow second consumers", secondVhostConnectionPool,
            () -> List.of(SECOND_QUEUE), connection -> slowRestart(runningRestarts, maxRunningRestarts));

        Result result = new RabbitMQConsumersHealthCheck(ImmutableSet.of(slowFirst, slowSecond)).check().block();

        assertThat(result.getCause()).hasValueSatisfying(cause -> assertThat(cause)
            .contains("slow first consumers")
            .contains("slow second consumers"));
        assertThat(maxRunningRestarts).hasValue(1);
    }

    @Test
    void checkShouldReturnUnhealthyWhenAQueueDoesNotExist() throws Exception {
        consume(firstChannel, FIRST_QUEUE);
        firstQueues.add("missing-queue");

        Result result = new RabbitMQConsumersHealthCheck(ImmutableSet.of(first)).check().block();

        assertThat(result.isUnHealthy()).isTrue();
    }

    @Test
    void checkShouldMonitorQueuesAddedAfterCreation() throws Exception {
        consume(firstChannel, FIRST_QUEUE);
        consume(secondChannel, SECOND_QUEUE);
        RabbitMQConsumersHealthCheck testee = new RabbitMQConsumersHealthCheck(ImmutableSet.of(first, second));
        String thirdQueue = "third-queue";
        declareQueue(firstChannel, thirdQueue);
        firstQueues.add(thirdQueue);

        Result result = testee.check().block();

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getCause()).contains("No consumers on " + thirdQueue + " of first consumers");
    }

    private List<String> listQueue(String queue, AtomicInteger listedQueues, CompletableFuture<Void> bothDetectionsDone) {
        if (listedQueues.incrementAndGet() == 2) {
            bothDetectionsDone.complete(null);
        }
        return List.of(queue);
    }

    private Mono<Void> slowRestart(AtomicInteger runningRestarts, AtomicInteger maxRunningRestarts) {
        return Mono.fromRunnable(() -> maxRunningRestarts.accumulateAndGet(runningRestarts.incrementAndGet(), Math::max))
            .then(Mono.delay(Duration.ofSeconds(1)))
            .then(Mono.fromRunnable(runningRestarts::decrementAndGet));
    }

    private void declareQueue(Channel channel, String queue) throws Exception {
        channel.queueDeclare(queue, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of());
    }

    private void consume(Channel channel, String queue) throws Exception {
        channel.basicConsume(queue, AUTO_ACK, (consumerTag, delivery) -> { }, consumerTag -> { });
    }
}
