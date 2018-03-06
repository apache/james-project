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

package org.apache.james.mailbox.store.event;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.MailboxListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class MixedEventDeliveryTest {

    private static final int DELIVERY_DELAY = (int) TimeUnit.MILLISECONDS.toMillis(100);
    private static final long ONE_MINUTE = 60000;
    private MixedEventDelivery mixedEventDelivery;
    private MailboxListener listener;

    @Before
    public void setUp() {
        listener = mock(MailboxListener.class);
        mixedEventDelivery = new MixedEventDelivery(new AsynchronousEventDelivery(2), new SynchronousEventDelivery());
    }

    @After
    public void tearDown() {
        mixedEventDelivery.stop();
    }

    @Test
    public void deliverShouldWorkOnSynchronousListeners() throws Exception {
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.SYNCHRONOUS);
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null) {};
        mixedEventDelivery.deliver(listener, event);
        verify(listener).event(event);
    }

    @Test
    public void deliverShouldEventuallyDeliverOnAsynchronousListeners() throws Exception {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null) {};
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        mixedEventDelivery.deliver(listener, event);
        verify(listener, timeout(DELIVERY_DELAY * 10)).event(event);
    }

    @Test(timeout = ONE_MINUTE)
    public void deliverShouldNotBlockOnAsynchronousListeners() throws Exception {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null) {};
        when(listener.getExecutionMode()).thenReturn(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.await();
            return null;
        }).when(listener).event(event);
        mixedEventDelivery.deliver(listener, event);
        latch.countDown();
    }

}
