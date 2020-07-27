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

import static org.apache.james.mailbox.events.EventBusTestFixture.DEFAULT_FIRST_BACKOFF;
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT_2;
import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT_ID;
import static org.apache.james.mailbox.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.mailbox.events.EventBusTestFixture.KEY_1;
import static org.apache.james.mailbox.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.mailbox.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.mailbox.util.EventCollector;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

interface ErrorHandlingContract extends EventBusContract {

    class ThrowingListener implements MailboxListener {
        private final List<Instant> timeElapsed;

        private ThrowingListener() {
            timeElapsed = new ArrayList<>();
        }

        @Override
        public boolean isHandling(Event event) {
            return true;
        }

        @Override
        public void event(Event event) {
            timeElapsed.add(Instant.now());
            throw new RuntimeException("throw to trigger reactor retry");
        }

        public int executionCount() {
            return timeElapsed.size();
        }
    }

    EventDeadLetters deadLetter();

    default EventCollector eventCollector() {
        return spy(new EventCollector());
    }

    default ThrowingListener throwingListener() {
        return new ThrowingListener();
    }

    @Test
    default void retryingIsNotAppliedForKeyRegistrations() {
        EventCollector eventCollector = eventCollector();

        doThrow(new RuntimeException())
            .when(eventCollector).event(EVENT);

        Mono.from(eventBus().register(eventCollector, KEY_1)).block();
        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        assertThat(eventCollector.getEvents())
            .isEmpty();
    }

    @Test
    default void listenerShouldReceiveWhenFailsLessThanMaxRetries() {
        EventCollector eventCollector = eventCollector();

        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        getSpeedProfile().shortWaitCondition()
            .untilAsserted(() -> assertThat(eventCollector.getEvents()).hasSize(1));
    }

    @Test
    default void listenerShouldReceiveWhenFailsEqualsMaxRetries() {
        EventCollector eventCollector = eventCollector();
        //do throw  RetryBackoffConfiguration.DEFAULT.DEFAULT_MAX_RETRIES
        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        getSpeedProfile().longWaitCondition()
            .untilAsserted(() -> assertThat(eventCollector.getEvents()).hasSize(1));
    }

    @Test
    default void listenerShouldNotReceiveWhenFailsGreaterThanMaxRetries() throws Exception {
        EventCollector eventCollector = eventCollector();

        //do throw  RetryBackoffConfiguration.DEFAULT.DEFAULT_MAX_RETRIES + 1 times
        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        TimeUnit.SECONDS.sleep(1);
        assertThat(eventCollector.getEvents())
            .isEmpty();
    }

    @Test
    default void exceedingMaxRetriesShouldStopConsumingFailedEvent() throws Exception {
        ThrowingListener throwingListener = throwingListener();

        eventBus().register(throwingListener, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        getSpeedProfile().shortWaitCondition()
            .untilAsserted(() -> assertThat(throwingListener.executionCount()).isEqualTo(4));
        Thread.sleep(getSpeedProfile().getShortWaitTime().toMillis());

        assertThat(throwingListener.executionCount())
            .isEqualTo(4);
    }

    @Test
    default void retriesBackOffShouldDelayByExponentialGrowth() throws Exception {
        ThrowingListener throwingListener = throwingListener();

        eventBus().register(throwingListener, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        Thread.sleep(getSpeedProfile().getShortWaitTime().toMillis());
        SoftAssertions.assertSoftly(softly -> {
            List<Instant> timeElapsed = throwingListener.timeElapsed;
            softly.assertThat(timeElapsed).hasSize(RETRY_BACKOFF_CONFIGURATION.getMaxRetries() + 1);

            long minFirstDelayAfter = DEFAULT_FIRST_BACKOFF.toMillis(); // first backOff
            long minSecondDelayAfter = DEFAULT_FIRST_BACKOFF.toMillis() / 2; // first_backOff * jitter factor (first_backOff * 0.5)
            long minThirdDelayAfter = DEFAULT_FIRST_BACKOFF.toMillis(); // first_backOff * jitter factor (first_backOff * 1)

            softly.assertThat(timeElapsed.get(1))
                .isAfterOrEqualTo(timeElapsed.get(0).plusMillis(minFirstDelayAfter));

            softly.assertThat(timeElapsed.get(2))
                .isAfterOrEqualTo(timeElapsed.get(1).plusMillis(minSecondDelayAfter));

            softly.assertThat(timeElapsed.get(3))
                .isAfterOrEqualTo(timeElapsed.get(2).plusMillis(minThirdDelayAfter));
        });
    }

    @Test
    default void retryingListenerCallingDispatchShouldNotFail() {
        AtomicBoolean firstExecution = new AtomicBoolean(true);
        AtomicBoolean successfulRetry = new AtomicBoolean(false);
        MailboxListener listener = event -> {
            if (event.getEventId().equals(EVENT_ID)) {
                if (firstExecution.get()) {
                    firstExecution.set(false);
                    throw new RuntimeException();
                }
                eventBus().dispatch(EVENT_2, NO_KEYS).block();
                successfulRetry.set(true);
            }
        };

        eventBus().register(listener, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        getSpeedProfile().shortWaitCondition().until(successfulRetry::get);
    }

    @Test
    default void deadLettersIsNotAppliedForKeyRegistrations() throws Exception {
        EventCollector eventCollector = eventCollector();

        //do throw  RetryBackoffConfiguration.DEFAULT.DEFAULT_MAX_RETRIES + 1 times
        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        Mono.from(eventBus().register(eventCollector, KEY_1)).block();
        eventBus().dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

        TimeUnit.SECONDS.sleep(1);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(eventCollector.getEvents()).isEmpty();
            softly.assertThat(deadLetter().groupsWithFailedEvents().toIterable())
                .isEmpty();
        });
    }

    @Test
    default void deadLetterShouldNotStoreWhenFailsLessThanMaxRetries() {
        EventCollector eventCollector = eventCollector();

        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, new EventBusTestFixture.GroupA());
        eventBus().dispatch(EVENT, NO_KEYS).block();

        getSpeedProfile().shortWaitCondition()
            .untilAsserted(() -> assertThat(eventCollector.getEvents()).hasSize(1));

        assertThat(deadLetter().groupsWithFailedEvents().toIterable())
            .isEmpty();
    }

