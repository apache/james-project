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

package org.apache.james.events;

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.apache.james.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.apache.james.events.EventBusTestFixture.newListener;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.rabbitmq.RabbitMQConsumersHealthCheck;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

class RabbitMQEventBusConsumersTest {
    private static final NamingStrategy NAMING_STRATEGY = new DefaultNamingStrategy(new EventBusName("consumersTest"));

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private RabbitMQEventBus eventBus;
    private RabbitMQEventBusConsumers testee;

    @BeforeEach
    void setUp() throws Exception {
        eventBus = new RabbitMQEventBus(NAMING_STRATEGY, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(),
            new TestEventSerializer(), RoutingKeyConverter.forFactories(new TestRegistrationKeyFactory()), new MemoryEventDeadLetters(),
            new RecordingMetricFactory(), rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(),
            new RabbitMQEventBus.Configurations(rabbitMQExtension.getRabbitMQ().getConfiguration(), RETRY_BACKOFF_CONFIGURATION));
        eventBus.start();
        eventBus.register(newListener(), GROUP_A);

        testee = new RabbitMQEventBusConsumers(eventBus, NAMING_STRATEGY, rabbitMQExtension.getConnectionPool(), GroupRegistrationHandler.GROUP);
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Test
    void queuesShouldBeTheWorkQueuesOfTheRegisteredGroupsAndOfTheGroupRegistrationHandler() {
        assertThat(testee.queues()).containsExactlyInAnyOrder(
            NAMING_STRATEGY.workQueue(GROUP_A).asString(),
            NAMING_STRATEGY.workQueue(GroupRegistrationHandler.GROUP).asString());
    }

    @Test
    void consumersOfAStartedEventBusShouldBeHealthy() {
        RabbitMQConsumersHealthCheck healthCheck = new RabbitMQConsumersHealthCheck(ImmutableSet.of(testee));

        awaitAtMostOneMinute.untilAsserted(() -> assertThat(healthCheck.check().block().isHealthy()).isTrue());
    }
}
