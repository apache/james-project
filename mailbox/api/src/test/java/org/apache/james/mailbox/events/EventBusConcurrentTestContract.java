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
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_1;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_2;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_3;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventDeadLettersContract.GROUP_A;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.jayway.awaitility.core.ConditionFactory;

public interface EventBusConcurrentTestContract {

    Duration FIVE_SECONDS = Duration.ofSeconds(5);
    ConditionFactory AWAIT_CONDITION = await().timeout(new com.jayway.awaitility.Duration(5, TimeUnit.SECONDS));

    int THREAD_COUNT = 10;
    int OPERATION_COUNT = 30;
    int TOTAL_DISPATCH_OPERATIONS = THREAD_COUNT * OPERATION_COUNT;

    Set<RegistrationKey> ALL_KEYS = ImmutableSet.of(KEY_1, KEY_2, KEY_3);

    static EventBusTestFixture.MailboxListenerCountingSuccessfulExecution newCountingListener() {
        return new EventBusTestFixture.MailboxListenerCountingSuccessfulExecution();
    }

    static int totalEventsReceived(ImmutableList<EventBusTestFixture.MailboxListenerCountingSuccessfulExecution> allListeners) {
        return allListeners.stream()
            .mapToInt(listener -> listener.numberOfEventCalls())
            .sum();
    }

    interface SingleEventBusConcurrentContract extends EventBusContract {

        @Test
        default void concurrentDispatchGroupShouldDeliverAllEventsToListenersWithSingleEventBus() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, new EventBusTestFixture.GroupA());
            eventBus().register(countingListener2, new EventBusTestFixture.GroupB());
            eventBus().register(countingListener3, new EventBusTestFixture.GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.until(() -> assertThat(totalEventsReceived(ImmutableList
                    .of(countingListener1, countingListener2, countingListener3)))
                .isEqualTo(totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchKeyShouldDeliverAllEventsToListenersWithSingleEventBus() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener3 = newCountingListener();
            eventBus().register(countingListener1, KEY_1);
            eventBus().register(countingListener2, KEY_2);
            eventBus().register(countingListener3, KEY_3);

            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventBus = 1;

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS).block())
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.until(() -> assertThat(totalEventsReceived(ImmutableList
                    .of(countingListener1, countingListener2, countingListener3)))
                .isEqualTo(totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchShouldDeliverAllEventsToListenersWithSingleEventBus() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, new EventBusTestFixture.GroupA());
            eventBus().register(countingListener2, new EventBusTestFixture.GroupB());
            eventBus().register(countingListener3, new EventBusTestFixture.GroupC());

            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC
            int totalEventDeliveredGlobally = totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS;

            eventBus().register(countingListener1, KEY_1);
            eventBus().register(countingListener2, KEY_2);
            eventBus().register(countingListener3, KEY_3);
            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventDeliveredByKeys = totalKeyListenerRegistrations * TOTAL_DISPATCH_OPERATIONS;

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS).block())
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.until(() -> assertThat(totalEventsReceived(ImmutableList
                    .of(countingListener1, countingListener2, countingListener3)))
                .isEqualTo(totalEventDeliveredGlobally + totalEventDeliveredByKeys));
        }
    }

    interface MultiEventBusConcurrentContract extends EventBusContract.MultipleEventBusContract {

        EventBus eventBus3();

        @Test
        default void concurrentDispatchGroupShouldDeliverAllEventsToListenersWithMultipleEventBus() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, new EventBusTestFixture.GroupA());
            eventBus().register(countingListener2, new EventBusTestFixture.GroupB());
            eventBus().register(countingListener3, new EventBusTestFixture.GroupC());

            eventBus2().register(countingListener1, new EventBusTestFixture.GroupA());
            eventBus2().register(countingListener2, new EventBusTestFixture.GroupB());
            eventBus2().register(countingListener3, new EventBusTestFixture.GroupC());

            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, NO_KEYS).block())
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.until(() -> assertThat(totalEventsReceived(ImmutableList
                    .of(countingListener1, countingListener2, countingListener3)))
                .isEqualTo(totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchKeyShouldDeliverAllEventsToListenersWithMultipleEventBus() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, KEY_1);
            eventBus().register(countingListener2, KEY_2);
            eventBus().register(countingListener3, KEY_3);

            eventBus2().register(countingListener1, KEY_1);
            eventBus2().register(countingListener2, KEY_2);
            eventBus2().register(countingListener3, KEY_3);

            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventBus = 2; // eventBus1 + eventBus2

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS).block())
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.until(() -> assertThat(totalEventsReceived(ImmutableList
                    .of(countingListener1, countingListener2, countingListener3)))
                .isEqualTo(totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchShouldDeliverAllEventsToListenersWithMultipleEventBus() throws Exception {
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventBusTestFixture.MailboxListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus2().register(countingListener1, GROUP_A);
            eventBus2().register(countingListener2, new EventBusTestFixture.GroupB());
            eventBus2().register(countingListener3, new EventBusTestFixture.GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC
            int totalEventDeliveredGlobally = totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS;

            eventBus().register(countingListener1, KEY_1);
            eventBus().register(countingListener2, KEY_2);

            eventBus2().register(countingListener1, KEY_1);
            eventBus2().register(countingListener2, KEY_2);

            eventBus3().register(countingListener3, KEY_1);
            eventBus3().register(countingListener3, KEY_2);

            int totalKeyListenerRegistrations = 2; // KEY1 + KEY2
            int totalEventBus = 3; // eventBus1 + eventBus2 + eventBus3
            int totalEventDeliveredByKeys = totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS;

            ConcurrentTestRunner.builder()
                .operation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS).block())
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.until(() -> assertThat(totalEventsReceived(ImmutableList
                    .of(countingListener1, countingListener2, countingListener3)))
                .isEqualTo(totalEventDeliveredGlobally + totalEventDeliveredByKeys));
        }
    }
}
