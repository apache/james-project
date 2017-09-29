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

import static org.apache.james.mailbox.fixture.MailboxFixture.MAILBOX_PATH1;
import static org.apache.james.mailbox.fixture.MailboxFixture.OTHER_USER;
import static org.apache.james.mailbox.fixture.MailboxFixture.THIRD_USER;
import static org.apache.james.mailbox.fixture.MailboxFixture.USER;
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
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(OTHER_USER).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(THIRD_USER).asAddition());
        MailboxACL actual = StoreMessageManager.filteredForSession(
            new SimpleMailbox(MAILBOX_PATH1, UID_VALIDITY), acl, new MockMailboxSession(USER));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldBeIdentityWhenAdmin() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(OTHER_USER).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(THIRD_USER).asAddition());
        MailboxACL actual = StoreMessageManager.filteredForSession(
            new SimpleMailbox(MAILBOX_PATH1, UID_VALIDITY), acl, new MockMailboxSession(THIRD_USER));
        assertThat(actual).isEqualTo(acl);
    }

    @Test
    public void filteredForSessionShouldContainOnlyLoggedUserWhenReadWriteAccess() throws UnsupportedRightException {
        MailboxACL acl = new MailboxACL()
            .apply(MailboxACL.command().rights(Right.Read, Right.Write).forUser(OTHER_USER).asAddition())
            .apply(MailboxACL.command().rights(Right.Read, Right.Write, Right.Administer).forUser(THIRD_USER).asAddition());
        MailboxACL actual = StoreMessageManager.filteredForSession(
            new SimpleMailbox(MAILBOX_PATH1, UID_VALIDITY), acl, new MockMailboxSession(OTHER_USER));
        assertThat(actual.getEntries()).containsKey(MailboxACL.EntryKey.createUser(OTHER_USER));
    }
}