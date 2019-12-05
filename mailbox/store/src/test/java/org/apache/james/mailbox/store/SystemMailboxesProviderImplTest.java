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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemMailboxesProviderImplTest {

    MailboxSession mailboxSession = MailboxSessionUtil.create(MailboxFixture.ALICE);
    SystemMailboxesProviderImpl systemMailboxProvider;

    MailboxManager mailboxManager;

    MessageManager inboxMessageManager;

    @BeforeEach
    void setUp() {
        mailboxManager = mock(MailboxManager.class);
        inboxMessageManager = mock(MessageManager.class);

        systemMailboxProvider = new SystemMailboxesProviderImpl(mailboxManager);
    }

    @Test
    void getMailboxByRoleShouldReturnEmptyWhenNoMailbox() throws Exception {
        when(mailboxManager.createSystemSession(MailboxFixture.ALICE)).thenReturn(mailboxSession);
        when(mailboxManager.getMailbox(eq(MailboxFixture.INBOX_ALICE), eq(mailboxSession))).thenThrow(MailboxNotFoundException.class);

        assertThat(systemMailboxProvider.getMailboxByRole(Role.INBOX, mailboxSession.getUser())).isEmpty();
    }

    @Test
    void getMailboxByRoleShouldReturnMailboxByRole() throws Exception {
        when(mailboxManager.createSystemSession(MailboxFixture.ALICE)).thenReturn(mailboxSession);
        when(mailboxManager.getMailbox(eq(MailboxFixture.INBOX_ALICE), eq(mailboxSession))).thenReturn(inboxMessageManager);

        assertThat(systemMailboxProvider.getMailboxByRole(Role.INBOX, mailboxSession.getUser()))
            .hasSize(1)
            .containsOnly(inboxMessageManager);
    }
}
