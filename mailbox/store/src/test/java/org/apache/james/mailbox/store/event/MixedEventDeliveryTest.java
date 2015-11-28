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

import org.apache.james.mailbox.MailboxListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;



public class MixedEventDeliveryTest {

    private MixedEventDelivery mixedEventDelivery;

    @Before
    public void setUp() {
        mixedEventDelivery = new MixedEventDelivery(new AsynchronousEventDelivery(2), new SynchronousEventDelivery());
    }

    @After
    public void tearDown() {
        mixedEventDelivery.stop();
    }

    @Test
    public void deliverShouldWorkOnSynchronousListeners() throws Exception {
        WaitMailboxListener listener = new WaitMailboxListener(MailboxListener.ExecutionMode.SYNCHRONOUS);
        MailboxListener.Event event = new MailboxListener.Event(null, null) {};
        mixedEventDelivery.deliver(listener, event);
        assertThat(listener.getInvocationCount().get()).isEqualTo(1);
    }

    @Test
    public void deliverShouldWorkOnAsynchronousListeners() throws Exception {
        WaitMailboxListener listener = new WaitMailboxListener(MailboxListener.ExecutionMode.ASYNCHRONOUS);
        MailboxListener.Event event = new MailboxListener.Event(null, null) {};
        mixedEventDelivery.deliver(listener, event);
        assertThat(listener.getInvocationCount().get()).isEqualTo(0);
        Thread.sleep(200);
        assertThat(listener.getInvocationCount().get()).isEqualTo(1);
    }

}
