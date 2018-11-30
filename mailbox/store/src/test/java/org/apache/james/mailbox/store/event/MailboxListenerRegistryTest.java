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
import static org.mockito.Mockito.mock;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.TestId;
import org.junit.Before;
import org.junit.Test;

public class MailboxListenerRegistryTest {
    private static final MailboxId MAILBOX_ID = TestId.of(42);
    private static final MailboxId OTHER_MAILBOX_ID = TestId.of(43);

    private MailboxListenerRegistry testee;
    private MailboxListener mailboxListener;
    private MailboxListener otherMailboxListener;

    @Before
    public void setUp() {
        testee = new MailboxListenerRegistry();
        mailboxListener = mock(MailboxListener.class);
        otherMailboxListener = mock(MailboxListener.class);
    }

    @Test
    public void getGlobalListenersShouldBeEmpty() {
        assertThat(testee.getGlobalListeners()).isEmpty();
    }

    @Test
    public void getLocalMailboxListenersShouldReturnEmptyList() {
        assertThat(testee.getLocalMailboxListeners(MAILBOX_ID)).isEmpty();
    }

    @Test
    public void addGlobalListenerShouldAddAGlobalListener() {
        testee.addGlobalListener(mailboxListener);

        assertThat(testee.getGlobalListeners()).containsOnly(mailboxListener);
    }

    @Test
    public void addListenerShouldAddAListener() {
        testee.addListener(MAILBOX_ID, mailboxListener);

        assertThat(testee.getLocalMailboxListeners(MAILBOX_ID)).containsOnly(mailboxListener);
    }

    @Test
    public void addListenerTwiceShouldAddAListenerOnlyOnce() {
        testee.addListener(MAILBOX_ID, mailboxListener);
        testee.addListener(MAILBOX_ID, mailboxListener);

        assertThat(testee.getLocalMailboxListeners(MAILBOX_ID)).containsExactly(mailboxListener);
    }

    @Test
    public void addListenerShouldAddAListenerOnCorrectPath() {
        testee.addListener(MAILBOX_ID, mailboxListener);

        assertThat(testee.getLocalMailboxListeners(OTHER_MAILBOX_ID)).isEmpty();
    }

    @Test
    public void removeGlobalListenerShouldWork() throws Exception {
        testee.addGlobalListener(mailboxListener);

        testee.removeGlobalListener(mailboxListener);

        assertThat(testee.getGlobalListeners()).isEmpty();
    }

    @Test
    public void removeListenerShouldWork() {
        testee.addListener(MAILBOX_ID, mailboxListener);

        testee.removeListener(MAILBOX_ID, mailboxListener);

        assertThat(testee.getLocalMailboxListeners(MAILBOX_ID)).isEmpty();
    }

    @Test
    public void removeGlobalListenerShouldNotRemoveOtherListeners() {
        testee.addGlobalListener(mailboxListener);
        testee.addGlobalListener(otherMailboxListener);

        testee.removeGlobalListener(mailboxListener);

        assertThat(testee.getGlobalListeners()).containsOnly(otherMailboxListener);
    }

    @Test
    public void removeListenerShouldNotRemoveOtherListeners() {
        testee.addListener(MAILBOX_ID, mailboxListener);
        testee.addListener(MAILBOX_ID, otherMailboxListener);

        testee.removeListener(MAILBOX_ID, mailboxListener);

        assertThat(testee.getLocalMailboxListeners(MAILBOX_ID)).containsOnly(otherMailboxListener);
    }

    @Test
    public void deleteRegistryForShouldRemoveAllListeners() {
        testee.addListener(MAILBOX_ID, mailboxListener);
        testee.addListener(MAILBOX_ID, otherMailboxListener);

        testee.deleteRegistryFor(MAILBOX_ID);

        assertThat(testee.getLocalMailboxListeners(MAILBOX_ID)).isEmpty();
    }

    @Test
    public void removeGlobalListenerShouldNotThrowOnAbsentListener() {
        testee.removeGlobalListener(mailboxListener);
    }

    @Test
    public void removeListenerShouldNotThrowOnAbsentListener() {
        testee.removeListener(MAILBOX_ID, mailboxListener);
    }
}
