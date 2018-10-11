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

package org.apache.mailbox.tools.indexer.registrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GlobalRegistrationTest {
    public static final MailboxPath INBOX = MailboxPath.forUser("btellier@apache.org", "INBOX");
    private static final MailboxPath NEW_PATH = MailboxPath.forUser("btellier@apache.org", "INBOX.new");
    private static final int UID_VALIDITY = 45;
    private static final SimpleMailbox MAILBOX = new SimpleMailbox(INBOX, UID_VALIDITY);
    private static final SimpleMailbox NEW_MAILBOX = new SimpleMailbox(NEW_PATH, UID_VALIDITY);

    private GlobalRegistration globalRegistration;
    private EventFactory eventFactory;
    private MockMailboxSession session;

    @BeforeEach
    void setUp() {
        eventFactory = new EventFactory();
        session = new MockMailboxSession("test");
        globalRegistration = new GlobalRegistration();
    }

    @Test
    void pathToIndexShouldNotBeChangedByDefault() {
        assertThat(globalRegistration.getPathToIndex(INBOX).get()).isEqualTo(INBOX);
    }

    @Test
    void pathToIndexShouldNotBeChangedByAddedEvents() {
        MailboxListener.MailboxEvent event = eventFactory.mailboxAdded(session, MAILBOX);
        globalRegistration.event(event);
        assertThat(globalRegistration.getPathToIndex(INBOX).get()).isEqualTo(INBOX);
    }

    @Test
    void pathToIndexShouldBeNullifiedByDeletedEvents() {
        QuotaRoot quotaRoot = QuotaRoot.quotaRoot("root", Optional.empty());
        QuotaCount quotaCount = QuotaCount.count(123);
        QuotaSize quotaSize = QuotaSize.size(456);
        MailboxListener.MailboxEvent event = eventFactory.mailboxDeleted(session, MAILBOX, quotaRoot, quotaCount, quotaSize);
        globalRegistration.event(event);
        assertThat(globalRegistration.getPathToIndex(INBOX)).isEqualTo(Optional.empty());
    }

    @Test
    void pathToIndexShouldBeModifiedByRenamedEvents() {
        MailboxListener.MailboxEvent event = eventFactory.mailboxRenamed(session, INBOX, NEW_MAILBOX);
        globalRegistration.event(event);
        assertThat(globalRegistration.getPathToIndex(INBOX).get()).isEqualTo(NEW_PATH);
    }
}
