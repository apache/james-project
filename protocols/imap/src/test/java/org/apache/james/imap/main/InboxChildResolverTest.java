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

package org.apache.james.imap.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InboxChildResolverTest {
    private static final Username BOB = Username.of("bob");

    private InMemoryMailboxManager mailboxManager;
    private MailboxSession session;
    private InboxChildResolver testee;

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        session = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), session);
        testee = new InboxChildResolver(mailboxManager);
    }

    @Test
    void newMailboxShouldGoUnderInbox() {
        assertThat(testee.newMailbox(MailboxPath.forUser(BOB, "inbox.sub"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "INBOX.sub"));
    }

    @Test
    void existingMailboxShouldFindTheMailboxCreatedUnderInbox() throws Exception {
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "INBOX.sub"), session);

        assertThat(testee.existingMailbox(MailboxPath.forUser(BOB, "inbox.sub"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "INBOX.sub"));
    }

    @Test
    void existingMailboxShouldKeepAMailboxStoredWithAnotherCaseOfInbox() throws Exception {
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Inbox.Archive"), session);
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "INBOX.Archive"), session);

        assertThat(testee.existingMailbox(MailboxPath.forUser(BOB, "Inbox.Archive"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "Inbox.Archive"));
    }

    @Test
    void existingMailboxShouldNotDesignateAStoredMailboxByAnotherCaseOfInbox() throws Exception {
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Inbox.Archive"), session);

        assertThat(testee.existingMailbox(MailboxPath.forUser(BOB, "inbox.Archive"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "INBOX.Archive"));
    }

    @Test
    void newMailboxShouldGoUnderAMailboxStoredWithAnotherCaseOfInbox() throws Exception {
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Inbox.Archive"), session);

        assertThat(testee.newMailbox(MailboxPath.forUser(BOB, "Inbox.Archive.2025"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "Inbox.Archive.2025"));
    }

    @Test
    void newMailboxShouldDesignateTheStoredMailboxItself() throws Exception {
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Inbox.Archive"), session);

        assertThat(testee.newMailbox(MailboxPath.forUser(BOB, "Inbox.Archive"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "Inbox.Archive"));
    }

    @Test
    void namesOnlyStartingWithInboxShouldBeKept() {
        assertThat(testee.newMailbox(MailboxPath.forUser(BOB, "inboxes.sub"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "inboxes.sub"));
    }

    @Test
    void childrenOfInboxShouldBeKept() {
        assertThat(testee.existingMailbox(MailboxPath.forUser(BOB, "INBOX.sub"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "INBOX.sub"));
    }

    @Test
    void newMailboxShouldNotPutADotlessINameUnderInbox() {
        // U+0131 LATIN SMALL LETTER DOTLESS I: IMAP case-insensitivity is ASCII only
        assertThat(testee.newMailbox(MailboxPath.forUser(BOB, "ınbox.sub"), session).block())
            .isEqualTo(MailboxPath.forUser(BOB, "ınbox.sub"));
    }

    @Test
    void inboxChildShouldOnlyFoldAsciiCase() {
        // U+0130 LATIN CAPITAL LETTER I WITH DOT ABOVE
        assertThat(InboxChildResolver.inboxChild(MailboxPath.forUser(BOB, "İnbox.sub"), '.')).isEmpty();
        assertThat(InboxChildResolver.inboxChild(MailboxPath.forUser(BOB, "ınbox.sub"), '.')).isEmpty();
        assertThat(InboxChildResolver.inboxChild(MailboxPath.forUser(BOB, "inbox.sub"), '.'))
            .contains(MailboxPath.forUser(BOB, "INBOX.sub"));
    }

    @Test
    void inboxChildShouldIgnoreNamesShorterThanTheInboxPrefix() {
        assertThat(InboxChildResolver.inboxChild(MailboxPath.forUser(BOB, "inbox"), '.')).isEmpty();
        assertThat(InboxChildResolver.inboxChild(MailboxPath.forUser(BOB, "in"), '.')).isEmpty();
    }
}
