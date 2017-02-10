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

package org.apache.james.jmap.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.manager.MailboxManagerFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.SimpleMailboxMetaData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SystemMailboxesProviderImplTest {

    private static final MailboxPath INBOX = MailboxManagerFixture.MAILBOX_PATH1;
    private static final MailboxPath OUTBOX = MailboxManagerFixture.MAILBOX_PATH2;
    private static final char DELIMITER = '.';

    private static final MailboxId inboxId = TestId.of(1);
    private static final MailboxId outboxId = TestId.of(2);

    private static final MailboxMetaData inboxMetadata = new SimpleMailboxMetaData(INBOX, inboxId, DELIMITER);
    private static final MailboxMetaData outboxMetadata = new SimpleMailboxMetaData(OUTBOX, outboxId, DELIMITER);

    private MailboxSession mailboxSession = new MockMailboxSession("user");
    private SystemMailboxesProviderImpl systemMailboxProvider;

    private MailboxManager mailboxManager;

    private MessageManager inboxMessageManager;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        mailboxManager = mock(MailboxManager.class);
        inboxMessageManager = mock(MessageManager.class);

        systemMailboxProvider = new SystemMailboxesProviderImpl(mailboxManager);
    }

    @Test
    public void getMailboxByRoleShouldReturnEmptyWhenNoMailbox() throws Exception {
        when(mailboxManager.getMailbox(eq(MailboxManagerFixture.MAILBOX_PATH1), eq(mailboxSession))).thenThrow(MailboxNotFoundException.class);

        assertThat(systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession)).isEmpty();
    }

    @Test
    public void getMailboxByRoleShouldReturnMailboxByRole() throws Exception {
        when(mailboxManager.getMailbox(eq(MailboxManagerFixture.MAILBOX_PATH1), eq(mailboxSession))).thenReturn(inboxMessageManager);

        assertThat(systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession))
            .hasSize(1)
            .containsOnly(inboxMessageManager);
    }
}
