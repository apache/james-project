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

package org.apache.james.imap.processor.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class SelectedMailboxImplTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectedMailboxImplTest.class);
    private static final MessageUid EMITTED_EVENT_UID = MessageUid.of(5);
    private static final int MOD_SEQ = 12;
    private static final int SIZE = 38;

    private ExecutorService executorService;
    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private MailboxPath mailboxPath;
    private ImapSession imapSession;
    private Mailbox mailbox;

    @Before
    public void setUp() throws Exception {
        executorService = Executors.newFixedThreadPool(1);
        mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "tellier@linagora.com", MailboxConstants.INBOX);
        mailboxManager = mock(MailboxManager.class);
        messageManager = mock(MessageManager.class);
        imapSession = mock(ImapSession.class);
        mailbox = mock(Mailbox.class);

        when(mailboxManager.getMailbox(eq(mailboxPath), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(messageManager.getApplicableFlags(any(MailboxSession.class)))
            .thenReturn(new Flags());
        when(messageManager.search(any(SearchQuery.class), any(MailboxSession.class)))
            .then(delayedSearchAnswer());

        when(imapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mock(MailboxSession.class));

        when(mailbox.generateAssociatedPath())
            .thenReturn(mailboxPath);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void concurrentEventShouldNotSkipAddedEventsEmittedDuringInitialisation() throws Exception {
        final AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitEventAnswer(successCount))
            .when(mailboxManager)
            .addListener(eq(mailboxPath), any(MailboxListener.class), any(MailboxSession.class));

        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            imapSession,
            mailboxPath);

        assertThat(selectedMailbox.getLastUid().get()).isEqualTo(EMITTED_EVENT_UID);
    }

    @Test
    public void concurrentEventShouldBeProcessedSuccessfullyDuringInitialisation() throws Exception {
        final AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitEventAnswer(successCount))
            .when(mailboxManager)
            .addListener(eq(mailboxPath), any(MailboxListener.class), any(MailboxSession.class));

        new SelectedMailboxImpl(
            mailboxManager,
            imapSession,
            mailboxPath);

        assertThat(successCount.get())
            .as("Get the incremented value in case of successful event processing.")
            .isEqualTo(1);
    }

    private Answer<Iterator<MessageUid>> delayedSearchAnswer() {
        return invocation -> {
            Thread.sleep(1000);
            return ImmutableList.of(MessageUid.of(1), MessageUid.of(3)).iterator();
        };
    }

    private Answer<Iterator<MessageUid>> generateEmitEventAnswer(final AtomicInteger success) {
        return invocation -> {
            Object[] args = invocation.getArguments();
            final MailboxListener mailboxListener = (MailboxListener) args[1];
            executorService.submit(() -> {
                try {
                    emitEvent(mailboxListener);
                    success.incrementAndGet();
                } catch (Exception e) {
                    LOGGER.error("Error while processing event on a concurrent thread", e);
                }
            });
            return null;
        };
    }

    private void emitEvent(MailboxListener mailboxListener) {
        TreeMap<MessageUid, MessageMetaData> result = new TreeMap<>();
        result.put(EMITTED_EVENT_UID, new SimpleMessageMetaData(EMITTED_EVENT_UID, MOD_SEQ, new Flags(), SIZE, new Date(), new DefaultMessageId()));
        mailboxListener.event(new EventFactory().added(mock(MailboxSession.class), result, mailbox, ImmutableMap.<MessageUid, MailboxMessage> of()));
    }
}
