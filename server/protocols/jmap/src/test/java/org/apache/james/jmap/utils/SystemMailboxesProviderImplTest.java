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

import java.util.stream.Stream;

import org.apache.james.jmap.exceptions.MailboxRoleNotFoundException;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.manager.MailboxManagerFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.SimpleMailboxMetaData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

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
    public void findMailboxesShouldReturnEmptyWhenEmptySearchResult() throws Exception {
        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of());

        assertThat(systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession)).isEmpty();
    }

    @Test
    public void findMailboxesShouldFilterTheMailboxByItsRole() throws Exception {
        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of(inboxMetadata, outboxMetadata));
        when(mailboxManager.getMailbox(eq(INBOX), eq(mailboxSession))).thenReturn(inboxMessageManager);

        Stream<MessageManager> result = systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession);

        assertThat(result).hasSize(1).containsOnly(inboxMessageManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void findMailboxesShouldThrowWhenMailboxManagerHasErrorWhenSearching() throws Exception {
        expectedException.expect(MailboxException.class);

        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenThrow(MailboxException.class);

        systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void findMailboxesShouldBeEmptyWhenMailboxManagerCanNotGetMailbox() throws Exception {
        expectedException.expect(MailboxException.class);

        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of(inboxMetadata, outboxMetadata));
        when(mailboxManager.getMailbox(eq(INBOX), eq(mailboxSession))).thenThrow(MailboxException.class);

        assertThat(systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void findMailboxesShouldReturnWhenMailboxManagerCanNotGetMailboxOfNonFilterMailbox() throws Exception {
        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of(inboxMetadata, outboxMetadata));

        when(mailboxManager.getMailbox(eq(INBOX), eq(mailboxSession))).thenReturn(inboxMessageManager);
        when(mailboxManager.getMailbox(eq(OUTBOX), eq(mailboxSession))).thenThrow(MailboxException.class);

        Stream<MessageManager> result = systemMailboxProvider.listMailboxes(Role.INBOX, mailboxSession);

        assertThat(result).hasSize(1).containsOnly(inboxMessageManager);

    }

    @Test
    public void findMailboxShouldThrowWhenEmptySearchResult() throws Exception {
        expectedException.expect(MailboxRoleNotFoundException.class);

        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of());

        systemMailboxProvider.findMailbox(Role.INBOX, mailboxSession);
    }

    @Test
    public void findMailboxShouldThrowWhenCanNotFindAny() throws Exception {
        expectedException.expect(MailboxRoleNotFoundException.class);

        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of(outboxMetadata));

        systemMailboxProvider.findMailbox(Role.INBOX, mailboxSession);
    }

    @Test
    public void findMailboxShouldReturnMailboxByRole() throws Exception {
        when(mailboxManager.search(any(MailboxQuery.class), eq(mailboxSession))).thenReturn(ImmutableList.of(inboxMetadata, outboxMetadata));
        when(mailboxManager.getMailbox(eq(INBOX), eq(mailboxSession))).thenReturn(inboxMessageManager);

        MessageManager result = systemMailboxProvider.findMailbox(Role.INBOX, mailboxSession);

        assertThat(result).isEqualTo(inboxMessageManager);
    }

}
