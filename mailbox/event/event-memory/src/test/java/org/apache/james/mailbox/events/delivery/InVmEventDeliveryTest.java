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

package org.apache.james.mailbox.events.delivery;

import static org.apache.james.mailbox.events.EventBusTestFixture.EVENT;
import static org.apache.james.mailbox.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.mailbox.events.EventBusTestFixture.MailboxListenerCountingSuccessfulExecution;
import static org.apache.james.mailbox.events.delivery.EventDelivery.Retryer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.delivery.EventDelivery.DeliveryOption;
import org.apache.james.mailbox.events.delivery.EventDelivery.PermanentFailureHandler;
import org.apache.james.mailbox.events.delivery.EventDelivery.Retryer.BackoffRetryer;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InVmEventDeliveryTest {
    private InVmEventDelivery inVmEventDelivery;
    private MailboxListenerCountingSuccessfulExecution listener;

    @BeforeEach
    void setUp() {
        listener = newListener();
        inVmEventDelivery = new InVmEventDelivery(new NoopMetricFactory());
    }

    MailboxListenerCountingSuccessfulExecution newListener() {
        return spy(new MailboxListenerCountingSuccessfulExecution());
    }

    @Nested
    class SynchronousListener {

        @Test
        void deliverShouldDeliverEvent() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
            inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                .block();

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(1);
        }

        @Test
        void deliverShouldReturnSuccessSynchronousMono() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
            assertThatCode(() -> inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                    .block())
                .doesNotThrowAnyException();
        }

        @Test
        void deliverShouldNotDeliverWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EVENT);

            assertThatThrownBy(() -> inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                .block())
            .isInstanceOf(RuntimeException.class);

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(0);
        }

        @Test
        void deliverShouldReturnAnErrorMonoWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EVENT);

            assertThatThrownBy(() -> inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                .block())
            .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class AsynchronousListener {

        @Test
        void deliverShouldDeliverEvent() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
            inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                .block();

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(1);
        }

        @Test
        void deliverShouldReturnSuccessSynchronousMono() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
            assertThatCode(() -> inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                    .block())
                .doesNotThrowAnyException();
        }

        @Test
        void deliverShouldNotFailWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EVENT);

            assertThatCode(() -> inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                .block())
            .doesNotThrowAnyException();
        }

        @Test
        void deliverShouldReturnAnSuccessSyncMonoWhenListenerGetException() {
            when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
            doThrow(new RuntimeException())
                .when(listener).event(EVENT);

            assertThatCode(() -> inVmEventDelivery.deliver(listener, EVENT, DeliveryOption.none())
                .block())
            .doesNotThrowAnyException();
        }
    }

    @Nested
    class WithOptions {

        @Test
        void retryShouldWorkWhenDeliverWithRetry() {
            MailboxListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doCallRealMethod()
                .when(listener).event(EVENT);

            inVmEventDelivery.deliver(listener, EVENT,
                DeliveryOption.of(
                    BackoffRetryer.of(RetryBackoffConfiguration.DEFAULT, listener),
                    PermanentFailureHandler.NO_HANDLER))
                .block();

            assertThat(listener.numberOfEventCalls())
                .isEqualTo(1);
        }

        @Test
        void failureHandlerShouldWorkWhenDeliverWithFailureHandler() {
            MailboxListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .when(listener).event(EVENT);

            MemoryEventDeadLetters deadLetter = new MemoryEventDeadLetters();

            inVmEventDelivery.deliver(listener, EVENT,
                DeliveryOption.of(
                    Retryer.NO_RETRYER,
                    PermanentFailureHandler.StoreToDeadLetters.of(GROUP_A, deadLetter)))
                .block();

            assertThat(deadLetter.groupsWithFailedEvents().toStream())
                .containsOnly(GROUP_A);
        }

        @Test
        void failureHandlerShouldNotWorkWhenRetrySuccess() {
            MailboxListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doCallRealMethod()
                .when(listener).event(EVENT);

            MemoryEventDeadLetters deadLetter = new MemoryEventDeadLetters();

            inVmEventDelivery.deliver(listener, EVENT,
                DeliveryOption.of(
                    BackoffRetryer.of(RetryBackoffConfiguration.DEFAULT, listener),
                    PermanentFailureHandler.StoreToDeadLetters.of(GROUP_A, deadLetter)))
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
            MailboxListenerCountingSuccessfulExecution listener = newListener();
            doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doThrow(new RuntimeException())
                .doCallRealMethod()
                .when(listener).event(EVENT);

            MemoryEventDeadLetters deadLetter = new MemoryEventDeadLetters();

            inVmEventDelivery.deliver(listener, EVENT,
                DeliveryOption.of(
                    BackoffRetryer.of(RetryBackoffConfiguration.DEFAULT, listener),
                    PermanentFailureHandler.StoreToDeadLetters.of(GROUP_A, deadLetter)))
                .block();

            SoftAssertions.assertSoftly(softy -> {
                softy.assertThat(listener.numberOfEventCalls())
                    .isEqualTo(0);
                assertThat(deadLetter.groupsWithFailedEvents().toStream())
                    .containsOnly(GROUP_A);
            });
        }
    }
}
