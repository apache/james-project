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

import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Test;

public class StoreMessageManagerTest {

    public static final long UID_VALIDITY = 3421l;

    @Test
    public void filteredForSessionShouldBeIdentityWhenOwner() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreMessageManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, new MockMailboxSession(ALICE));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenAdmin() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreMessageManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, new MockMailboxSession(CEDRIC));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldContainOnlyLoggedUserWhenReadWriteAccess() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(BOB).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(CEDRIC).asAddition());
        MailboxACL actual = StoreMessageManager.filteredForSession(
            new SimpleMailbox(INBOX_ALICE, UID_VALIDITY), acl, new MockMailboxSession(BOB));
        assertThat(actual.getEntries()).containsKey(MailboxACL.EntryKey.createUserEntryKey(BOB));
    }
}