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

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSession.SessionType;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public abstract class AbstractMessageIdManagerStorageTest {
    public static final Flags FLAGS = new Flags();

    private static final MessageUid messageUid1 = MessageUid.of(111);
    private static final MessageUid messageUid2 = MessageUid.of(222);

    private MessageIdManagerTestSystem testingData;
    private MessageIdManager messageIdManager;
    private Mailbox aliceMailbox1;
    private Mailbox aliceMailbox2;
    private Mailbox aliceMailbox3;
    private Mailbox bobMailbox1;
    private MailboxSession aliceSession;
    private MailboxSession bobSession;
    private MailboxSession systemSession;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    protected abstract MessageIdManagerTestSystem createTestingData() throws Exception;

    public void setUp() throws Exception {
        aliceSession = new MockMailboxSession(MailboxFixture.USER);
        bobSession = new MockMailboxSession(MailboxFixture.OTHER_USER);
        systemSession = new MockMailboxSession("systemuser", SessionType.System);
        testingData = createTestingData();
        messageIdManager = testingData.getMessageIdManager();

        aliceMailbox1 = testingData.createMailbox(MailboxFixture.MAILBOX_PATH1, aliceSession);
        aliceMailbox2 = testingData.createMailbox(MailboxFixture.MAILBOX_PATH2, aliceSession);
        aliceMailbox3 = testingData.createMailbox(MailboxFixture.MAILBOX_PATH3, aliceSession);
        bobMailbox1 = testingData.createMailbox(MailboxFixture.MAILBOX_PATH4, bobSession);
    }

    @Test
    public void getMessagesShouldReturnEmptyListWhenMessageIdNotUsed() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession))
            .isEmpty();
    }

    @Test
    public void setFlagsShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.setFlags(FLAGS, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    public void deleteMessageShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    public void setInMailboxesShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    public void getMessagesShouldReturnStoredResults() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession))
            .hasSize(1);
    }

    @Test
    public void getMessageShouldReturnOnlyMessageBelongingToCurrentUser() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)).hasSize(1);
        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, bobSession)).isEmpty();
    }

    @Test
    public void setInMailboxesShouldSetMessageInBothMailboxes() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession))
            .hasSize(2);
    }

    @Test
    public void setInMailboxesShouldNotDuplicateMessageIfSameMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession))
            .hasSize(1);
    }

    @Test
    public void setInMailboxesShouldSetHighestUidInNewMailbox() throws Exception {
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox2.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId2, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageUid uidMessage1Mailbox1 = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, aliceSession)
            .get(0)
            .getUid();
        MessageUid uidMessage2Mailbox1 = messageIdManager.getMessages(ImmutableList.of(messageId2), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox1.getMailboxId()))
            .findFirst()
            .get()
            .getUid();

        assertThat(uidMessage2Mailbox1).isGreaterThan(uidMessage1Mailbox1);
    }

    @Test
    public void setInMailboxesShouldSetHighestModSeqInNewMailbox() throws Exception {
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox2.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId2, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        long modSeqMessage1Mailbox1 = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, aliceSession)
            .get(0)
            .getModSeq();
        long modSeqMessage2Mailbox1 = messageIdManager.getMessages(ImmutableList.of(messageId2), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox1.getMailboxId()))
            .findFirst()
            .get()
            .getModSeq();

        assertThat(modSeqMessage2Mailbox1).isGreaterThan(modSeqMessage1Mailbox1);
    }

    @Test
    public void setInMailboxesShouldNotChangeUidAndModSeqInOriginalMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession).get(0);
        MessageUid messageUid1 = messageResult1.getUid();
        long modSeq1 = messageResult1.getModSeq();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        MessageResult messageResult2 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox1.getMailboxId()))
            .findFirst()
            .get();
        MessageUid messageUid2 = messageResult2.getUid();
        long modSeq2 = messageResult2.getModSeq();

        assertThat(messageUid1).isEqualTo(messageUid2);
        assertThat(modSeq1).isEqualTo(modSeq2);
    }

    @Test
    public void setInMailboxesShouldAddAndRemoveMailboxes() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox3.getMailboxId()), aliceSession);

        List<MailboxId> messageMailboxIds = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getMailboxId)
            .collect(Guavate.toImmutableList());

        assertThat(messageMailboxIds).containsOnly(aliceMailbox1.getMailboxId(), aliceMailbox3.getMailboxId());
    }

    @Test
    public void setInMailboxesShouldReplaceFlagsOfMessageInAddedMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox3.getMailboxId()), aliceSession);

        MessageResult messageResult3 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox3.getMailboxId()))
            .findFirst()
            .get();
        assertThat(messageResult3.getFlags()).isEqualTo(newFlags);
    }

    @Test
    public void setInMailboxesShouldThrowExceptionWhenSetInMailboxesInAnotherSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), bobSession);
    }

    @Test
    public void setInMailboxesShouldThrowExceptionWhenOneMailboxDoesNotBelongToMailboxSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    public void setInMailboxesShouldIgnoreMessagesBelongingToOtherUsers() throws Exception {
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    public void deleteMessageShouldRemoveMessageFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    public void deleteMessageShouldRemoveMessageOnlyFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(aliceMailbox2.getMailboxId());
    }

    @Test
    public void deleteMessageShouldNotRemoveMessageOnAnotherMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox2.getMailboxId()), aliceSession);

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(aliceMailbox1.getMailboxId());
    }

    @Test
    public void deleteMessageShouldThrowExceptionWhenDeletingOnOtherSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession);
    }

    @Test
    public void deleteMessageShouldThrowExceptionWhenDeletingOnSystemSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), systemSession);
    }

    @Test
    public void deleteMessageShouldThrowExceptionWhenOneMailboxDoesNotBelongToUser() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        messageIdManager.delete(messageId, ImmutableList.of(bobMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    public void setFlagsShouldUpdateFlags() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageResult messageResult = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession).get(0);
        assertThat(messageResult.getFlags()).isEqualTo(newFlags);
    }

    @Test
    public void setFlagsShouldNotChangeTheUid() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession).get(0);
        MessageUid messageUid1 = messageResult1.getUid();

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageResult messageResult2 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession).get(0);
        MessageUid messageUid2 = messageResult2.getUid();

        assertThat(messageUid2).isEqualTo(messageUid1);
    }

    @Test
    public void setFlagsShouldChangeTheModSeq() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        MessageResult messageResult1 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession).get(0);
        long modSeq1 = messageResult1.getModSeq();

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageResult messageResult2 = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession).get(0);
        long modSeq2 = messageResult2.getModSeq();

        assertThat(modSeq2).isGreaterThan(modSeq1);
    }

    @Test
    public void setFlagsShouldChangeFlagsInAllMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        List<Flags> flags = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(Guavate.toImmutableList());

        assertThat(flags).hasSize(2);
        assertThat(flags.get(0)).isEqualTo(newFlags);
        assertThat(flags.get(1)).isEqualTo(newFlags);
    }

    @Test
    public void setFlagsShouldNotChangeFlagsOfAnotherMessageInSameMailbox() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId2, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        List<Flags> flags = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(Guavate.toImmutableList());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldNotChangeFlagsWhenEmptyMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(), aliceSession);

        List<Flags> flags = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(Guavate.toImmutableList());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldNotChangeFlagsWhenMessageDoesNotBelongToTheMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(aliceMailbox2.getMailboxId()), aliceSession);

        List<Flags> flags = messageIdManager.getMessages(ImmutableList.of(messageId1), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(Guavate.toImmutableList());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldChangeFlagsWhenMessageBelongsToTheMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox2.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        Map<MessageId, Flags> flags = messageIdManager.getMessages(ImmutableList.of(messageId1, messageId2), FetchGroupImpl.MINIMAL, aliceSession)
            .stream()
            .collect(Guavate.toImmutableMap(MessageResult::getMessageId, MessageResult::getFlags));

        assertThat(flags).hasSize(2);
        assertThat(flags.get(messageId1)).isEqualTo(newFlags);
        assertThat(flags.get(messageId2)).isEqualTo(FLAGS);
    }

    @Test
    public void setFlagsShouldThrowExceptionWhenSetFlagsOnOtherSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession);
    }

    @Test
    public void setFlagsShouldThrowExceptionWhenSetFlagsOnSystemSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);

        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), systemSession);
    }

    @Test
    public void setFlagsShouldThrowExceptionWhenMailboxDoesNotBelongToMailboxSession() throws Exception {
        expectedException.expect(MailboxNotFoundException.class);
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()), aliceSession);

    }

    @Test
    public void getMessageShouldBeEmptyWhenMessageHasNoMoreMailboxes() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        testingData.deleteMailbox(aliceMailbox1.getMailboxId(), aliceSession);

        assertThat(messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    public void setInMailboxesShouldPreserveMessageFromOneMailboxDeletion() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox2.getMailboxId()), aliceSession);

        testingData.deleteMailbox(aliceMailbox1.getMailboxId(), aliceSession);

        List<MessageResult> messageResults = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, aliceSession);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(aliceMailbox2.getMailboxId());
    }

    @Test
    public void accessibleMessagesShouldReturnMessageIdsThatBelongsToTheUser() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(messageId), aliceSession))
            .containsExactly(messageId);
    }

    @Test
    public void accessibleMessagesShouldReturnEmptyWhenSuppliedMessageIdsAreEmpty() throws Exception {
        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(), aliceSession))
            .isEmpty();
    }

    @Test
    public void accessibleMessagesShouldFilterOutMessageIdsWhenNotExisting() throws Exception {
        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(testingData.createNotUsedMessageId()), aliceSession))
            .isEmpty();
    }

    @Test
    public void accessibleMessagesShouldFilterOutMessageIdsWhenNotBelongingToTheUser() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(messageId), bobSession))
            .isEmpty();
    }

    private Predicate<MessageResult> inMailbox(final MailboxId mailboxId) {
        return messageResult -> messageResult.getMailboxId().equals(mailboxId);
    }

}
