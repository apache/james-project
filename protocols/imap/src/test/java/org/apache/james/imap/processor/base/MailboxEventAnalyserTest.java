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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.junit.Before;
import org.junit.Test;

public class MailboxEventAnalyserTest {
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
    private static final char PATH_DELIMITER = '.';
    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", USER, "name");
    private static final TestId MAILBOX_ID = TestId.of(36);
    private static final int UID_VALIDITY = 1024;
    private static final Mailbox DEFAULT_MAILBOX = new Mailbox(MAILBOX_PATH, UID_VALIDITY, MAILBOX_ID);
    private static final MailboxListener.Added ADDED = EventFactory.added()
        .randomEventId()
        .mailboxSession(MAILBOX_SESSION)
        .mailbox(DEFAULT_MAILBOX)
        .addMetaData(new MessageMetaData(MessageUid.of(11), ModSeq.first(), new Flags(), 45, new Date(), new DefaultMessageId()))
        .build();

    private SelectedMailboxImpl testee;

    @Before
    public void setUp() throws MailboxException {
        ImapSession imapSession = mock(ImapSession.class);
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()));
        when(imapSession.getMailboxSession())
            .thenReturn(MAILBOX_SESSION);
        when(imapSession.getState()).thenReturn(ImapSessionState.AUTHENTICATED);

        MailboxManager mailboxManager = mock(MailboxManager.class);
        MessageManager messageManager = mock(MessageManager.class);
        when(mailboxManager.getDelimiter()).thenReturn(PATH_DELIMITER);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getMailboxId()).thenReturn(MAILBOX_ID);
        when(messageResult.getUid()).thenReturn(MESSAGE_UID);

        when(messageManager.getApplicableFlags(any())).thenReturn(new Flags());
        when(messageManager.getId()).thenReturn(MAILBOX_ID);
        when(messageManager.search(any(), any()))
            .thenReturn(Stream.of(MESSAGE_UID));
        when(messageManager.getMessages(any(), any(), any()))
            .thenReturn(new SingleMessageResultIterator(messageResult));

        testee = new SelectedMailboxImpl(mailboxManager, eventBus, imapSession, MAILBOX_PATH);
    }

    @Test
    public void testShouldBeNoSizeChangeOnOtherEvent() {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxAdded(MAILBOX_SESSION.getSessionId(),
            MAILBOX_SESSION.getUser(), MAILBOX_PATH, MAILBOX_ID, Event.EventId.random());
      
        testee.event(event);

        assertThat(testee.isSizeChanged()).isFalse();
    }

    @Test
    public void testShouldBeNoSizeChangeOnAdded() {
        testee.event(ADDED);

        assertThat(testee.isSizeChanged()).isTrue();
    }

    @Test
    public void testShouldNoSizeChangeAfterReset() {
        testee.event(ADDED);
        testee.resetEvents();

        assertThat(testee.isSizeChanged()).isFalse();
    }

    @Test
    public void testShouldNotSetUidWhenNoSystemFlagChange() {
        MailboxListener.FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(NOOP_UPDATED_FLAGS)
            .build();

        testee.event(update);

        assertThat(testee.flagUpdateUids()).isEmpty();
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChange() {
        MailboxListener.FlagsUpdated update = EventFactory.flagsUpdated()
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
    public void testShouldClearFlagUidsUponReset() {
        SelectedMailboxImpl analyser = this.testee;

        MailboxListener.FlagsUpdated update = EventFactory.flagsUpdated()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(DEFAULT_MAILBOX)
            .updatedFlag(ADD_ANSWERED_UPDATED_FLAGS)
            .build();

        analyser.event(update);
        analyser.event(update);
        analyser.deselect();

        assertThat(analyser.flagUpdateUids()).isEmpty();
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChangeDifferentSessionInSilentMode() {
        MailboxListener.FlagsUpdated update = EventFactory.flagsUpdated()
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
    public void testShouldNotSetUidWhenSystemFlagChangeSameSessionInSilentMode() {
        MailboxListener.FlagsUpdated update = EventFactory.flagsUpdated()
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
    public void testShouldNotSetUidWhenOnlyRecentFlagUpdated() {
        MailboxListener.FlagsUpdated update = EventFactory.flagsUpdated()
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
