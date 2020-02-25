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

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.mailbox.events.EventBusConcurrentTestContract.newCountingListener;
import static org.apache.james.mailbox.events.EventBusTestFixture.ALL_GROUPS;
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_1;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusTestFixture.WAIT_CONDITION;
import static org.apache.james.mailbox.events.EventBusTestFixture.newListener;
import static org.apache.james.mailbox.events.GroupRegistration.WorkQueueName.MAILBOX_EVENT_WORK_QUEUE_PREFIX;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT;
import static org.apache.james.mailbox.events.RabbitMQEventBus.MAILBOX_EVENT_EXCHANGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQExtension.DockerRestartPolicy;
import org.apache.james.backends.rabbitmq.RabbitMQFixture;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.events.EventBusTestFixture.GroupA;
import org.apache.james.mailbox.events.EventBusTestFixture.MailboxListenerCountingSuccessfulExecution;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;

import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

class RabbitMQEventBusTest implements GroupContract.SingleEventBusGroupContract, GroupContract.MultipleEventBusGroupContract,
    KeyContract.SingleEventBusKeyContract, KeyContract.MultipleEventBusKeyContract,
    ErrorHandlingContract {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    private RabbitMQEventBus eventBus;
    private RabbitMQEventBus eventBus2;
    private RabbitMQEventBus eventBus3;
    private EventSerializer eventSerializer;
    private RoutingKeyConverter routingKeyConverter;
    private MemoryEventDeadLetters memoryEventDeadLetters;

    @BeforeEach
    void setUp() {
        memoryEventDeadLetters = new MemoryEventDeadLetters();

        TestId.Factory mailboxIdFactory = new TestId.Factory();
        eventSerializer = new EventSerializer(mailboxIdFactory, new TestMessageId.Factory(), new DefaultUserQuotaRootResolver.DefaultQuotaRootDeserializer());
        routingKeyConverter = RoutingKeyConverter.forFactories(new MailboxIdRegistrationKey.Factory(mailboxIdFactory));

        eventBus = newEventBus();
        eventBus2 = newEventBus();
        eventBus3 = newEventBus();

        eventBus.start();
        eventBus2.start();
        eventBus3.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        eventBus2.stop();
        eventBus3.stop();
        ALL_GROUPS.stream()
            .map(GroupRegistration.WorkQueueName::of)
            .forEach(queueName -> rabbitMQExtension.getRabbitChannelPool().getSender().delete(QueueSpecification.queue(queueName.asString())).block());
        rabbitMQExtension.getRabbitChannelPool()
            .getSender()
            .delete(ExchangeSpecification.exchange(MAILBOX_EVENT_EXCHANGE_NAME))
            .block();
    }

    private RabbitMQEventBus newEventBus() {
        return newEventBus(rabbitMQExtension.getRabbitChannelPool());
    }

    private RabbitMQEventBus newEventBus(ReactorRabbitMQChannelPool rabbitMQChannelPool) {
        return new RabbitMQEventBus(rabbitMQChannelPool, eventSerializer, RetryBackoffConfiguration.DEFAULT, routingKeyConverter, memoryEventDeadLetters, new RecordingMetricFactory());
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

    @Test
    void deserializeEventCollectorGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.util.EventCollector$EventCollectorGroup"))
            .isEqualTo(new EventCollector.EventCollectorGroup());
    }

    @Test
    void registerGroupShouldCreateRetryExchange() throws Exception {
        MailboxListener listener = newListener();
        EventBusTestFixture.GroupA registeredGroup = new EventBusTestFixture.GroupA();
        eventBus.register(listener, registeredGroup);

        GroupConsumerRetry.RetryExchangeName retryExchangeName = GroupConsumerRetry.RetryExchangeName.of(registeredGroup);
        assertThat(rabbitMQExtension.managementAPI().listExchanges())
            .anyMatch(exchange -> exchange.getName().equals(retryExchangeName.asString()));
    }

    @Nested
    class ConcurrentTest implements EventBusConcurrentTestContract.MultiEventBusConcurrentContract,
        EventBusConcurrentTestContract.SingleEventBusConcurrentContract {

        @Test
        void rabbitMQEventBusShouldHandleBulksGracefully() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            eventBus().register(countingListener1, new EventBusTestFixture.GroupA());
            int totalGlobalRegistrations = 1; // GroupA

            int threadCount = 10;
            int operationCount = 10000;
            int totalDispatchOperations = threadCount * operationCount;
            eventBus = (RabbitMQEventBus) eventBus();
            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus.dispatch(EVENT, NO_KEYS).block())
                .threadCount(threadCount)
                .operationCount(operationCount)
                .runSuccessfullyWithin(Duration.ofMinutes(3));

            await()
                .pollInterval(org.awaitility.Duration.FIVE_SECONDS)
                .timeout(org.awaitility.Duration.TEN_MINUTES).untilAsserted(() ->
                    assertThat(countingListener1.numberOfEventCalls()).isEqualTo((totalGlobalRegistrations * totalDispatchOperations)));
        }

        @Override
        public EventBus eventBus3() {
            return eventBus3;
        }

        @Override
        public EventBus eventBus2() {
            return eventBus2;
        }

        @Override
        public EventBus eventBus() {
            return eventBus;
        }
    }

    @Nested
    class AtLeastOnceTest {

        @Test
        void inProcessingEventShouldBeReDispatchedToAnotherEventBusWhenOneIsDown() {
            MailboxListenerCountingSuccessfulExecution eventBusListener = spy(new EventBusTestFixture.MailboxListenerCountingSuccessfulExecution());
            MailboxListenerCountingSuccessfulExecution eventBus2Listener = spy(new EventBusTestFixture.MailboxListenerCountingSuccessfulExecution());
            MailboxListenerCountingSuccessfulExecution eventBus3Listener = spy(new EventBusTestFixture.MailboxListenerCountingSuccessfulExecution());
            Answer<?> callEventAndSleepForever = invocation -> {
                invocation.callRealMethod();
                TimeUnit.SECONDS.sleep(Long.MAX_VALUE);
                return null;
            };

            doAnswer(callEventAndSleepForever).when(eventBusListener).event(any());
            doAnswer(callEventAndSleepForever).when(eventBus2Listener).event(any());

            eventBus.register(eventBusListener, GROUP_A);
            eventBus2.register(eventBus2Listener, GROUP_A);
            eventBus3.register(eventBus3Listener, GROUP_A);

            eventBus.dispatch(EVENT, NO_KEYS).block();
            WAIT_CONDITION
                .untilAsserted(() -> assertThat(eventBusListener.numberOfEventCalls()).isEqualTo(1));
            eventBus.stop();

            WAIT_CONDITION
                .untilAsserted(() -> assertThat(eventBus2Listener.numberOfEventCalls()).isEqualTo(1));
            eventBus2.stop();

            WAIT_CONDITION
                .untilAsserted(() -> assertThat(eventBus3Listener.numberOfEventCalls()).isEqualTo(1));
        }
    }

    @Nested
    class PublishingTest {
        private static final String MAILBOX_WORK_QUEUE_NAME = MAILBOX_EVENT + "-workQueue";

        @BeforeEach
        void setUp() {
            Sender sender = rabbitMQExtension.getRabbitChannelPool().getSender();

            sender.declareQueue(QueueSpecification.queue(MAILBOX_WORK_QUEUE_NAME)
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS))
                .block();
            sender.bind(BindingSpecification.binding()
                .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
                .queue(MAILBOX_WORK_QUEUE_NAME)
                .routingKey(EMPTY_ROUTING_KEY))
                .block();
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQ() {
            eventBus.dispatch(EVENT, NO_KEYS).block();

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQWhenNotBlocking() {
            eventBus.dispatch(EVENT, NO_KEYS).block();

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        private Event dequeueEvent() {
            try (Receiver receiver = rabbitMQExtension.getRabbitChannelPool().createReceiver()) {
                byte[] eventInBytes = receiver.consumeAutoAck(MAILBOX_WORK_QUEUE_NAME)
                    .blockFirst()
                    .getBody();

                return eventSerializer.fromJson(new String(eventInBytes, StandardCharsets.UTF_8))
                    .get();
            }
        }
    }

    @Nested
    class LifeCycleTest {
        private static final int THREAD_COUNT = 10;
        private static final int OPERATION_COUNT = 100000;

        private RabbitMQManagementAPI rabbitManagementAPI;

        @BeforeEach
        void setUp() throws Exception {
            rabbitManagementAPI = rabbitMQExtension.managementAPI();
        }

        @AfterEach
        void tearDown() {
            rabbitMQExtension.getRabbitMQ().unpause();
        }

        @Nested
        class SingleEventBus {

            @Nested
            class DispatchingWhenNetWorkIssue {

                @RegisterExtension
                RabbitMQExtension rabbitMQNetWorkIssueExtension = RabbitMQExtension.defaultRabbitMQ()
                    .restartPolicy(DockerRestartPolicy.PER_TEST);

                private RabbitMQEventBus rabbitMQEventBusWithNetWorkIssue;

                @BeforeEach
                void beforeEach() {
                    rabbitMQEventBusWithNetWorkIssue = newEventBus(rabbitMQNetWorkIssueExtension.getRabbitChannelPool());
                }

                @Test
                void dispatchShouldWorkAfterNetworkIssuesForOldRegistration() {
                    rabbitMQEventBusWithNetWorkIssue.start();
                    MailboxListener listener = newListener();
                    rabbitMQEventBusWithNetWorkIssue.register(listener, GROUP_A);

                    rabbitMQNetWorkIssueExtension.getRabbitMQ().pause();

                    assertThatThrownBy(() -> rabbitMQEventBusWithNetWorkIssue.dispatch(EVENT, NO_KEYS).block())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Retries exhausted");

                    rabbitMQNetWorkIssueExtension.getRabbitMQ().unpause();

                    rabbitMQEventBusWithNetWorkIssue.dispatch(EVENT, NO_KEYS).block();
                    assertThatListenerReceiveOneEvent(listener);
                }
            }

            @Test
            void startShouldCreateEventExchange() {
                eventBus.start();
                assertThat(rabbitManagementAPI.listExchanges())
                    .filteredOn(exchange -> exchange.getName().equals(MAILBOX_EVENT_EXCHANGE_NAME))
                    .hasOnlyOneElementSatisfying(exchange -> {
                        assertThat(exchange.isDurable()).isTrue();
                        assertThat(exchange.getType()).isEqualTo(DIRECT_EXCHANGE);
                    });
            }

            @Test
            void dispatchShouldWorkAfterRestartForOldRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();
                eventBus.register(listener, GROUP_A);

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.dispatch(EVENT, NO_KEYS).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterRestartForNewRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.register(listener, GROUP_A);

                eventBus.dispatch(EVENT, NO_KEYS).block();

                assertThatListenerReceiveOneEvent(listener);

            }

            @Test
            void redeliverShouldWorkAfterRestartForOldRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();
                eventBus.register(listener, GROUP_A);

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.reDeliver(GROUP_A, EVENT).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void redeliverShouldWorkAfterRestartForNewRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.register(listener, GROUP_A);

                eventBus.reDeliver(GROUP_A, EVENT).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterRestartForOldKeyRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();
                eventBus.register(listener, KEY_1);

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.dispatch(EVENT, KEY_1).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterRestartForNewKeyRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.register(listener, KEY_1);

                eventBus.dispatch(EVENT, KEY_1).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterNetworkIssuesForNewRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().pause();

                assertThatThrownBy(() -> eventBus.dispatch(EVENT, NO_KEYS).block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Retries exhausted");

                rabbitMQExtension.getRabbitMQ().unpause();

                eventBus.register(listener, GROUP_A);
                eventBus.dispatch(EVENT, NO_KEYS).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void redeliverShouldWorkAfterNetworkIssuesForNewRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().pause();

                assertThatThrownBy(() -> eventBus.reDeliver(GROUP_A, EVENT).block())
                    .isInstanceOf(GroupRegistrationNotFound.class);

                rabbitMQExtension.getRabbitMQ().unpause();

                eventBus.register(listener, GROUP_A);
                eventBus.reDeliver(GROUP_A, EVENT).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterNetworkIssuesForOldKeyRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();
                when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
                eventBus.register(listener, KEY_1);

                rabbitMQExtension.getRabbitMQ().pause();

                assertThatThrownBy(() -> eventBus.dispatch(EVENT, NO_KEYS).block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Retries exhausted");

                rabbitMQExtension.getRabbitMQ().unpause();

                eventBus.dispatch(EVENT, KEY_1).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterNetworkIssuesForNewKeyRegistration() {
                eventBus.start();
                MailboxListener listener = newListener();
                when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);

                rabbitMQExtension.getRabbitMQ().pause();

                assertThatThrownBy(() -> eventBus.dispatch(EVENT, NO_KEYS).block())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Retries exhausted");

                rabbitMQExtension.getRabbitMQ().unpause();

                eventBus.register(listener, KEY_1);
                eventBus.dispatch(EVENT, KEY_1).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void stopShouldNotDeleteEventBusExchange() {
                eventBus.start();
                eventBus.stop();

                assertThat(rabbitManagementAPI.listExchanges())
                    .anySatisfy(exchange -> assertThat(exchange.getName()).isEqualTo(MAILBOX_EVENT_EXCHANGE_NAME));
            }

            @Test
            void stopShouldNotDeleteGroupRegistrationWorkQueue() {
                eventBus.start();
                eventBus.register(mock(MailboxListener.class), GROUP_A);
                eventBus.stop();

                assertThat(rabbitManagementAPI.listQueues())
                    .anySatisfy(queue -> assertThat(queue.getName()).contains(GroupA.class.getName()));
            }

            @Test
            void eventBusShouldNotThrowWhenContinuouslyStartAndStop() {
                assertThatCode(() -> {
                    eventBus.start();
                    eventBus.stop();
                    eventBus.stop();
                    eventBus.start();
                    eventBus.start();
                    eventBus.start();
                    eventBus.stop();
                    eventBus.stop();
                }).doesNotThrowAnyException();
            }

            @Test
            void dispatchShouldStopDeliveringEventsShortlyAfterStopIsCalled() throws Exception {
                eventBus.start();

                MailboxListenerCountingSuccessfulExecution listener = new MailboxListenerCountingSuccessfulExecution();
                eventBus.register(listener, GROUP_A);

                try (Closeable closeable = ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1).block())
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .noErrorLogs()
                    .run()) {

                    TimeUnit.SECONDS.sleep(2);

                    eventBus.stop();
                    eventBus2.stop();
                    int callsAfterStop = listener.numberOfEventCalls();

                    TimeUnit.SECONDS.sleep(1);
                    assertThat(listener.numberOfEventCalls())
                        .isCloseTo(callsAfterStop, Percentage.withPercentage(2));
                }
            }
        }

        @Nested
        class MultiEventBus {

            @Test
            void multipleEventBusStartShouldCreateOnlyOneEventExchange() {
                assertThat(rabbitManagementAPI.listExchanges())
                    .filteredOn(exchange -> exchange.getName().equals(MAILBOX_EVENT_EXCHANGE_NAME))
                    .hasSize(1);
            }

            @Test
            void multipleEventBusShouldNotThrowWhenStartAndStopContinuously() {
                assertThatCode(() -> {
                    eventBus.start();
                    eventBus.start();
                    eventBus2.start();
                    eventBus2.start();
                    eventBus.stop();
                    eventBus.stop();
                    eventBus.stop();
                    eventBus3.start();
                    eventBus3.start();
                    eventBus3.start();
                    eventBus3.stop();
                    eventBus.start();
                    eventBus2.start();
                    eventBus.stop();
                    eventBus2.stop();
                }).doesNotThrowAnyException();
            }

            @Test
            void multipleEventBusStopShouldNotDeleteEventBusExchange() {
                eventBus.stop();
                eventBus2.stop();
                eventBus3.stop();

                assertThat(rabbitManagementAPI.listExchanges())
                    .anySatisfy(exchange -> assertThat(exchange.getName()).isEqualTo(MAILBOX_EVENT_EXCHANGE_NAME));
            }

            @Test
            void multipleEventBusStopShouldNotDeleteGroupRegistrationWorkQueue() {
                eventBus.register(mock(MailboxListener.class), GROUP_A);

                eventBus.stop();
                eventBus2.stop();
                eventBus3.stop();

                assertThat(rabbitManagementAPI.listQueues())
                    .anySatisfy(queue -> assertThat(queue.getName()).contains(GroupA.class.getName()));
            }

            @Test
            void multipleEventBusStopShouldDeleteAllKeyRegistrationsWorkQueue() {
                eventBus.stop();
                eventBus2.stop();
                eventBus3.stop();

                assertThat(rabbitManagementAPI.listQueues())
                    .filteredOn(queue -> !queue.getName().startsWith(MAILBOX_EVENT_WORK_QUEUE_PREFIX))
                    .isEmpty();
            }

            @Test
            void dispatchShouldStopDeliveringEventsShortlyAfterStopIsCalled() throws Exception {
                eventBus.start();
                eventBus2.start();

                MailboxListenerCountingSuccessfulExecution listener = new MailboxListenerCountingSuccessfulExecution();
                eventBus.register(listener, GROUP_A);
                eventBus2.register(listener, GROUP_A);

                try (Closeable closeable = ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1).block())
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .noErrorLogs()
                    .run()) {

                    TimeUnit.SECONDS.sleep(2);

                    eventBus.stop();
                    eventBus2.stop();
                    int callsAfterStop = listener.numberOfEventCalls();

                    TimeUnit.SECONDS.sleep(1);
                    assertThat(listener.numberOfEventCalls())
                        .isCloseTo(callsAfterStop, Percentage.withPercentage(2));
                }
            }
        }

    }

    private void assertThatListenerReceiveOneEvent(MailboxListener listener) {
        RabbitMQFixture.awaitAtMostThirtySeconds
            .untilAsserted(() -> verify(listener).event(EVENT));
    }
}