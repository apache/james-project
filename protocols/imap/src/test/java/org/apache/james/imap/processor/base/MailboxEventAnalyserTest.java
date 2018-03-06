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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MailboxEventAnalyserTest {
    public static class SingleMessageResultIterator implements MessageResultIterator {
        private final MessageResult messageResult;
        private boolean done;

        public SingleMessageResultIterator(MessageResult messageResult) {
            this.messageResult = messageResult;
            done = false;
        }

        public void remove() {
            throw new NotImplementedException("Not implemented");
        }

        public MessageResult next() {
            done = true;
            return messageResult;
        }

        public boolean hasNext() {
            return !done;
        }

        public MailboxException getException() {
            throw new NotImplementedException("Not implemented");
        }
    }


    private static final MessageUid MESSAGE_UID = MessageUid.of(1);
    private static final MockMailboxSession MAILBOX_SESSION = new MockMailboxSession("user");
    private static final MockMailboxSession OTHER_MAILBOX_SESSION = new MockMailboxSession("user");
    private static final char PATH_DELIMITER = '.';
    private static final MailboxPath MAILBOX_PATH = new MailboxPath("namespace", "user", "name");

    private SelectedMailboxImpl testee;

    @Before
    public void setUp() throws MailboxException {
        ImapSession imapSession = mock(ImapSession.class);
        when(imapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY))
            .thenReturn(MAILBOX_SESSION);
        when(imapSession.getState()).thenReturn(ImapSessionState.AUTHENTICATED);

        MailboxManager mailboxManager = mock(MailboxManager.class);
        MessageManager messageManager = mock(MessageManager.class);
        when(mailboxManager.getDelimiter()).thenReturn(PATH_DELIMITER);
        when(mailboxManager.getMailbox(any(MailboxId.class), any(MailboxSession.class)))
            .thenReturn(messageManager);
        when(mailboxManager.getMailbox(any(MailboxPath.class), any(MailboxSession.class)))
            .thenReturn(messageManager);

        MessageResult messageResult = mock(MessageResult.class);
        when(messageResult.getMailboxId()).thenReturn(TestId.of(36));
        when(messageResult.getUid()).thenReturn(MESSAGE_UID);

        when(messageManager.getApplicableFlags(any()))
            .thenReturn(new Flags());
        when(messageManager.search(any(), any()))
            .thenReturn(ImmutableList.of(MESSAGE_UID).iterator());
        when(messageManager.getMessages(any(), any(), any()))
            .thenReturn(new SingleMessageResultIterator(messageResult));

        testee = new SelectedMailboxImpl(mailboxManager, imapSession, MAILBOX_PATH);
    }

    @Test
    public void testShouldBeNoSizeChangeOnOtherEvent() throws Exception {
        MailboxListener.MailboxEvent event = new MailboxListener.MailboxEvent(MAILBOX_SESSION, MAILBOX_PATH) {};
      
        testee.event(event);

        assertThat(testee.isSizeChanged()).isFalse();
    }

    @Test
    public void testShouldBeNoSizeChangeOnAdded() throws Exception {
        testee.event(new FakeMailboxListenerAdded(MAILBOX_SESSION, ImmutableList.of(MessageUid.of(11)), MAILBOX_PATH));
        assertThat(testee.isSizeChanged()).isTrue();
    }

    @Test
    public void testShouldNoSizeChangeAfterReset() throws Exception {
        testee.event(new FakeMailboxListenerAdded(MAILBOX_SESSION, ImmutableList.of(MessageUid.of(11)), MAILBOX_PATH));
        testee.resetEvents();

        assertThat(testee.isSizeChanged()).isFalse();
    }

    @Test
    public void testShouldNotSetUidWhenNoSystemFlagChange() throws Exception {
        FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(MAILBOX_SESSION,
            ImmutableList.of(MessageUid.of(90L)),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(90))
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags())
                .build()),
            MAILBOX_PATH);
        testee.event(update);

        assertThat(testee.flagUpdateUids()).isEmpty();
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChange() throws Exception {
        MessageUid uid = MessageUid.of(900);
        
        FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(OTHER_MAILBOX_SESSION,
            ImmutableList.of(uid),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(uid)
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            MAILBOX_PATH);
        testee.event(update);

       assertThat(testee.flagUpdateUids().iterator()).containsExactly(uid);
    }

    @Test
    public void testShouldClearFlagUidsUponReset() throws Exception {
        MessageUid uid = MessageUid.of(900);
        SelectedMailboxImpl analyser = this.testee;
        
        FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(MAILBOX_SESSION,
            ImmutableList.of(uid),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(uid)
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            MAILBOX_PATH);
        analyser.event(update);
        analyser.event(update);
        analyser.deselect();

        assertThat(analyser.flagUpdateUids()).isEmpty();
    }

    @Test
    public void testShouldSetUidWhenSystemFlagChangeDifferentSessionInSilentMode() throws Exception {
        MessageUid uid = MessageUid.of(900);

        FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(OTHER_MAILBOX_SESSION,
            ImmutableList.of(uid),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(uid)
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.ANSWERED))
                .build()),
            MAILBOX_PATH);
        testee.event(update);
        testee.setSilentFlagChanges(true);
        testee.event(update);

        assertThat(testee.flagUpdateUids().iterator()).containsExactly(uid);
    }

    @Test
    public void testShouldNotSetUidWhenSystemFlagChangeSameSessionInSilentMode() throws Exception {
        FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(MAILBOX_SESSION,
            ImmutableList.of(MessageUid.of(345)),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(345))
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags())
                .build()),
            MAILBOX_PATH);
        testee.event(update);
        testee.setSilentFlagChanges(true);
        testee.event(update);

        assertThat(testee.flagUpdateUids().iterator()).isEmpty();
    }

    @Test
    public void testShouldNotSetUidWhenOnlyRecentFlagUpdated() throws Exception {
        FakeMailboxListenerFlagsUpdate update = new FakeMailboxListenerFlagsUpdate(MAILBOX_SESSION,
            ImmutableList.of(MessageUid.of(886)),
            ImmutableList.of(UpdatedFlags.builder()
                .uid(MessageUid.of(886))
                .modSeq(-1)
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.RECENT))
                .build()),
            MAILBOX_PATH);
        testee.event(update);

        assertThat(testee.flagUpdateUids().iterator()).isEmpty();
    }
}
