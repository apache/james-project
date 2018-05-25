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

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public abstract class AbstractMessageIdManagerSideEffectTest {
    private static final Quota<QuotaCount> OVER_QUOTA = Quota.<QuotaCount>builder()
        .used(QuotaCount.count(102))
        .computedLimit(QuotaCount.count(100))
        .build();
    private static final MessageUid messageUid1 = MessageUid.of(111);
    private static final MessageUid messageUid2 = MessageUid.of(113);

    public static final Flags FLAGS = new Flags();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MessageIdManager messageIdManager;
    private MailboxEventDispatcher dispatcher;
    private MailboxSession session;
    private Mailbox mailbox1;
    private Mailbox mailbox2;
    private Mailbox mailbox3;
    private QuotaManager quotaManager;
    private MessageIdManagerTestSystem testingData;

    protected abstract MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, MailboxEventDispatcher dispatcher) throws Exception;

    public void setUp() throws Exception {
        dispatcher = mock(MailboxEventDispatcher.class);
        quotaManager = mock(QuotaManager.class);

        session = new MockMailboxSession(ALICE);
        testingData = createTestSystem(quotaManager, dispatcher);
        messageIdManager = testingData.getMessageIdManager();

        mailbox1 = testingData.createMailbox(MailboxFixture.INBOX_ALICE, session);
        mailbox2 = testingData.createMailbox(MailboxFixture.OUTBOX_ALICE, session);
        mailbox3 = testingData.createMailbox(MailboxFixture.SENT_ALICE, session);
    }

    @Test
    public void deleteShouldCallEventDispatcher() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        MessageResult messageResult = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session).get(0);
        SimpleMessageMetaData simpleMessageMetaData = fromMessageResult(messageId, messageResult);

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        verify(dispatcher).expunged(session, simpleMessageMetaData, mailbox1);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void deletesShouldCallEventDispatcher() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId1 = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        MessageId messageId2 = testingData.persist(mailbox1.getMailboxId(), messageUid2, FLAGS, session);
        reset(dispatcher);

        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, session).get(0);
        SimpleMessageMetaData simpleMessageMetaData1 = fromMessageResult(messageId1, messageResult1);
        MessageResult messageResult2 = messageIdManager.getMessages(ImmutableList.of(messageId2), FetchGroupImpl.MINIMAL, session).get(0);
        SimpleMessageMetaData simpleMessageMetaData2 = fromMessageResult(messageId2, messageResult2);

        messageIdManager.delete(ImmutableList.of(messageId1, messageId2), session);

        verify(dispatcher).expunged(session, simpleMessageMetaData1, mailbox1);
        verify(dispatcher).expunged(session, simpleMessageMetaData2, mailbox1);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void deleteShouldNotCallEventDispatcherWhenMessageIsInWrongMailbox() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setInMailboxesShouldNotCallDispatcherWhenMessageAlreadyInMailbox() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setInMailboxesShouldCallDispatcher() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        verify(dispatcher).added(eq(session), eq(mailbox1), any(MailboxMessage.class));
        verify(dispatcher).moved(eq(session), any(), any());
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setInMailboxesShouldCallDispatcherWithMultipleMailboxes() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId(), mailbox3.getMailboxId()), session);

        messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);

        verify(dispatcher).added(eq(session), eq(mailbox1), any(MailboxMessage.class));
        verify(dispatcher).added(eq(session), eq(mailbox3), any(MailboxMessage.class));
        verify(dispatcher).moved(eq(session), any(), any());
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setInMailboxesShouldThrowExceptionWhenOverQuota() throws Exception {
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);
        when(quotaManager.getStorageQuota(any(QuotaRoot.class))).thenReturn(
            Quota.<QuotaSize>builder().used(QuotaSize.size(2)).computedLimit(QuotaSize.unlimited()).build());
        when(quotaManager.getMessageQuota(any(QuotaRoot.class))).thenReturn(OVER_QUOTA);
        when(quotaManager.getStorageQuota(any(QuotaRoot.class))).thenReturn(
            Quota.<QuotaSize>builder().used(QuotaSize.size(2)).computedLimit(QuotaSize.unlimited()).build());

        expectedException.expect(OverQuotaException.class);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
    }

    @Test
    public void setInMailboxesShouldCallDispatchForOnlyAddedAndRemovedMailboxes() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
        reset(dispatcher);

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);
        assertThat(messageResults).hasSize(2);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox3.getMailboxId()), session);

        verify(dispatcher).expunged(eq(session), any(SimpleMessageMetaData.class), eq(mailbox2));
        verify(dispatcher).added(eq(session), eq(mailbox3), any(MailboxMessage.class));
        verify(dispatcher).moved(eq(session), any(), any());
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldNotDispatchWhenFlagAlreadySet() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, newFlags, session);
        reset(dispatcher);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldNotDispatchWhenMessageAlreadyInMailbox() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, newFlags, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox2.getMailboxId()), session);
        reset(dispatcher);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldNotDispatchWhenMessageDoesNotBelongToMailbox() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldNotDispatchWhenEmptyMailboxes() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldDispatchWhenMessageBelongsToAllMailboxes() throws Exception {
        givenUnlimitedQuota();
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(mailbox1.getMailboxId(), messageUid1, FLAGS, session);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);
        reset(dispatcher);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        verify(dispatcher, times(2)).flagsUpdated(eq(session), any(MessageUid.class), any(Mailbox.class), any(UpdatedFlags.class));
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldDispatchWhenMessageBelongsToTheMailboxes() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);
        reset(dispatcher);

        Flags newFlags = new Flags(Flags.Flag.SEEN);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox1.getMailboxId(), mailbox2.getMailboxId()), session);

        List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, session);
        assertThat(messages).hasSize(1);
        MessageResult messageResult = messages.get(0);
        MessageUid messageUid = messageResult.getUid();
        long modSeq = messageResult.getModSeq();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .modSeq(modSeq)
            .oldFlags(FLAGS)
            .newFlags(newFlags)
            .build();

        verify(dispatcher, times(1)).flagsUpdated(session, messageUid, mailbox2, updatedFlags);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void deleteShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();

        reset(dispatcher);

        messageIdManager.delete(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void deletesShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();

        reset(dispatcher);

        messageIdManager.delete(ImmutableList.of(messageId), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setFlagsShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();
        reset(dispatcher);

        messageIdManager.setFlags(FLAGS, FlagsUpdateMode.ADD, messageId, ImmutableList.of(mailbox2.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    public void setInMailboxesShouldNotDispatchEventWhenMessageDoesNotExist() throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.createNotUsedMessageId();
        reset(dispatcher);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        verifyNoMoreInteractions(dispatcher);
    }

    private void givenUnlimitedQuota() throws MailboxException {
        when(quotaManager.getMessageQuota(any(QuotaRoot.class))).thenReturn(
            Quota.<QuotaCount>builder().used(QuotaCount.count(2)).computedLimit(QuotaCount.unlimited()).build());
        when(quotaManager.getStorageQuota(any(QuotaRoot.class))).thenReturn(
            Quota.<QuotaSize>builder().used(QuotaSize.size(2)).computedLimit(QuotaSize.unlimited()).build());
    }

    private SimpleMessageMetaData fromMessageResult(MessageId messageId, MessageResult messageResult) {
        return new SimpleMessageMetaData(messageResult.getUid(), messageResult.getModSeq(), messageResult.getFlags(), messageResult.getSize(), messageResult.getInternalDate(), messageId);
    }
}
