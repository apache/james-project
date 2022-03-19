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

import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxEvent;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MailboxEventAnalyserTest {
    private static final MessageUid UID = MessageUid.of(900);
    private static final UpdatedFlags ADD_RECENT_UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(UID)
        .modSeq(ModSeq.first())
        .oldFlags(new Flags())
        .newFlags(new Flags(Flags.Flag.RECENT))
        .build();
    private static final UpdatedFlags ADD_ANSWERED_UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(UID)
        .modSeq(ModSeq.first())
        .oldFlags(new Flags())
        .newFlags(new Flags(Flags.Flag.ANSWERED))
        .build();
    private static final UpdatedFlags NOOP_UPDATED_FLAGS = UpdatedFlags.builder()
        .uid(UID)
        .modSeq(ModSeq.first())
        .oldFlags(new Flags())
        .newFlags(new Flags())
        .build();

    public static class SingleMessageResultIterator implements MessageResultIterator {
        private final MessageResult messageResult;
        private boolean done;

        public SingleMessageResultIterator(MessageResult messageResult) {
            this.messageResult = messageResult;
            done = false;
        }

        @Override
        public void remove() {
            throw new NotImplementedException("Not implemented");
        }

        @Override
        public MessageResult next() {
            done = true;
            return messageResult;
        }

        @Override
        public boolean hasNext() {
            return !done;
        }

        @Override
        public MailboxException getException() {
            throw new NotImplementedException("Not implemented");
        }
    }

    private static final MessageUid MESSAGE_UID = MessageUid.of(1);
    private static final Username USER = Username.of("user");
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(USER);
    private static final MailboxSession OTHER_MAILBOX_SESSION = MailboxSessionUtil.create(USER);
    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", USER, "name");
    private static final TestId MAILBOX_ID = TestId.of(36);
    private static final UidValidity UID_VALIDITY = UidValidity.of(1024);
    private static final Mailbox DEFAULT_MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID);
    private static final Added ADDED = EventFactory.added()
        .randomEventId()
        .mailboxSession(MAILBOX_SESSION)
        .mailbox(DEFAULT_MAILBOX)
        .addMetaData(new MessageMetaData(MessageUid.of(11), ModSeq.first(), new Flags(), 45, new Date(), Optional.empty(), new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId())))
        .isDelivery(!IS_DELIVERY)
        .isAppended(!IS_APPENDED)
        .build();

    private SelectedMailboxImpl testee;

    @BeforeEach
    void setUp() throws MailboxException {
        FakeImapSession imapSession = new FakeImapSession();
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        imapSession.setMailboxSession(MAILBOX_SESSION);
        imapSession.authenticated();

        MailboxManager mailboxManager = mock(MailboxManager.class);
        MessageManager messageManager = mock(MessageManager.class);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getMailboxId()).thenReturn(MAILBOX_ID);
        when(messageResult.getUid()).thenReturn(MESSAGE_UID);

        when(messageManager.getApplicableFlagsReactive(any())).thenReturn(Mono.just(new Flags()));
        when(messageManager.getId()).thenReturn(MAILBOX_ID);
        when(messageManager.search(any(), any()))
            .thenReturn(Flux.just(MESSAGE_UID));
        when(messageManager.getMessagesReactive(any(), any(), any()))
            .thenReturn(Flux.just(messageResult));

        testee = new SelectedMailboxImpl(mailboxManager, eventBus, imapSession, messageManager);
        testee.finishInit().block();
    }

    @Test
    void testShouldBeNoSizeChangeOnOtherEvent() throws Exception {
            MailboxEvent event = new MailboxAdded(MAILBOX_SESSION.getSessionId(),
            MAILBOX_SESSION.getUser(), MAILBOX_PATH, MAILBOX_ID, Event.EventId.random());
      
        testee.event(event);

        assertThat(testee.isSizeChanged()).isFalse();
    }

    @Test
    void testShouldBeNoSizeChangeOnAdded() throws Exception {
        testee.event(ADDED);

        assertThat(testee.isSizeChanged()).isTrue();
    }

    @Test
    void testShouldNoSizeChangeAfterReset() throws Exception {
        testee.event(ADDED);
        testee.resetEvents();

        assertThat(testee.isSizeChanged()).isFalse();
    }

    @Test
    void testShouldNotSetUidWhenNoSystemFlagChange() throws Exception {
        FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(NOOP_UPDATED_FLAGS)
            .build();

        testee.event(update);

        assertThat(testee.flagUpdateUids()).isEmpty();
    }

    @Test
    void testShouldSetUidWhenSystemFlagChange() throws Exception {
        FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(OTHER_MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(ADD_ANSWERED_UPDATED_FLAGS)
            .build();

        testee.event(update);

       assertThat(testee.flagUpdateUids())
           .containsExactly(UID);
    }

    @Test
    void testShouldClearFlagUidsUponReset() throws Exception {
        SelectedMailboxImpl analyser = this.testee;

        FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(ADD_ANSWERED_UPDATED_FLAGS)
            .build();

        analyser.event(update);
        analyser.event(update);
        analyser.deselect().block();

        assertThat(analyser.flagUpdateUids()).isEmpty();
    }

    @Test
    void testShouldSetUidWhenSystemFlagChangeDifferentSessionInSilentMode() throws Exception {
        FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(OTHER_MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(ADD_ANSWERED_UPDATED_FLAGS)
            .build();

        testee.event(update);
        testee.setSilentFlagChanges(true);
        testee.event(update);

        assertThat(testee.flagUpdateUids())
            .containsExactly(UID);
    }

    @Test
    void testShouldNotSetUidWhenSystemFlagChangeSameSessionInSilentMode() throws Exception {
        FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(NOOP_UPDATED_FLAGS)
            .build();

        testee.event(update);
        testee.setSilentFlagChanges(true);
        testee.event(update);

        assertThat(testee.flagUpdateUids())
            .isEmpty();
    }

    @Test
    void testShouldNotSetUidWhenOnlyRecentFlagUpdated() throws Exception {
        FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(ADD_RECENT_UPDATED_FLAGS)
            .build();

        testee.event(update);

        assertThat(testee.flagUpdateUids())
            .isEmpty();
    }
}