    @Test
    default void deadLetterShouldStoreWhenDispatchFailsGreaterThanMaxRetries() {
        EventCollector eventCollector = eventCollector();
        //do throw  RetryBackoffConfiguration.DEFAULT.DEFAULT_MAX_RETRIES + 1 times
        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, GROUP_A);
        eventBus().dispatch(EVENT, NO_KEYS).block();

        getSpeedProfile().longWaitCondition()
            .untilAsserted(() -> assertThat(deadLetter().failedIds(GROUP_A)
                .flatMap(insertionId -> deadLetter().failedEvent(GROUP_A, insertionId))
                .toIterable())
            .containsOnly(EVENT));
        assertThat(eventCollector.getEvents())
            .isEmpty();
    }

    @Test
    default void deadLetterShouldStoreWhenRedeliverFailsGreaterThanMaxRetries() {
        EventCollector eventCollector = eventCollector();

        //do throw  RetryBackoffConfiguration.DEFAULT.DEFAULT_MAX_RETRIES + 1 times
        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, GROUP_A);
        eventBus().reDeliver(GROUP_A, EVENT).block();

        getSpeedProfile().longWaitCondition()
            .untilAsserted(() ->
                assertThat(
                        deadLetter()
                            .failedIds(GROUP_A)
                            .flatMap(insertionId -> deadLetter().failedEvent(GROUP_A, insertionId))
                            .toIterable())
                .containsOnly(EVENT));
        assertThat(eventCollector.getEvents()).isEmpty();
    }

    @Disabled("JAMES-2907 redeliver should work as initial dispatch")
    @Test
    default void retryShouldDeliverAsManyTimesAsInitialDeliveryAttempt() {
        EventCollector eventCollector = eventCollector();

        //do throw  RetryBackoffConfiguration.DEFAULT.DEFAULT_MAX_RETRIES + 1 times
        doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doThrow(new RuntimeException())
            .doCallRealMethod()
            .when(eventCollector).event(EVENT);

        eventBus().register(eventCollector, GROUP_A);
        eventBus().reDeliver(GROUP_A, EVENT).block();

        getSpeedProfile().longWaitCondition()
            .untilAsserted(() -> assertThat(eventCollector.getEvents()).isNotEmpty());
    }

    @Test
    default void redeliverShouldNotSendEventsToKeyListeners() {
        EventCollector eventCollector = eventCollector();
        EventCollector eventCollector2 = eventCollector();

        eventBus().register(eventCollector, GROUP_A);
        Mono.from(eventBus().register(eventCollector2, KEY_1)).block();
        eventBus().reDeliver(GROUP_A, EVENT).block();

        getSpeedProfile().longWaitCondition()
            .untilAsserted(() -> assertThat(eventCollector.getEvents()).hasSize(1));
        assertThat(eventCollector2.getEvents()).isEmpty();
    }
}
