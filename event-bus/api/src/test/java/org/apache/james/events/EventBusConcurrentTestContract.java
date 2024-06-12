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
import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.apache.james.events.EventBusTestFixture.KEY_2;
import static org.apache.james.events.EventBusTestFixture.KEY_3;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.james.events.EventBusTestFixture.EventListenerCountingSuccessfulExecution;
import org.apache.james.events.EventBusTestFixture.GroupA;
import org.apache.james.events.EventBusTestFixture.GroupB;
import org.apache.james.events.EventBusTestFixture.GroupC;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public interface EventBusConcurrentTestContract {

    Duration FIVE_SECONDS = Duration.ofSeconds(5);
    ConditionFactory AWAIT_CONDITION = await().timeout(Duration.ofSeconds(5));

    int THREAD_COUNT = 10;
    int OPERATION_COUNT = 30;
    int TOTAL_DISPATCH_OPERATIONS = THREAD_COUNT * OPERATION_COUNT;

    Set<RegistrationKey> ALL_KEYS = ImmutableSet.of(KEY_1, KEY_2, KEY_3);

    static EventListenerCountingSuccessfulExecution newCountingListener() {
        return new EventListenerCountingSuccessfulExecution();
    }

    static int totalEventsReceived(ImmutableList<EventListenerCountingSuccessfulExecution> allListeners) {
        return allListeners.stream()
            .mapToInt(EventListenerCountingSuccessfulExecution::numberOfEventCalls)
            .sum();
    }

    interface SingleEventBusConcurrentContract extends EventBusContract {

        @Test
        default void concurrentDispatchGroupShouldDeliverAllEventsToListenersWithSingleEventBus() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, new GroupA());
            eventBus().register(countingListener2, new GroupB());
            eventBus().register(countingListener3, new GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, NO_KEYS))
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() ->
                assertThat(totalEventsReceived(ImmutableList.of(countingListener1, countingListener2, countingListener3)))
                    .isEqualTo(totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchKeyShouldDeliverAllEventsToListenersWithSingleEventBus() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener3 = newCountingListener();
            Mono.from(eventBus().register(countingListener1, KEY_1)).block();
            Mono.from(eventBus().register(countingListener2, KEY_2)).block();
            Mono.from(eventBus().register(countingListener3, KEY_3)).block();

            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventBus = 1;

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS))
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() ->
                assertThat(totalEventsReceived(ImmutableList.of(countingListener1, countingListener2, countingListener3)))
                    .isEqualTo(totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchShouldDeliverAllEventsToListenersWithSingleEventBus() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, new GroupA());
            eventBus().register(countingListener2, new GroupB());
            eventBus().register(countingListener3, new GroupC());

            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC
            int totalEventDeliveredGlobally = totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS;

            Mono.from(eventBus().register(countingListener1, KEY_1)).block();
            Mono.from(eventBus().register(countingListener2, KEY_2)).block();
            Mono.from(eventBus().register(countingListener3, KEY_3)).block();
            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventDeliveredByKeys = totalKeyListenerRegistrations * TOTAL_DISPATCH_OPERATIONS;

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS))
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() ->
                assertThat(totalEventsReceived(ImmutableList.of(countingListener1, countingListener2, countingListener3)))
                    .isEqualTo(totalEventDeliveredGlobally + totalEventDeliveredByKeys));
        }

        @Test
        default void concurrentRegisterThenDispatchShouldDeliverAllEventsToAtLeastOneListenerWithSingleEventBus()
                throws Exception {
            List<EventListenerCountingSuccessfulExecution> countingListeners = IntStream
                    .range(0, TOTAL_DISPATCH_OPERATIONS).mapToObj(i -> newCountingListener()).toList();

            ConcurrentTestRunner.builder()
                    .reactorOperation((threadNumber, operationNumber) -> Mono
                            .from(eventBus().register(
                                    countingListeners.get(threadNumber * OPERATION_COUNT + operationNumber), KEY_1))
                            .then(eventBus().dispatch(EVENT, ALL_KEYS)))
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() -> assertThat(countingListeners.stream()
                    .mapToInt(EventListenerCountingSuccessfulExecution::numberOfEventCalls).max().getAsInt())
                    .isEqualTo(TOTAL_DISPATCH_OPERATIONS));
        }
    }

    interface MultiEventBusConcurrentContract extends EventBusContract.MultipleEventBusContract {

        EventBus eventBus3();

        @Test
        default void concurrentDispatchGroupShouldDeliverAllEventsToListenersWithMultipleEventBus() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus().register(countingListener1, new GroupA());
            eventBus().register(countingListener2, new GroupB());
            eventBus().register(countingListener3, new GroupC());

            eventBus2().register(countingListener1, new GroupA());
            eventBus2().register(countingListener2, new GroupB());
            eventBus2().register(countingListener3, new GroupC());

            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, NO_KEYS))
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() ->
                assertThat(totalEventsReceived(ImmutableList.of(countingListener1, countingListener2, countingListener3)))
                    .isEqualTo(totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchKeyShouldDeliverAllEventsToListenersWithMultipleEventBus() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            Mono.from(eventBus().register(countingListener1, KEY_1)).block();
            Mono.from(eventBus().register(countingListener2, KEY_2)).block();
            Mono.from(eventBus().register(countingListener3, KEY_3)).block();

            Mono.from(eventBus2().register(countingListener1, KEY_1)).block();
            Mono.from(eventBus2().register(countingListener2, KEY_2)).block();
            Mono.from(eventBus2().register(countingListener3, KEY_3)).block();

            int totalKeyListenerRegistrations = 3; // KEY1 + KEY2 + KEY3
            int totalEventBus = 2; // eventBus1 + eventBus2

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS))
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() ->
                assertThat(totalEventsReceived(ImmutableList.of(countingListener1, countingListener2, countingListener3)))
                    .isEqualTo(totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS));
        }

        @Test
        default void concurrentDispatchShouldDeliverAllEventsToListenersWithMultipleEventBus() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener2 = newCountingListener();
            EventListenerCountingSuccessfulExecution countingListener3 = newCountingListener();

            eventBus2().register(countingListener1, EventDeadLettersContract.GROUP_A);
            eventBus2().register(countingListener2, new GroupB());
            eventBus2().register(countingListener3, new GroupC());
            int totalGlobalRegistrations = 3; // GroupA + GroupB + GroupC
            int totalEventDeliveredGlobally = totalGlobalRegistrations * TOTAL_DISPATCH_OPERATIONS;

            Mono.from(eventBus().register(countingListener1, KEY_1)).block();
            Mono.from(eventBus().register(countingListener2, KEY_2)).block();

            Mono.from(eventBus2().register(countingListener1, KEY_1)).block();
            Mono.from(eventBus2().register(countingListener2, KEY_2)).block();

            Mono.from(eventBus3().register(countingListener3, KEY_1)).block();
            Mono.from(eventBus3().register(countingListener3, KEY_2)).block();

            int totalKeyListenerRegistrations = 2; // KEY1 + KEY2
            int totalEventBus = 3; // eventBus1 + eventBus2 + eventBus3
            int totalEventDeliveredByKeys = totalKeyListenerRegistrations * totalEventBus * TOTAL_DISPATCH_OPERATIONS;

            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus().dispatch(EVENT, ALL_KEYS))
                .threadCount(THREAD_COUNT)
                .operationCount(OPERATION_COUNT)
                .runSuccessfullyWithin(FIVE_SECONDS);

            AWAIT_CONDITION.untilAsserted(() ->
                assertThat(totalEventsReceived(ImmutableList.of(countingListener1, countingListener2, countingListener3)))
                    .isEqualTo(totalEventDeliveredGlobally + totalEventDeliveredByKeys));
        }
    }
}
