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

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class AsynchronousEventDeliveryTest {

    public class WaitMailboxListener implements MailboxListener {

        private final AtomicLong invocationCount;

        public WaitMailboxListener() {
            invocationCount = new AtomicLong(0);
        }

        public AtomicLong getInvocationCount() {
            return invocationCount;
        }

        @Override
        public ListenerType getType() {
            return ListenerType.MAILBOX;
        }

        @Override
        public void event(Event event) {
            try {
                Thread.sleep(100);
                invocationCount.incrementAndGet();
            } catch (InterruptedException e) {
                LOGGER.info("interrupted", e);
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AsynchronousEventDeliveryTest.class);

    private MailboxListener mailboxListener;
    private AsynchronousEventDelivery asynchronousEventDelivery;

    @Before
    public void setUp() {
        mailboxListener = mock(MailboxListener.class);
        asynchronousEventDelivery = new AsynchronousEventDelivery(2);
    }

    @After
    public void tearDown() {
        asynchronousEventDelivery.stop();
    }

    @Test
    public void deliverShouldWork() throws Exception {
        MailboxListener.Event event = new MailboxListener.Event(null, null) {};
        asynchronousEventDelivery.deliver(mailboxListener, event);
        Thread.sleep(100);
        verify(mailboxListener).event(event);
    }

    @Test
    public void deliverShouldNotPropagateException() throws Exception {
        MailboxListener.Event event = new MailboxListener.Event(new MockMailboxSession("test"), null) {};
        doThrow(new RuntimeException()).when(mailboxListener).event(event);
        asynchronousEventDelivery.deliver(mailboxListener, event);
        Thread.sleep(100);
        verify(mailboxListener).event(event);
    }

    @Test
    public void deliverShouldWorkWhenThePoolIsFull() throws Exception {
        MailboxListener.Event event = new MailboxListener.Event(new MockMailboxSession("test"), null) {};
        WaitMailboxListener waitMailboxListener = new WaitMailboxListener();
        long operationCount = 10;
        for (int i = 0; i < operationCount; i++) {
            asynchronousEventDelivery.deliver(waitMailboxListener, event);
        }
        Thread.sleep(2000);
        assertThat(waitMailboxListener.getInvocationCount().get()).isEqualTo(operationCount);
    }

}
