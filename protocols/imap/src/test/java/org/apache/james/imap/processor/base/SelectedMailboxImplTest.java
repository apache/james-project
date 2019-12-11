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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SelectedMailboxImplTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectedMailboxImplTest.class);
    private static final MessageUid EMITTED_EVENT_UID = MessageUid.of(5);
    private static final ModSeq MOD_SEQ = ModSeq.of(12);
    private static final int SIZE = 38;

    private ExecutorService executorService;
    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private MailboxPath mailboxPath;
    private FakeImapSession imapSession;
    private Mailbox mailbox;
    private TestId mailboxId;
    private EventBus eventBus;
    private MailboxIdRegistrationKey mailboxIdRegistrationKey;

    @Before
    public void setUp() throws Exception {
        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        executorService = Executors.newFixedThreadPool(1, threadFactory);
        mailboxPath = MailboxPath.inbox(Username.of("tellier@linagora.com"));
        mailboxManager = mock(MailboxManager.class);
        messageManager = mock(MessageManager.class);
        imapSession = new FakeImapSession();
        mailbox = mock(Mailbox.class);
        mailboxId = TestId.of(42);
        mailboxIdRegistrationKey = new MailboxIdRegistrationKey(mailboxId);
        eventBus = mock(EventBus.class);

        when(mailboxManager.getMailbox(eq(mailboxPath), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(messageManager.getApplicableFlags(any(MailboxSession.class)))
            .thenReturn(new Flags());
        when(messageManager.search(any(SearchQuery.class), any(MailboxSession.class)))
            .then(delayedSearchAnswer());
        when(messageManager.getId()).thenReturn(mailboxId);

        imapSession.setMailboxSession(mock(MailboxSession.class));

        when(mailbox.generateAssociatedPath()).thenReturn(mailboxPath);
        when(mailbox.getMailboxId()).thenReturn(mailboxId);
    }

    @After
    public void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    public void concurrentEventShouldNotSkipAddedEventsEmittedDuringInitialisation() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitEventAnswer(successCount))
            .when(eventBus)
            .register(any(MailboxListener.class), eq(mailboxIdRegistrationKey));
        SelectedMailboxImpl selectedMailbox = new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            imapSession,
            mailboxPath);

        assertThat(selectedMailbox.getLastUid().get()).isEqualTo(EMITTED_EVENT_UID);
    }

    @Test
    public void concurrentEventShouldBeProcessedSuccessfullyDuringInitialisation() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        doAnswer(generateEmitEventAnswer(successCount))
            .when(eventBus)
            .register(any(MailboxListener.class), eq(mailboxIdRegistrationKey));

        new SelectedMailboxImpl(
            mailboxManager,
            eventBus,
            imapSession,
            mailboxPath);

        assertThat(successCount.get())
            .as("Get the incremented value in case of successful event processing.")
            .isEqualTo(1);
    }

    private Answer<Stream<MessageUid>> delayedSearchAnswer() {
        return invocation -> {
            Thread.sleep(1000);
            return Stream.of(MessageUid.of(1), MessageUid.of(3));
        };
    }

    private Answer<Iterator<MessageUid>> generateEmitEventAnswer(AtomicInteger success) {
        return invocation -> {
            Object[] args = invocation.getArguments();
            MailboxListener mailboxListener = (MailboxListener) args[0];
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

    private void emitEvent(MailboxListener mailboxListener) throws Exception {
        mailboxListener.event(EventFactory.added()
            .randomEventId()
            .mailboxSession(MailboxSessionUtil.create(Username.of("user")))
            .mailbox(mailbox)
            .addMetaData(new MessageMetaData(EMITTED_EVENT_UID, MOD_SEQ, new Flags(), SIZE, new Date(), new DefaultMessageId()))
            .build());
    }
}
