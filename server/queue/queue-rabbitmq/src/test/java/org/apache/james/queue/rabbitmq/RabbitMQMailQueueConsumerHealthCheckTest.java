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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_ACK;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.queue.api.MailQueueFactory.SPOOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.Channel;

import reactor.core.publisher.Mono;

class RabbitMQMailQueueConsumerHealthCheckTest {
    private static final String SPOOL_WORK_QUEUE = MailQueueName.fromString(SPOOL.asString()).toWorkQueueName().asString();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private final AtomicInteger reconnections = new AtomicInteger();
    private Channel channel;
    private RabbitMQMailQueueConsumerHealthCheck testee;

    @BeforeEach
    void setUp() throws Exception {
        channel = rabbitMQExtension.getConnectionPool().getResilientConnection().block().createChannel();
        channel.queueDeclare(SPOOL_WORK_QUEUE, DURABLE, !EXCLUSIVE, !AUTO_DELETE, ImmutableMap.of());

        RabbitMQMailQueueFactory queueFactory = mock(RabbitMQMailQueueFactory.class);
        when(queueFactory.listCreatedMailQueues()).thenReturn(ImmutableSet.of(SPOOL));
        SimpleConnectionPool.ReconnectionHandler countingHandler = connection -> Mono.fromRunnable(reconnections::incrementAndGet);

        testee = new RabbitMQMailQueueConsumerHealthCheck(queueFactory, ImmutableSet.of(countingHandler), rabbitMQExtension.getConnectionPool());
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.close();
    }

    @Test
    void checkShouldRunReconnectionHandlersWhenAWorkQueueHasNoConsumer() {
        Result result = testee.check().block();

        assertThat(result.isDegraded()).isTrue();
        assertThat(reconnections).hasValue(1);
    }

    @Test
    void checkShouldNotRunReconnectionHandlersWhenWorkQueuesHaveConsumers() throws Exception {
        channel.basicConsume(SPOOL_WORK_QUEUE, AUTO_ACK, (consumerTag, delivery) -> { }, consumerTag -> { });

        Result result = testee.check().block();

        assertThat(result.isHealthy()).isTrue();
        assertThat(reconnections).hasValue(0);
    }
}
