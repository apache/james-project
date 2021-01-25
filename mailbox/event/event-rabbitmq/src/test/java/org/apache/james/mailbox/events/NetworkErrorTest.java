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

package org.apache.james.mailbox.events;

import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.apache.james.mailbox.events.EventBusTestFixture.newListener;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQFixture;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
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
    void setUp() {
        MemoryEventDeadLetters memoryEventDeadLetters = new MemoryEventDeadLetters();

        TestId.Factory mailboxIdFactory = new TestId.Factory();
        EventSerializer eventSerializer = new EventSerializer(mailboxIdFactory, new TestMessageId.Factory(), new DefaultUserQuotaRootResolver.DefaultQuotaRootDeserializer());
        RoutingKeyConverter routingKeyConverter = RoutingKeyConverter.forFactories(new MailboxIdRegistrationKey.Factory(mailboxIdFactory));

        eventBus = new RabbitMQEventBus(rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(),
            eventSerializer, RETRY_BACKOFF_CONFIGURATION, routingKeyConverter,
            memoryEventDeadLetters, new RecordingMetricFactory(), rabbitMQExtension.getRabbitChannelPool(),
            EventBusId.random());

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
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Retries exhausted");

        rabbitMQExtension.getRabbitMQ().unpause();

        eventBus.dispatch(EVENT, NO_KEYS).block();
        RabbitMQFixture.awaitAtMostThirtySeconds
            .untilAsserted(() -> verify(listener).event(EVENT));
    }

}
