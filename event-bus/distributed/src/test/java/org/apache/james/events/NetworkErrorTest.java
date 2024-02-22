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

import static org.apache.james.events.EventBusTestFixture.EVENT;
import static org.apache.james.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.apache.james.events.EventBusTestFixture.newListener;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.util.NoSuchElementException;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQFixture;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NetworkErrorTest {
    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private RabbitMQEventBus eventBus;

    @BeforeEach
    void setUp() throws Exception {
        MemoryEventDeadLetters memoryEventDeadLetters = new MemoryEventDeadLetters();


        EventSerializer eventSerializer = new TestEventSerializer();
        RoutingKeyConverter routingKeyConverter = RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory());

        eventBus = new RabbitMQEventBus(new NamingStrategy(new EventBusName("test")), rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(),
            eventSerializer, RETRY_BACKOFF_CONFIGURATION, routingKeyConverter,
            memoryEventDeadLetters, new RecordingMetricFactory(), rabbitMQExtension.getRabbitChannelPool(),
            EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            null); // todo use redis client factory

        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    @Test
    void dispatchShouldWorkAfterNetworkIssuesForOldRegistration() {
        EventListener listener = newListener();
        eventBus.register(listener, GROUP_A);

        rabbitMQExtension.getRabbitMQ().pause();

        assertThatThrownBy(() -> eventBus.dispatch(EVENT, NO_KEYS).block())
            .getCause()
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Timeout waiting for idle object");

        rabbitMQExtension.getRabbitMQ().unpause();

        eventBus.dispatch(EVENT, NO_KEYS).block();
        RabbitMQFixture.awaitAtMostThirtySeconds
            .untilAsserted(() -> verify(listener).event(EVENT));
    }

}
