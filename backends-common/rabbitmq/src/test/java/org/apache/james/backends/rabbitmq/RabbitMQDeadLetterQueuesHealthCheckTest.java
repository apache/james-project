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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.apache.james.core.healthcheck.Result;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.Channel;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;

class RabbitMQDeadLetterQueuesHealthCheckTest {
    private static final String DEFAULT_EXCHANGE = "";
    private static final String SECOND_VHOST = "second";
    private static final String FIRST_DEAD_LETTER_QUEUE = "first-dead-letter";
    private static final String SECOND_DEAD_LETTER_QUEUE = "second-dead-letter";

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private RabbitMQConfiguration configuration;
    private RabbitMQConfiguration secondVhostConfiguration;
    private SimpleConnectionPool secondVhostConnectionPool;

    @BeforeEach
    void setUp() throws Exception {
        DockerRabbitMQ rabbitMQ = rabbitMQExtension.getRabbitMQ();
        rabbitMQ.container().execInContainer("rabbitmqctl", "add_vhost", SECOND_VHOST);
        rabbitMQ.container().execInContainer("rabbitmqctl", "set_permissions", "-p", SECOND_VHOST, rabbitMQ.getUsername(), ".*", ".*", ".*");
        configuration = rabbitMQ.getConfiguration();
        secondVhostConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(URI.create(rabbitMQ.amqpUri() + "/" + SECOND_VHOST))
            .managementUri(rabbitMQ.managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .vhost(Optional.of(SECOND_VHOST))
            .build();
        secondVhostConnectionPool = new SimpleConnectionPool(new RabbitMQConnectionFactory(secondVhostConfiguration),
            SimpleConnectionPool.Configuration.DEFAULT);
    }

    @AfterEach
    void tearDown() {
        secondVhostConnectionPool.close();
    }

    @Test
    void checkShouldReturnHealthyWhenNoDeadLetterQueueIsMonitored() {
        Result result = new RabbitMQDeadLetterQueuesHealthCheck(ImmutableSet.of()).check().block();

        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnHealthyWhenAllDeadLetterQueuesAreEmpty() throws Exception {
        declareQueues();

        Result result = twoQueuesHealthCheck().check().block();

        assertThat(result.isHealthy()).isTrue();
        assertThat(result.getComponentName()).isEqualTo(RabbitMQDeadLetterQueuesHealthCheck.COMPONENT_NAME);
    }

    @Test
    void checkShouldReturnDegradedNamingOnlyTheFirstDeadLetterQueueWhenItIsTheOnlyNonEmptyOne() throws Exception {
        declareQueues();
        publish(FIRST_DEAD_LETTER_QUEUE);

        assertDegradedNamingOnly(FIRST_DEAD_LETTER_QUEUE, SECOND_DEAD_LETTER_QUEUE);
    }

    @Test
    void checkShouldReturnDegradedNamingOnlyTheSecondDeadLetterQueueWhenItIsTheOnlyNonEmptyOne() throws Exception {
        declareQueues();
        publishInSecondVhost(SECOND_DEAD_LETTER_QUEUE);

        assertDegradedNamingOnly(SECOND_DEAD_LETTER_QUEUE, FIRST_DEAD_LETTER_QUEUE);
    }

    @Test
    void checkShouldQueryTheDeadLetterQueuesConcurrently() throws Exception {
        try (UnresponsiveServer firstServer = new UnresponsiveServer();
             UnresponsiveServer secondServer = new UnresponsiveServer()) {
            Disposable check = new RabbitMQDeadLetterQueuesHealthCheck(ImmutableSet.of(
                    new MonitoredDeadLetterQueue(firstServer.configuration(), FIRST_DEAD_LETTER_QUEUE),
                    new MonitoredDeadLetterQueue(secondServer.configuration(), SECOND_DEAD_LETTER_QUEUE)))
                .check()
                .subscribe();

            try {
                // Well below the 60 seconds read timeout of the first, unanswered, request
                Awaitility.await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        assertThat(firstServer.acceptedConnections()).isPositive();
                        assertThat(secondServer.acceptedConnections()).isPositive();
                    });
            } finally {
                check.dispose();
            }
        }
    }

    @Test
    void checkShouldReturnUnhealthyAndStillReportOtherQueuesWhenADeadLetterQueueDoesNotExist() throws Exception {
        declareQueueInSecondVhost(SECOND_DEAD_LETTER_QUEUE);
        publishInSecondVhost(SECOND_DEAD_LETTER_QUEUE);

        awaitAtMostOneMinute.untilAsserted(() -> {
            Result result = twoQueuesHealthCheck().check().block();

            assertThat(result.isUnHealthy()).isTrue();
            assertThat(result.getCause()).hasValueSatisfying(cause -> assertThat(cause)
                .contains("Error checking RabbitMQ dead letter queue " + FIRST_DEAD_LETTER_QUEUE)
                .contains("RabbitMQ dead letter queue " + SECOND_DEAD_LETTER_QUEUE));
        });
    }

    private RabbitMQDeadLetterQueuesHealthCheck twoQueuesHealthCheck() {
        return new RabbitMQDeadLetterQueuesHealthCheck(ImmutableSet.of(
            new MonitoredDeadLetterQueue(configuration, FIRST_DEAD_LETTER_QUEUE),
            new MonitoredDeadLetterQueue(secondVhostConfiguration, SECOND_DEAD_LETTER_QUEUE)));
    }

    private void assertDegradedNamingOnly(String nonEmptyQueue, String emptyQueue) {
        awaitAtMostOneMinute.untilAsserted(() -> {
            Result result = twoQueuesHealthCheck().check().block();

            assertThat(result.isDegraded()).isTrue();
            assertThat(result.getCause()).hasValueSatisfying(cause -> assertThat(cause)
                .contains(nonEmptyQueue)
                .doesNotContain(emptyQueue));
        });
    }

    /**
     * The second dead letter queue only exists in the second vhost: checking it with the configuration of the first
     * one fails.
     */
    private void declareQueues() throws Exception {
        declareQueue(FIRST_DEAD_LETTER_QUEUE);
        declareQueueInSecondVhost(SECOND_DEAD_LETTER_QUEUE);
    }

    private void declareQueue(String queue) {
        rabbitMQExtension.getSender()
            .declareQueue(QueueSpecification.queue(queue).durable(DURABLE))
            .block();
    }

    private void declareQueueInSecondVhost(String queue) throws Exception {
        try (Channel channel = secondVhostConnectionPool.getResilientConnection().block().createChannel()) {
            channel.queueDeclare(queue, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of());
        }
    }

    private void publishInSecondVhost(String queue) throws Exception {
        try (Channel channel = secondVhostConnectionPool.getResilientConnection().block().createChannel()) {
            channel.basicPublish(DEFAULT_EXCHANGE, queue, null, "message".getBytes(UTF_8));
        }
    }

    private void publish(String queue) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(DEFAULT_EXCHANGE, queue, "message".getBytes(UTF_8))))
            .block();
    }
}
