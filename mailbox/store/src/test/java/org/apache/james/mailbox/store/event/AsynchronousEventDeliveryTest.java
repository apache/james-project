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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AsynchronousEventDeliveryTest {

    private static final int ONE_MINUTE = (int) TimeUnit.MINUTES.toMillis(1);
    private MailboxListener mailboxListener;
    private AsynchronousEventDelivery asynchronousEventDelivery;

    @Before
    public void setUp() {
        mailboxListener = mock(MailboxListener.class);
        asynchronousEventDelivery = new AsynchronousEventDelivery(2,
            new SynchronousEventDelivery(new NoopMetricFactory()));
    }

    @After
    public void tearDown() {
        asynchronousEventDelivery.stop();
    }

    @Test
    public void deliverShouldWork() throws Exception {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null) {};
        asynchronousEventDelivery.deliver(mailboxListener, event);
        verify(mailboxListener, timeout(ONE_MINUTE)).event(event);
    }

    @Test
    public void deliverShouldNotPropagateException() throws Exception {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(new MockMailboxSession("test"), null) {};
        doThrow(new RuntimeException()).when(mailboxListener).event(event);
        asynchronousEventDelivery.deliver(mailboxListener, event);
        verify(mailboxListener, timeout(ONE_MINUTE)).event(event);
    }

    @Test
    public void deliverShouldWorkWhenThePoolIsFull() throws Exception {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(new MockMailboxSession("test"), null) {};
        int operationCount = 10;
        for (int i = 0; i < operationCount; i++) {
            asynchronousEventDelivery.deliver(mailboxListener, event);
        }
        verify(mailboxListener, timeout(ONE_MINUTE).times(operationCount)).event(event);
    }

}
