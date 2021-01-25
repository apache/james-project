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

package org.apache.james.events.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.EventListener;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InVmEventDeliveryTest {
    private InVmEventDelivery inVmEventDelivery;
    private EventBusTestFixture.EventListenerCountingSuccessfulExecution listener;

    @BeforeEach
    void setUp() {
        listener = newListener();
        inVmEventDelivery = new InVmEventDelivery(new RecordingMetricFactory());
    }

    EventBusTestFixture.EventListenerCountingSuccessfulExecution newListener() {
        return Mockito.spy(new EventBusTestFixture.EventListenerCountingSuccessfulExecution());
    }

    @Nested
    class SynchronousListener {

        @Test
        void deliverShouldDeliverEvent() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
            inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                .block();

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(1);
        }

        @Test
        void deliverShouldReturnSuccessSynchronousMono() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
            assertThatCode(() -> inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                    .block())
                .doesNotThrowAnyException();
        }

        @Test
        void deliverShouldNotDeliverWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EventBusTestFixture.EVENT);

            assertThatThrownBy(() -> inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                .block())
            .isInstanceOf(RuntimeException.class);

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(0);
        }

        @Test
        void deliverShouldReturnAnErrorMonoWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EventBusTestFixture.EVENT);

            assertThatThrownBy(() -> inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                .block())
            .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class AsynchronousListener {

        @Test
        void deliverShouldDeliverEvent() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);
            inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                .block();

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(1);
        }

        @Test
        void deliverShouldReturnSuccessSynchronousMono() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);
            assertThatCode(() -> inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                    .block())
                .doesNotThrowAnyException();
        }

        @Test
        void deliverShouldNotFailWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EventBusTestFixture.EVENT);

            assertThatCode(() -> inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                .block())
            .doesNotThrowAnyException();
        }

        @Test
        void deliverShouldReturnAnSuccessSyncMonoWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EventBusTestFixture.EVENT);

            assertThatCode(() -> inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT, EventDelivery.DeliveryOption.none())
                .block())
            .doesNotThrowAnyException();
        }
    }

    @Nested
    class WithOptions {

        @Test
        void retryShouldWorkWhenDeliverWithRetry() {
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doCallRealMethod()
                .when(listener).event(EventBusTestFixture.EVENT);

            inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT,
                EventDelivery.DeliveryOption.of(
                    EventDelivery.Retryer.BackoffRetryer.of(RetryBackoffConfiguration.DEFAULT, listener),
                    EventDelivery.PermanentFailureHandler.NO_HANDLER))
                .block();

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(1);
        }

        @Test
        void failureHandlerShouldWorkWhenDeliverWithFailureHandler() {
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .when(listener).event(EventBusTestFixture.EVENT);

            MemoryEventDeadLetters deadLetter = new MemoryEventDeadLetters();

            inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT,
                EventDelivery.DeliveryOption.of(
                    EventDelivery.Retryer.NO_RETRYER,
                    EventDelivery.PermanentFailureHandler.StoreToDeadLetters.of(EventBusTestFixture.GROUP_A, deadLetter)))
                .block();

            assertThat(deadLetter.groupsWithFailedEvents().toStream())
                .containsOnly(EventBusTestFixture.GROUP_A);
        }

        @Test
        void failureHandlerShouldNotWorkWhenRetrySuccess() {
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doCallRealMethod()
                .when(listener).event(EventBusTestFixture.EVENT);

            MemoryEventDeadLetters deadLetter = new MemoryEventDeadLetters();

            inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT,
                EventDelivery.DeliveryOption.of(
                    EventDelivery.Retryer.BackoffRetryer.of(RetryBackoffConfiguration.DEFAULT, listener),
                    EventDelivery.PermanentFailureHandler.StoreToDeadLetters.of(EventBusTestFixture.GROUP_A, deadLetter)))
                .block();

            SoftAssertions.assertSoftly(softy -> {
                softy.assertThat(listener.numberOfEventCalls())
                    .isEqualTo(1);
                softy.assertThat(deadLetter.groupsWithFailedEvents().toStream())
                    .isEmpty();
            });
        }


        @Test
        void failureHandlerShouldWorkWhenRetryFails() {
            EventBusTestFixture.EventListenerCountingSuccessfulExecution listener = newListener();
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
                .when(listener).event(EventBusTestFixture.EVENT);

            MemoryEventDeadLetters deadLetter = new MemoryEventDeadLetters();

            inVmEventDelivery.deliver(listener, EventBusTestFixture.EVENT,
                EventDelivery.DeliveryOption.of(
                    EventDelivery.Retryer.BackoffRetryer.of(RetryBackoffConfiguration.builder()
                            .maxRetries(8)
                            .firstBackoff(Duration.ofMillis(1))
                            .jitterFactor(0.2)
                            .build(), listener),
                    EventDelivery.PermanentFailureHandler.StoreToDeadLetters.of(EventBusTestFixture.GROUP_A, deadLetter)))
                .block();

            SoftAssertions.assertSoftly(softy -> {
                softy.assertThat(listener.numberOfEventCalls())
                    .isEqualTo(0);
                assertThat(deadLetter.groupsWithFailedEvents().toStream())
                    .containsOnly(EventBusTestFixture.GROUP_A);
            });
        }
    }
}
