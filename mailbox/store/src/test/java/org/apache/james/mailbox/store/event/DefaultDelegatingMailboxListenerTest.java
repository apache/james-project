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

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.util.EventCollector;
import org.junit.Before;
import org.junit.Test;

public class DefaultDelegatingMailboxListenerTest {

    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");
    private static final MailboxPath OTHER_MAILBOX_PATH = new MailboxPath("namespace", "other", "name");
    private static final MailboxId MAILBOX_ID = TestId.of(100);
    private static final MailboxId OTHER_MAILBOX_ID = TestId.of(42);

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
        defaultDelegatingMailboxListener.addListener(MAILBOX_ID, mailboxEventCollector, null);
        defaultDelegatingMailboxListener.addGlobalListener(onceEventCollector, null);
        defaultDelegatingMailboxListener.addGlobalListener(eachNodeEventCollector, null);
    }

    @Test(expected = MailboxException.class)
    public void addListenerShouldThrowOnEACH_NODEListenerType() throws Exception {
        MailboxListener mailboxListener = new EventCollector(MailboxListener.ListenerType.EACH_NODE);
        defaultDelegatingMailboxListener.addListener(MAILBOX_ID, mailboxListener, null);
    }

    @Test(expected = MailboxException.class)
    public void addListenerShouldThrowOnONCEListenerType() throws Exception {
        MailboxListener mailboxListener = new EventCollector(MailboxListener.ListenerType.ONCE);
        defaultDelegatingMailboxListener.addListener(MAILBOX_ID, mailboxListener, null);
    }

    @Test(expected = MailboxException.class)
    public void addGlobalListenerShouldThrowOnMAILBOXListenerType() throws Exception {
        MailboxListener mailboxListener = new EventCollector(MailboxListener.ListenerType.MAILBOX);
        defaultDelegatingMailboxListener.addGlobalListener(mailboxListener, null);
    }

    @Test
    public void eventShouldWork() {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null, MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void eventShouldOnlyTriggerMAILBOXListenerRelatedToTheEvent() {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null, OTHER_MAILBOX_PATH, OTHER_MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).isEmpty();
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void mailboxDeletionShouldUnregisterMAILBOXListeners() {
        QuotaRoot quotaRoot = QuotaRoot.quotaRoot("root", null);
        QuotaCount deletedMessageCount = QuotaCount.count(123);
        QuotaSize totalDeletedSize = QuotaSize.size(456);
        MailboxListener.MailboxDeletion event = new MailboxListener.MailboxDeletion(null, null, MAILBOX_PATH, quotaRoot, deletedMessageCount, totalDeletedSize, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        MailboxListener.MailboxEvent secondEvent = new MailboxListener.MailboxEvent(null, null, MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(secondEvent);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(onceEventCollector.getEvents()).containsExactly(event, secondEvent);
    }

    @Test
    public void mailboxDeletionShouldNotRegisterMAILBOXListenerToOtherPaths() {
        QuotaRoot quotaRoot = QuotaRoot.quotaRoot("root", null);
        QuotaCount quotaCount = QuotaCount.count(123);
        QuotaSize quotaSize = QuotaSize.size(456);
        MailboxListener.MailboxDeletion event = new MailboxListener.MailboxDeletion(null, null, MAILBOX_PATH, quotaRoot, quotaCount, quotaSize, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        MailboxListener.MailboxEvent secondEvent = new MailboxListener.MailboxEvent(null, null, OTHER_MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(secondEvent);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsOnly(event, secondEvent);
        assertThat(onceEventCollector.getEvents()).containsExactly(event, secondEvent);
    }

    @Test
    public void removeListenerShouldWork() {
        defaultDelegatingMailboxListener.removeListener(MAILBOX_ID, mailboxEventCollector, null);
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null, MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).isEmpty();
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void removeListenerShouldNotRemoveAListenerFromADifferentPath() {
        defaultDelegatingMailboxListener.removeListener(OTHER_MAILBOX_ID, mailboxEventCollector, null);
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null, MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void removeGlobalListenerShouldWorkForONCE() {
        defaultDelegatingMailboxListener.removeGlobalListener(eachNodeEventCollector, null);
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null, MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).isEmpty();
        assertThat(onceEventCollector.getEvents()).containsExactly(event);
    }

    @Test
    public void removeGlobalListenerShouldWorkForEACH_NODE() throws Exception {
        defaultDelegatingMailboxListener.removeGlobalListener(onceEventCollector, null);
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(null, null, MAILBOX_PATH, MAILBOX_ID) {};
        defaultDelegatingMailboxListener.event(event);
        assertThat(mailboxEventCollector.getEvents()).containsExactly(event);
        assertThat(eachNodeEventCollector.getEvents()).containsExactly(event);
        assertThat(onceEventCollector.getEvents()).isEmpty();
    }

    @Test
    public void listenersErrorsShouldNotBePropageted() throws Exception {
        MailboxSession session = MailboxSession.create("benwa");
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(session.getSessionId(),
            session.getUser(), MAILBOX_PATH, MAILBOX_ID) {};
        MailboxListener mockedListener = mock(MailboxListener.class);
        when(mockedListener.getType()).thenReturn(MailboxListener.ListenerType.ONCE);
        doThrow(new RuntimeException()).when(mockedListener).event(event);

        defaultDelegatingMailboxListener.addGlobalListener(mockedListener, null);

        defaultDelegatingMailboxListener.event(event);
    }

}
