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
import static org.mockito.Mockito.verify;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.junit.Before;
import org.junit.Test;

public class SynchronousEventDeliveryTest {

    private MailboxListener mailboxListener;
    private SynchronousEventDelivery synchronousEventDelivery;

    @Before
    public void setUp() {
        mailboxListener = mock(MailboxListener.class);
        synchronousEventDelivery = new SynchronousEventDelivery();
    }

    @Test
    public void deliverShouldWork() {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null) {};
        synchronousEventDelivery.deliver(mailboxListener, event);
        verify(mailboxListener).event(event);
    }

    @Test
    public void deliverShouldNotPropagateException() {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(new MockMailboxSession("test"), null) {};
        doThrow(new RuntimeException()).when(mailboxListener).event(event);
        synchronousEventDelivery.deliver(mailboxListener, event);
        verify(mailboxListener).event(event);
    }

}
