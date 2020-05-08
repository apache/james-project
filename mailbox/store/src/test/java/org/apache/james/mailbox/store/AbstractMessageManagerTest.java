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

package org.apache.james.mailbox.store;

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.apache.james.mailbox.fixture.MailboxFixture.CEDRIC;
import static org.apache.james.mailbox.fixture.MailboxFixture.INBOX_ALICE;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.Test;

public abstract class AbstractMessageManagerTest {

    static final boolean NO_RESET_RECENT = false;

    MailboxManager mailboxManager;
    MailboxSession aliceSession;
    MailboxSession bobSession;

    protected void setup(MessageManagerTestSystem testSystem) throws Exception {
        aliceSession = MailboxSessionUtil.create(ALICE);
        bobSession = MailboxSessionUtil.create(BOB);
        mailboxManager = testSystem.getMailboxManager();

        testSystem.createMailbox(INBOX_ALICE, aliceSession);
        testSystem.createMailbox(MailboxFixture.OUTBOX_ALICE, aliceSession);
        testSystem.createMailbox(MailboxFixture.SENT_ALICE, aliceSession);
        testSystem.createMailbox(MailboxFixture.INBOX_BOB, bobSession);
    }

    @Test
    void getMetadataShouldListUsersAclWhenShared() throws Exception {
        mailboxManager.applyRightsCommand(INBOX_ALICE, MailboxACL.command().forUser(BOB).rights(MailboxACL.Right.Read).asAddition(), aliceSession);
        mailboxManager.applyRightsCommand(INBOX_ALICE, MailboxACL.command().forUser(CEDRIC).rights(MailboxACL.Right.Read).asAddition(), aliceSession);
        MessageManager messageManager = mailboxManager.getMailbox(INBOX_ALICE, aliceSession);

        MessageManager.MailboxMetaData actual = messageManager.getMetaData(NO_RESET_RECENT, aliceSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT);
        assertThat(actual.getACL().getEntries()).containsKeys(MailboxACL.EntryKey.createUserEntryKey(BOB), MailboxACL.EntryKey.createUserEntryKey(CEDRIC));
    }

    @Test
    void getMetadataShouldNotExposeOtherUsersWhenSessionIsNotOwner() throws Exception {
        mailboxManager.applyRightsCommand(INBOX_ALICE, MailboxACL.command().forUser(BOB).rights(MailboxACL.Right.Read).asAddition(), aliceSession);
        mailboxManager.applyRightsCommand(INBOX_ALICE, MailboxACL.command().forUser(CEDRIC).rights(MailboxACL.Right.Read).asAddition(), aliceSession);
        MessageManager messageManager = mailboxManager.getMailbox(INBOX_ALICE, aliceSession);

        MessageManager.MailboxMetaData actual = messageManager.getMetaData(NO_RESET_RECENT, bobSession, MessageManager.MailboxMetaData.FetchGroup.NO_COUNT);
        assertThat(actual.getACL().getEntries()).containsOnlyKeys(MailboxACL.EntryKey.createUserEntryKey(BOB));
    }

}
