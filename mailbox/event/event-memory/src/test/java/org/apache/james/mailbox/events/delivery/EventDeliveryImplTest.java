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

import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class EventDeliveryImplTest {
    private static final int DELIVERY_DELAY = (int) TimeUnit.MILLISECONDS.toMillis(100);

    private EventDeliveryImpl eventDeliveryImpl;
    private MailboxListener listener;
    private MailboxListener listener2;
    private MailboxListener.MailboxEvent event;

    @BeforeEach
    void setUp() {
        event = mock(MailboxListener.MailboxEvent.class);
        listener = mock(MailboxListener.class);
        listener2 = mock(MailboxListener.class);
        eventDeliveryImpl = new EventDeliveryImpl(new NoopMetricFactory());
    }

    @Test
    void deliverShouldHaveCalledSynchronousListenersWhenAllListenerExecutedJoined() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);

        eventDeliveryImpl.deliver(ImmutableList.of(listener), event).allListenerFuture().join();

        verify(listener).event(event);
    }

    @Test
    void deliverShouldHaveCalledAsynchronousListenersWhenAllListenerExecutedJoined() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);

        eventDeliveryImpl.deliver(ImmutableList.of(listener), event).allListenerFuture().join();

        verify(listener).event(event);
    }

    @Test
    void deliverShouldHaveCalledSynchronousListenersWhenSynchronousListenerExecutedJoined() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);

        eventDeliveryImpl.deliver(ImmutableList.of(listener), event).synchronousListenerFuture().join();

        verify(listener).event(event);
    }

    @Test
    void deliverShouldNotBlockObAsynchronousListenersWhenSynchronousListenerExecutedJoined() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.await();
            return null;
        }).when(listener).event(event);

        assertTimeout(Duration.ofSeconds(2),
            () -> {
                eventDeliveryImpl.deliver(ImmutableList.of(listener), event).synchronousListenerFuture().join();
                latch.countDown();
            });
    }

    @Test
    void deliverShouldNotBlockOnSynchronousListenersWhenNoJoin() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.await();
            return null;
        }).when(listener).event(event);

        assertTimeout(Duration.ofSeconds(2),
            () -> {
                eventDeliveryImpl.deliver(ImmutableList.of(listener), event);
                latch.countDown();
            });
    }

    @Test
    void deliverShouldNotBlockOnAsynchronousListenersWhenNoJoin() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.await();
            return null;
        }).when(listener).event(event);

        assertTimeout(Duration.ofSeconds(2),
            () -> {
                eventDeliveryImpl.deliver(ImmutableList.of(listener), event);
                latch.countDown();
            });
    }

    @Test
    void deliverShouldEventuallyDeliverAsynchronousListenersWhenSynchronousListenerExecutedJoined() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);

        eventDeliveryImpl.deliver(ImmutableList.of(listener), event).synchronousListenerFuture().join();

        verify(listener, timeout(DELIVERY_DELAY * 10)).event(event);
    }

    @Test
    void deliverShouldEventuallyDeliverSynchronousListenersWhenNoJoin() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);

        eventDeliveryImpl.deliver(ImmutableList.of(listener), event);

        verify(listener, timeout(DELIVERY_DELAY * 10)).event(event);
    }

    @Test
    void deliverShouldCallSynchronousListenersWhenAsynchronousListenersAreAlsoRegistered() {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        when(listener2.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);

        eventDeliveryImpl.deliver(ImmutableList.of(listener, listener2), event).synchronousListenerFuture().join();

        verify(listener2).event(event);
    }
}
