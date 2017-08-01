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
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;

public class DefaultDelegatingMailboxListenerTest {

    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");
    private static final MailboxPath OTHER_MAILBOX_PATH = new MailboxPath("namespace", "other", "name");

    private DefaultDelegatingMailboxListener defaultDelegatingMailboxListener;
    private EventCollector mailboxEventCollector;
    private EventCollector eachNodeEventCollector;
    private EventCollector onceEventCollector;

    @Before
    public void setUp() throws Exception {
        mailboxEventCollector = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        eachNodeEventCollector = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        onceEventCollector = new EventCollector(MailboxListener.ListenerType.ONCE);
        defaultDelegatingMailboxListener = new DefaultDelegatingMailboxListener();
        defaultDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxEventCollector, null);
        defaultDelegatingMailboxListener.addGlobalListener(onceEventCollector, null);
        defaultDelegatingMailboxListener.addGlobalListener(eachNodeEventCollector, null);
    }

    @Test(expected = MailboxException.class)
    public void addListenerShouldThrowOnEACH_NODEListenerType() throws Exception {
        MailboxListener mailboxListener = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        defaultDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxListener, null);
    }

    @Test(expected = MailboxException.class)
    public void addListenerShouldThrowOnONCEListenerType() throws Exception {
        MailboxListener mailboxListener = new EventCollector(MailboxListener.ListenerType.ONCE);
        defaultDelegatingMailboxListener.addListener(MAILBOX_PATH, mailboxListener, null);
    }

    @Test(expected = MailboxException.class)
    public void addGlobalListenerShouldThrowOnMAILBOXListenerType() throws Exception {
        MailboxListener mailboxListener = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        defaultDelegatingMailboxListener.addGlobalListener(mailboxListener, null);
    }

    @Test
    public void eventShouldWork() throws Exception {
        MailboxListener.Event event = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void eventShouldOnlyTriggerMAILBOXListenerRelatedToTheEvent() throws Exception {
        MailboxListener.Event event = new MailboxListener.Event(null, OTHER_MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).isEmpty();
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void mailboxRenamedEventShouldUnregisterMAILBOXFromTheirPreviousPath() throws Exception {
        MailboxListener.MailboxRenamed event = new MailboxListener.MailboxRenamed(null, MAILBOX_PATH) {
            @Override
            public MailboxPath getNewPath() {
                return OTHER_MAILBOX_PATH;
            }
        };
        defaultDelegatingMailboxListener.event(event);
        MailboxListener.Event secondEvent = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(secondEvent);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(onceEventCollector.getEvents()).containsExactly(event, secondEvent);
    }

    @Test
    public void mailboxRenamedEventShouldRegisterMAILBOXToTheirNewPath() throws Exception {
        MailboxListener.MailboxRenamed event = new MailboxListener.MailboxRenamed(null, MAILBOX_PATH) {
            @Override
            public MailboxPath getNewPath() {
                return OTHER_MAILBOX_PATH;
            }
        };
        defaultDelegatingMailboxListener.event(event);
        MailboxListener.Event secondEvent = new MailboxListener.Event(null, OTHER_MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(secondEvent);
        assertThat(mailboxEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(eachNodeEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(onceEventCollector.getEvents()).containsExactly(event, secondEvent);
    }

    @Test
    public void mailboxDeletionShouldUnregisterMAILBOXListeners() throws Exception {
        MailboxListener.MailboxDeletion event = new MailboxListener.MailboxDeletion(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        MailboxListener.Event secondEvent = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(secondEvent);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(onceEventCollector.getEvents()).containsExactly(event, secondEvent);
    }

    @Test
    public void mailboxDeletionShouldNotRegisterMAILBOXListenerToOtherPaths() throws Exception {
        MailboxListener.MailboxDeletion event = new MailboxListener.MailboxDeletion(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        MailboxListener.Event secondEvent = new MailboxListener.Event(null, OTHER_MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(secondEvent);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(onceEventCollector.getEvents()).containsExactly(event, secondEvent);
    }

    @Test
    public void removeListenerShouldWork() throws Exception {
        defaultDelegatingMailboxListener.removeListener(MAILBOX_PATH, mailboxEventCollector, null);
        MailboxListener.Event event = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).isEmpty();
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void removeListenerShouldNotRemoveAListenerFromADifferentPath() throws Exception {
        defaultDelegatingMailboxListener.removeListener(OTHER_MAILBOX_PATH, mailboxEventCollector, null);
        MailboxListener.Event event = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void removeGlobalListenerShouldWorkForONCE() throws Exception {
        defaultDelegatingMailboxListener.removeGlobalListener(eachNodeEventCollector, null);
        MailboxListener.Event event = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).isEmpty();
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void removeGlobalListenerShouldWorkForEACH_NODE() throws Exception {
        defaultDelegatingMailboxListener.removeGlobalListener(onceEventCollector, null);
        MailboxListener.Event event = new MailboxListener.Event(null, MAILBOX_PATH) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).isEmpty();
    }

    @Test
    public void listenersErrorsShouldNotBePropageted() throws Exception {
        MailboxSession session = new MockMailboxSession("benwa");
        MailboxListener.Event event = new MailboxListener.Event(session, MAILBOX_PATH) {};
        MailboxListener mockedListener = mock(MailboxListener.class);
        when(mockedListener.getType()).thenReturn(MailboxListener.ListenerType.ONCE);
        doThrow(new RuntimeException()).when(mockedListener).event(event);

        defaultDelegatingMailboxListener.addGlobalListener(mockedListener, null);

        defaultDelegatingMailboxListener.event(event);
    }

}
