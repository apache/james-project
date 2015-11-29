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

package org.apache.james.mailbox.indexer.registrations;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Optional;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

public class GlobalRegistrationTest {

    public static final MockMailboxSession SESSION = new MockMailboxSession("test");
    public static final MailboxPath INBOX = new MailboxPath("#private", "btellier@apache.org", "INBOX");
    public static final MailboxPath NEW_PATH = new MailboxPath("#private", "btellier@apache.org", "INBOX.new");
    public static final int UID_VALIDITY = 45;
    public static final SimpleMailbox<TestId> MAILBOX = new SimpleMailbox<TestId>(INBOX, UID_VALIDITY);
    public static final SimpleMailbox<TestId> NEW_MAILBOX = new SimpleMailbox<TestId>(NEW_PATH, UID_VALIDITY);

    private GlobalRegistration globalRegistration;
    private EventFactory<TestId> eventFactory;

    @Before
    public void setUp() {
        eventFactory = new EventFactory<TestId>();
        globalRegistration = new GlobalRegistration();
    }

    @Test
    public void pathToIndexShouldNotBeChangedByDefault() {
        assertThat(globalRegistration.getPathToIndex(INBOX).get()).isEqualTo(INBOX);
    }

    @Test
    public void pathToIndexShouldNotBeChangedByAddedEvents() {
        MailboxListener.Event event = eventFactory.mailboxAdded(SESSION, MAILBOX);
        globalRegistration.event(event);
        assertThat(globalRegistration.getPathToIndex(INBOX).get()).isEqualTo(INBOX);
    }

    @Test
    public void pathToIndexShouldBeNullifiedByDeletedEvents() {
        MailboxListener.Event event = eventFactory.mailboxDeleted(SESSION, MAILBOX);
        globalRegistration.event(event);
        assertThat(globalRegistration.getPathToIndex(INBOX)).isEqualTo(Optional.absent());
    }

    @Test
    public void pathToIndexShouldBeModifiedByRenamedEvents() {
        MailboxListener.Event event = eventFactory.mailboxRenamed(SESSION, INBOX, NEW_MAILBOX);
        globalRegistration.event(event);
        assertThat(globalRegistration.getPathToIndex(INBOX).get()).isEqualTo(NEW_PATH);
    }


}
