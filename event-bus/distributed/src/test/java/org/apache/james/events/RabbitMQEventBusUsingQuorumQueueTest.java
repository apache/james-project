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

import static org.apache.james.events.EventBusTestFixture.ALL_GROUPS;

import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Sender;

class RabbitMQEventBusUsingQuorumQueueTest implements GroupContract.SingleEventBusGroupContract, GroupContract.MultipleEventBusGroupContract,
    KeyContract.SingleEventBusKeyContract, KeyContract.MultipleEventBusKeyContract,
    ErrorHandlingContract {
    
    static EventBusName TEST_EVENT_BUS = new EventBusName("test-quorum");
    static NamingStrategy TEST_NAMING_STRATEGY = new NamingStrategy(TEST_EVENT_BUS);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private RabbitMQEventBus eventBus;
    private RabbitMQEventBus eventBus2;
    private RabbitMQEventBus eventBus3;
    private RabbitMQEventBus eventBusWithKeyHandlerNotStarted;
    private EventSerializer eventSerializer;
    private RoutingKeyConverter routingKeyConverter;
    private MemoryEventDeadLetters memoryEventDeadLetters;

    @Override
    public EnvironmentSpeedProfile getSpeedProfile() {
        return EnvironmentSpeedProfile.SLOW;
    }

    @BeforeEach
    void setUp() throws Exception {
        memoryEventDeadLetters = new MemoryEventDeadLetters();

        eventSerializer = new TestEventSerializer();
        routingKeyConverter = RoutingKeyConverter.forFactories(new TestRegistrationKeyFactory());

        eventBus = newEventBus();
        eventBus2 = newEventBus();
        eventBus3 = newEventBus();
        eventBusWithKeyHandlerNotStarted = newEventBus();

        eventBus.start();
        eventBus2.start();
        eventBus3.start();
        eventBusWithKeyHandlerNotStarted.startWithoutStartingKeyRegistrationHandler();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            eventBus.stop();
            eventBus2.stop();
            eventBus3.stop();
            eventBusWithKeyHandlerNotStarted.stop();
            Stream.concat(
                    ALL_GROUPS.stream(),
                    Stream.of(GroupRegistrationHandler.GROUP))
                .map(TEST_NAMING_STRATEGY::workQueue)
                .forEach(queueName -> rabbitMQExtension.getSender().delete(QueueSpecification.queue(queueName.asString())).block());
            rabbitMQExtension.getSender()
                .delete(ExchangeSpecification.exchange(TEST_NAMING_STRATEGY.exchange()))
                .block();
            rabbitMQExtension.getSender()
                .delete(TEST_NAMING_STRATEGY.deadLetterQueue())
                .block();
        } catch (Exception ignored) {
            // exception while cleaning up data after a test -> force a RabbitMQ self clean up
            rabbitMQExtension.getRabbitMQ().reset();
        }
    }

    private RabbitMQEventBus newEventBus() throws Exception {
        return newEventBus(TEST_NAMING_STRATEGY, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider());
    }

    private RabbitMQEventBus newEventBus(NamingStrategy namingStrategy, Sender sender, ReceiverProvider receiverProvider) throws Exception {
        return new RabbitMQEventBus(namingStrategy, sender, receiverProvider, eventSerializer,
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, routingKeyConverter,
            memoryEventDeadLetters, new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(), rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration());
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    public EventBus eventBus2() {
        return eventBus2;
    }

    @Override
    public EventDeadLetters deadLetter() {
        return memoryEventDeadLetters;
    }

    @Override
    @Test
    @Disabled("This test is failing by design as the different registration keys are handled by distinct messages")
    public void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {

    }
}
