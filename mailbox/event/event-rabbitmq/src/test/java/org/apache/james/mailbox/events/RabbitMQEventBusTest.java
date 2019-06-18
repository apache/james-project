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

import static com.jayway.awaitility.Awaitility.await;
import static org.apache.james.backend.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backend.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backend.rabbitmq.Constants.DURABLE;
import static org.apache.james.backend.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backend.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backend.rabbitmq.Constants.NO_ARGUMENTS;
import static org.apache.james.mailbox.events.EventBusConcurrentTestContract.newCountingListener;
import static org.apache.james.mailbox.events.EventBusConcurrentTestContract.totalEventsReceived;
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

import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.backend.rabbitmq.RabbitMQExtension.DockerRestartPolicy;
import org.apache.james.backend.rabbitmq.RabbitMQFixture;
import org.apache.james.backend.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.backend.rabbitmq.SimpleConnectionPool;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.mailbox.events.EventBusTestFixture.GroupA;
import org.apache.james.mailbox.events.EventBusTestFixture.MailboxListenerCountingSuccessfulExecution;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.RabbitFluxException;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class RabbitMQEventBusTest implements GroupContract.SingleEventBusGroupContract, GroupContract.MultipleEventBusGroupContract,
    KeyContract.SingleEventBusKeyContract, KeyContract.MultipleEventBusKeyContract,
    ErrorHandlingContract {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    private RabbitMQEventBus eventBus;
    private RabbitMQEventBus eventBus2;
    private RabbitMQEventBus eventBus3;
    private Sender sender;
    private EventSerializer eventSerializer;
    private RoutingKeyConverter routingKeyConverter;
    private MemoryEventDeadLetters memoryEventDeadLetters;
    private Mono<Connection> resilientConnection;

    @BeforeEach
    void setUp() {
        memoryEventDeadLetters = new MemoryEventDeadLetters();

        TestId.Factory mailboxIdFactory = new TestId.Factory();
        eventSerializer = new EventSerializer(mailboxIdFactory, new TestMessageId.Factory());
        routingKeyConverter = RoutingKeyConverter.forFactories(new MailboxIdRegistrationKey.Factory(mailboxIdFactory));

        eventBus = newEventBus();
        eventBus2 = newEventBus();
        eventBus3 = newEventBus();

        eventBus.start();
        eventBus2.start();
        eventBus3.start();
        resilientConnection = rabbitMQExtension.getRabbitConnectionPool().getResilientConnection();
        sender = RabbitFlux.createSender(new SenderOptions().connectionMono(resilientConnection));
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        eventBus2.stop();
        eventBus3.stop();
        ALL_GROUPS.stream()
            .map(GroupRegistration.WorkQueueName::of)
            .forEach(queueName -> sender.delete(QueueSpecification.queue(queueName.asString())).block());
        sender.delete(ExchangeSpecification.exchange(MAILBOX_EVENT_EXCHANGE_NAME)).block();
        sender.close();
    }

    private RabbitMQEventBus newEventBus() {
        return newEventBus(rabbitMQExtension.getRabbitConnectionPool());
    }

    private RabbitMQEventBus newEventBus(SimpleConnectionPool connectionPool) {
        return new RabbitMQEventBus(connectionPool, eventSerializer, RetryBackoffConfiguration.DEFAULT, routingKeyConverter, memoryEventDeadLetters, new NoopMetricFactory());
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
        void rabbitMQEventBusCannotHandleHugeDispatchingOperations() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();

            eventBus().register(countingListener1, new EventBusTestFixture.GroupA());
            int totalGlobalRegistrations = 1; // GroupA + GroupB + GroupC

            int threadCount = 10;
            int operationCount = 10000;
            int totalDispatchOperations = threadCount * operationCount;
            eventBus = (RabbitMQEventBus) eventBus();
            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus.dispatch(EVENT, NO_KEYS).block())
                .threadCount(threadCount)
                .operationCount(operationCount)
                .runSuccessfullyWithin(Duration.ofMinutes(10));

            // there is a moment when RabbitMQ EventBus consumed amount of messages, then it will stop to consume more
            await()
                .pollInterval(com.jayway.awaitility.Duration.FIVE_SECONDS)
                .timeout(com.jayway.awaitility.Duration.TEN_MINUTES).until(() -> {
                    int totalEventsReceived = totalEventsReceived(ImmutableList.of(countingListener1));
                    System.out.println("event received: " + totalEventsReceived);
                    System.out.println("dispatching count: " + eventBus.eventDispatcher.dispatchCount.get());
                    assertThat(totalEventsReceived)
                        .isEqualTo(totalGlobalRegistrations * totalDispatchOperations);
                });
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
                .until(() -> assertThat(eventBusListener.numberOfEventCalls()).isEqualTo(1));
            eventBus.stop();

            WAIT_CONDITION
                .until(() -> assertThat(eventBus2Listener.numberOfEventCalls()).isEqualTo(1));
            eventBus2.stop();

            WAIT_CONDITION
                .until(() -> assertThat(eventBus3Listener.numberOfEventCalls()).isEqualTo(1));
        }
    }

    @Nested
    class PublishingTest {
        private static final String MAILBOX_WORK_QUEUE_NAME = MAILBOX_EVENT + "-workQueue";
        private Sender sender1;

        @BeforeEach
        void setUp() {
            SenderOptions senderOption = new SenderOptions().connectionMono(resilientConnection);
            sender1 = RabbitFlux.createSender(senderOption);

            sender1.declareQueue(QueueSpecification.queue(MAILBOX_WORK_QUEUE_NAME)
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS))
                .block();
            sender1.bind(BindingSpecification.binding()
                .exchange(MAILBOX_EVENT_EXCHANGE_NAME)
                .queue(MAILBOX_WORK_QUEUE_NAME)
                .routingKey(EMPTY_ROUTING_KEY))
                .block();
        }

        @AfterEach
        void tearDown() {
            sender1.close();
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQ() {
            eventBus.dispatch(EVENT, NO_KEYS).block();

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQWhenNotBlocking() {
            eventBus.dispatch(EVENT, NO_KEYS);

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        private Event dequeueEvent() {
            try (Receiver receiver = RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(resilientConnection))) {
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
        private static final int MAX_EVENT_DISPATCHED_COUNT = THREAD_COUNT * OPERATION_COUNT;

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
                    rabbitMQEventBusWithNetWorkIssue = newEventBus(rabbitMQNetWorkIssueExtension.getRabbitConnectionPool());
                }

                @Test
                void dispatchShouldWorkAfterNetworkIssuesForOldRegistration() {
                    rabbitMQEventBusWithNetWorkIssue.start();
                    MailboxListener listener = newListener();
                    rabbitMQEventBusWithNetWorkIssue.register(listener, GROUP_A);

                    rabbitMQNetWorkIssueExtension.getRabbitMQ().pause();

                    assertThatThrownBy(() -> rabbitMQEventBusWithNetWorkIssue.dispatch(EVENT, NO_KEYS).block())
                        .isInstanceOf(RabbitFluxException.class);

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
                    .isInstanceOf(RabbitFluxException.class);

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
                    .isInstanceOf(RabbitFluxException.class);

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
                    .isInstanceOf(RabbitFluxException.class);

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
            void registrationsShouldNotHandleEventsAfterStop() throws Exception {
                eventBus.start();

                MailboxListenerCountingSuccessfulExecution listener = new MailboxListenerCountingSuccessfulExecution();
                eventBus.register(listener, GROUP_A);

                try (Closeable closeable = ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1))
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .run()) {

                    TimeUnit.SECONDS.sleep(2);

                    eventBus.stop();
                    eventBus2.stop();
                    int callsAfterStop = listener.numberOfEventCalls();

                    TimeUnit.SECONDS.sleep(1);
                    assertThat(listener.numberOfEventCalls())
                        .isEqualTo(callsAfterStop)
                        .isLessThanOrEqualTo(MAX_EVENT_DISPATCHED_COUNT);
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
            void registrationsShouldNotHandleEventsAfterStop() throws Exception {
                eventBus.start();
                eventBus2.start();

                MailboxListenerCountingSuccessfulExecution listener = new MailboxListenerCountingSuccessfulExecution();
                eventBus.register(listener, GROUP_A);
                eventBus2.register(listener, GROUP_A);

                try (Closeable closeable = ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1))
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .run()) {

                    TimeUnit.SECONDS.sleep(2);

                    eventBus.stop();
                    eventBus2.stop();
                    int callsAfterStop = listener.numberOfEventCalls();

                    TimeUnit.SECONDS.sleep(1);
                    assertThat(listener.numberOfEventCalls())
                        .isEqualTo(callsAfterStop)
                        .isLessThanOrEqualTo(MAX_EVENT_DISPATCHED_COUNT);
                }
            }
        }

    }

    private void assertThatListenerReceiveOneEvent(MailboxListener listener) {
        RabbitMQFixture.awaitAtMostThirtySeconds
            .untilAsserted(() -> verify(listener).event(EVENT));
    }
}