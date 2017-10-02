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

package org.apache.james.mailbox.indexer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ReIndexerImplTest {

    public static final MailboxPath INBOX = MailboxPath.forUser("benwa@apache.org", "INBOX");
    public static final int LIMIT = 0;
    private MailboxManager mailboxManager;
    private MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private ListeningMessageSearchIndex messageSearchIndex;

    private ReIndexer reIndexer;

    @Before
    public void setUp() {
        mailboxManager = mock(MailboxManager.class);
        mailboxSessionMapperFactory = mock(MailboxSessionMapperFactory.class);
        messageSearchIndex = mock(ListeningMessageSearchIndex.class);
        reIndexer = new ReIndexerImpl(mailboxManager, messageSearchIndex, mailboxSessionMapperFactory);
    }

    @Test
    public void test() throws Exception {
        final MockMailboxSession mockMailboxSession = new MockMailboxSession("re-indexing");
        when(mailboxManager.createSystemSession(any(String.class)))
            .thenReturn(mockMailboxSession);
        final MessageMapper messageMapper = mock(MessageMapper.class);
        final MailboxMapper mailboxMapper = mock(MailboxMapper.class);
        when(mailboxSessionMapperFactory.getMessageMapper(any(MailboxSession.class)))
            .thenReturn(messageMapper);
        when(mailboxSessionMapperFactory.getMailboxMapper(any(MailboxSession.class)))
            .thenReturn(mailboxMapper);
        final MailboxMessage message = new MessageBuilder().build();
        final SimpleMailbox mailbox = new SimpleMailbox(INBOX, 42);
        mailbox.setMailboxId(message.getMailboxId());
        when(mailboxMapper.findMailboxByPath(INBOX)).thenReturn(mailbox);
        when(messageMapper.findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .thenReturn(Lists.newArrayList(message).iterator());

        reIndexer.reIndex(INBOX);

        verify(mailboxManager).createSystemSession(any(String.class));
        verify(mailboxSessionMapperFactory).getMailboxMapper(mockMailboxSession);
        verify(mailboxSessionMapperFactory).getMessageMapper(mockMailboxSession);
        verify(mailboxMapper).findMailboxByPath(INBOX);
        verify(messageMapper).findInMailbox(mailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT);
        verify(mailboxManager).addListener(eq(INBOX), any(MailboxListener.class), any(MailboxSession.class));
        verify(mailboxManager).removeListener(eq(INBOX), any(MailboxListener.class), any(MailboxSession.class));
        verify(messageSearchIndex).add(any(MailboxSession.class), eq(mailbox), eq(message));
        verify(messageSearchIndex).deleteAll(any(MailboxSession.class), eq(mailbox));
        verifyNoMoreInteractions(mailboxMapper, mailboxSessionMapperFactory, messageSearchIndex, messageMapper, mailboxMapper);
    }

    @Test
    public void mailboxPathUserShouldBeUsedWhenReIndexing() throws Exception {
        MockMailboxSession systemMailboxSession = new MockMailboxSession("re-indexing");
        when(mailboxManager.createSystemSession("re-indexing"))
            .thenReturn(systemMailboxSession);
        MailboxMapper mailboxMapper = mock(MailboxMapper.class);
        when(mailboxSessionMapperFactory.getMailboxMapper(systemMailboxSession))
            .thenReturn(mailboxMapper);

        String user1 = "user1@james.org";
        MailboxPath user1MailboxPath = MailboxPath.forUser(user1, "Inbox");
        MockMailboxSession user1MailboxSession = new MockMailboxSession(user1);
        when(mailboxManager.createSystemSession(user1))
            .thenReturn(user1MailboxSession);
        MailboxMapper user1MailboxMapper = mock(MailboxMapper.class);
        when(mailboxSessionMapperFactory.getMailboxMapper(user1MailboxSession))
            .thenReturn(user1MailboxMapper);
        Mailbox user1Mailbox = mock(Mailbox.class);
        when(user1MailboxMapper.findMailboxByPath(user1MailboxPath))
            .thenReturn(user1Mailbox);
        MessageMapper user1MessageMapper = mock(MessageMapper.class);
        when(mailboxSessionMapperFactory.getMessageMapper(user1MailboxSession))
            .thenReturn(user1MessageMapper);
        MailboxMessage user1MailboxMessage = mock(MailboxMessage.class);
        when(user1MessageMapper.findInMailbox(user1Mailbox, MessageRange.all(), MessageMapper.FetchType.Full, ReIndexerImpl.NO_LIMIT))
            .thenReturn(ImmutableList.of(user1MailboxMessage).iterator());
        when(user1MailboxMessage.getUid())
            .thenReturn(MessageUid.of(1));

        when(mailboxManager.list(systemMailboxSession))
            .thenReturn(ImmutableList.of(user1MailboxPath));

        reIndexer.reIndex();

        verify(messageSearchIndex).deleteAll(user1MailboxSession, user1Mailbox);
        verify(messageSearchIndex).add(user1MailboxSession, user1Mailbox, user1MailboxMessage);
    }
}
