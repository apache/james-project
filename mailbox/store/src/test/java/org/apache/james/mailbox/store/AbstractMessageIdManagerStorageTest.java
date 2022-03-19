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

import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxACL.Right;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
    private Mailbox bobMailbox2;
    private MailboxSession aliceSession;
    private MailboxSession bobSession;
    private MailboxSession systemSession;

    protected abstract MessageIdManagerTestSystem createTestingData() throws Exception;

    @BeforeEach
    void setUp() throws Exception {
        aliceSession = MailboxSessionUtil.create(MailboxFixture.ALICE);
        bobSession = MailboxSessionUtil.create(BOB);
        systemSession = MailboxSessionUtil.create(Username.of("systemuser"));
        testingData = createTestingData();
        messageIdManager = testingData.getMessageIdManager();

        aliceMailbox1 = testingData.createMailbox(MailboxFixture.INBOX_ALICE, aliceSession);
        aliceMailbox2 = testingData.createMailbox(MailboxFixture.OUTBOX_ALICE, aliceSession);
        aliceMailbox3 = testingData.createMailbox(MailboxFixture.SENT_ALICE, aliceSession);
        bobMailbox1 = testingData.createMailbox(MailboxFixture.INBOX_BOB, bobSession);
        bobMailbox2 = testingData.createMailbox(MailboxFixture.BOB_2, bobSession);
    }

    @Test
    void getMessagesShouldReturnEmptyListWhenMessageIdNotUsed() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession))
            .isEmpty();
    }

    @Test
    void setFlagsShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.setFlags(FLAGS, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    void deleteMessageShouldReturnNotFoundWhenMessageDoesNotExist() throws MailboxException {
        MessageId messageId = testingData.createNotUsedMessageId();

        assertThat(messageIdManager.delete(messageId, bobSession))
            .isEqualTo(DeleteResult.notFound(messageId));
    }

    @Test
    void setInMailboxesShouldNotFailWhenMessageDoesNotExist() throws Exception {
        MessageId messageId = testingData.createNotUsedMessageId();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);
    }

    @Test
    void getMessagesShouldReturnStoredResults() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession))
            .hasSize(1);
    }

    @Test
    void getMessageShouldReturnOnlyMessageBelongingToCurrentUser() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)).hasSize(1);
        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession)).isEmpty();
    }

    @Test
    void setInMailboxesShouldSetMessageInBothMailboxes() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession))
            .hasSize(2);
    }

    @Test
    void setInMailboxesShouldNotDuplicateMessageIfSameMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession))
            .hasSize(1);
    }

    @Test
    void setInMailboxesShouldSetHighestUidInNewMailbox() throws Exception {
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox2.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId2, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageUid uidMessage1Mailbox1 = messageIdManager.getMessage(messageId1, FetchGroup.MINIMAL, aliceSession)
            .get(0)
            .getUid();
        MessageUid uidMessage2Mailbox1 = messageIdManager.getMessage(messageId2, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox1.getMailboxId()))
            .findFirst()
            .get()
            .getUid();

        assertThat(uidMessage2Mailbox1).isGreaterThan(uidMessage1Mailbox1);
    }

    @Test
    void setInMailboxesShouldSetHighestModSeqInNewMailbox() throws Exception {
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox2.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setInMailboxes(messageId2, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        ModSeq modSeqMessage1Mailbox1 = messageIdManager.getMessage(messageId1, FetchGroup.MINIMAL, aliceSession)
            .get(0)
            .getModSeq();
        ModSeq modSeqMessage2Mailbox1 = messageIdManager.getMessage(messageId2, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox1.getMailboxId()))
            .findFirst()
            .get()
            .getModSeq();

        assertThat(modSeqMessage2Mailbox1).isGreaterThan(modSeqMessage1Mailbox1);
    }

    @Test
    void setInMailboxesShouldNotChangeUidAndModSeqInOriginalMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageResult messageResult1 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession).get(0);
        MessageUid messageUid1 = messageResult1.getUid();
        ModSeq modSeq1 = messageResult1.getModSeq();

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        MessageResult messageResult2 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox1.getMailboxId()))
            .findFirst()
            .get();
        MessageUid messageUid2 = messageResult2.getUid();
        ModSeq modSeq2 = messageResult2.getModSeq();

        assertThat(messageUid1).isEqualTo(messageUid2);
        assertThat(modSeq1).isEqualTo(modSeq2);
    }

    @Test
    void setInMailboxesShouldAddAndRemoveMailboxes() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox3.getMailboxId()), aliceSession);

        List<MailboxId> messageMailboxIds = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getMailboxId)
            .collect(ImmutableList.toImmutableList());

        assertThat(messageMailboxIds).containsOnly(aliceMailbox1.getMailboxId(), aliceMailbox3.getMailboxId());
    }

    @Test
    void setInMailboxesShouldReplaceFlagsOfMessageInAddedMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox3.getMailboxId()), aliceSession);

        MessageResult messageResult3 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .filter(inMailbox(aliceMailbox3.getMailboxId()))
            .findFirst()
            .get();
        assertThat(messageResult3.getFlags()).isEqualTo(newFlags);
    }

    @Test
    void setInMailboxesShouldThrowExceptionWhenSetInMailboxesInAnotherSession() {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()),
                bobSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void setInMailboxesShouldThrowExceptionWhenOneMailboxDoesNotBelongToMailboxSession() {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()),
                aliceSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void setInMailboxesShouldIgnoreMessagesBelongingToOtherUsers() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        testingData.getMailboxManager().setRights(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(BOB)
                .rights(MailboxACL.FULL_RIGHTS)
                .asAddition()),
            aliceSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox1.getMailboxId(), bobMailbox2.getMailboxId()), bobSession);

        // Bob couldn't alter the message in the mailbox he could not access
        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession))
            .extracting(MessageResult::getMailboxId)
            .containsOnly(aliceMailbox2.getMailboxId());
        // Bob succeeded to copy the message in his mailboxes
        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession))
            .extracting(MessageResult::getMailboxId)
            .containsOnly(bobMailbox1.getMailboxId(), bobMailbox2.getMailboxId());
    }

    @Test
    void deleteMessageShouldRemoveMessageFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    void deleteAllMessageShouldRemoveMessageFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    void deleteMessageShouldRemoveMessageOnlyFromMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        List<MessageResult> messageResults = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(aliceMailbox2.getMailboxId());
    }

    @Test
    void deleteAllShouldRemoveMessageFromAllMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.delete(messageId, aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    void deleteMessageShouldNotRemoveMessageOnAnotherMailbox() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox2.getMailboxId()), aliceSession);

        List<MessageResult> messageResults = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(aliceMailbox1.getMailboxId());
    }

    @Test
    void deleteMessageShouldThrowExceptionWhenDeletingOnOtherSession() {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void deleteAllShouldReturnNotFoundWhenDeletingOnOtherSession() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.delete(messageId, bobSession))
            .isEqualTo(DeleteResult.notFound(messageId));
    }

    @Test
    void deleteAllShouldReturnNotDeleteWhenDeletingOnOtherSession() throws Exception {
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        messageIdManager.delete(messageId, aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession)).hasSize(1);
    }

    @Test
    void deleteMessageShouldThrowExceptionWhenDeletingOnSystemSession() {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), systemSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void deleteMessageShouldThrowExceptionWhenOneMailboxDoesNotBelongToUser() {
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        assertThatThrownBy(() -> messageIdManager.delete(messageId, ImmutableList.of(bobMailbox1.getMailboxId()), aliceSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void setFlagsShouldUpdateFlags() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageResult messageResult = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession).get(0);
        assertThat(messageResult.getFlags()).isEqualTo(newFlags);
    }

    @Test
    void setFlagsShouldNotChangeTheUid() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        MessageResult messageResult1 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession).get(0);
        MessageUid messageUid1 = messageResult1.getUid();

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageResult messageResult2 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession).get(0);
        MessageUid messageUid2 = messageResult2.getUid();

        assertThat(messageUid2).isEqualTo(messageUid1);
    }

    @Test
    void setFlagsShouldChangeTheModSeq() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        MessageResult messageResult1 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession).get(0);
        ModSeq modSeq1 = messageResult1.getModSeq();

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        MessageResult messageResult2 = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession).get(0);
        ModSeq modSeq2 = messageResult2.getModSeq();

        assertThat(modSeq2).isGreaterThan(modSeq1);
    }

    @Test
    void setFlagsShouldChangeFlagsInAllMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        List<Flags> flags = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(ImmutableList.toImmutableList());

        assertThat(flags).hasSize(2);
        assertThat(flags.get(0)).isEqualTo(newFlags);
        assertThat(flags.get(1)).isEqualTo(newFlags);
    }

    @Test
    void setFlagsShouldNotChangeFlagsOfAnotherMessageInSameMailbox() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId2, ImmutableList.of(aliceMailbox1.getMailboxId()), aliceSession);

        List<Flags> flags = messageIdManager.getMessage(messageId1, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(ImmutableList.toImmutableList());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    void setFlagsShouldNotChangeFlagsWhenEmptyMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(), aliceSession);

        List<Flags> flags = messageIdManager.getMessage(messageId1, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(ImmutableList.toImmutableList());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    void setFlagsShouldNotChangeFlagsWhenMessageDoesNotBelongToTheMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(aliceMailbox2.getMailboxId()), aliceSession);

        List<Flags> flags = messageIdManager.getMessage(messageId1, FetchGroup.MINIMAL, aliceSession)
            .stream()
            .map(MessageResult::getFlags)
            .collect(ImmutableList.toImmutableList());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0)).isEqualTo(FLAGS);
    }

    @Test
    void setFlagsShouldChangeFlagsWhenMessageBelongsToTheMailboxes() throws Exception {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId1 = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        MessageId messageId2 = testingData.persist(aliceMailbox2.getMailboxId(), messageUid2, FLAGS, aliceSession);

        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.ADD, messageId1, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        Map<MessageId, Flags> flags = messageIdManager.getMessages(ImmutableList.of(messageId1, messageId2), FetchGroup.MINIMAL, aliceSession)
            .stream()
            .collect(ImmutableMap.toImmutableMap(MessageResult::getMessageId, MessageResult::getFlags));

        assertThat(flags).hasSize(2);
        assertThat(flags.get(messageId1)).isEqualTo(newFlags);
        assertThat(flags.get(messageId2)).isEqualTo(FLAGS);
    }

    @Test
    void setFlagsShouldThrowExceptionWhenSetFlagsOnOtherSession() {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.setFlags(newFlags,
                MessageManager.FlagsUpdateMode.ADD,
                messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId()),
                bobSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void setFlagsShouldThrowExceptionWhenSetFlagsOnSystemSession() {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.setFlags(newFlags,
                MessageManager.FlagsUpdateMode.ADD,
                messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId()),
                systemSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void setFlagsShouldThrowExceptionWhenMailboxDoesNotBelongToMailboxSession() {
        Flags newFlags = new Flags(Flags.Flag.SEEN);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() -> messageIdManager.setFlags(newFlags,
                MessageManager.FlagsUpdateMode.ADD,
                messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()),
                aliceSession))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void getMessageShouldBeEmptyWhenMessageHasNoMoreMailboxes() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        testingData.deleteMailbox(aliceMailbox1.getMailboxId(), aliceSession);

        assertThat(messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession)).isEmpty();
    }

    @Test
    void setInMailboxesShouldPreserveMessageFromOneMailboxDeletion() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox2.getMailboxId()), aliceSession);

        testingData.deleteMailbox(aliceMailbox1.getMailboxId(), aliceSession);

        List<MessageResult> messageResults = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messageResults).hasSize(1);
        assertThat(messageResults.get(0).getMailboxId()).isEqualTo(aliceMailbox2.getMailboxId());
    }

    @Test
    void accessibleMessagesShouldReturnMessageIdsThatBelongsToTheUser() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(messageId), aliceSession))
            .containsExactly(messageId);
    }

    @Test
    void accessibleMessagesShouldReturnEmptyWhenSuppliedMessageIdsAreEmpty() throws Exception {
        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(), aliceSession))
            .isEmpty();
    }

    @Test
    void accessibleMessagesShouldFilterOutMessageIdsWhenNotExisting() throws Exception {
        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(testingData.createNotUsedMessageId()), aliceSession))
            .isEmpty();
    }

    @Test
    void accessibleMessagesShouldFilterOutMessageIdsWhenNotBelongingToTheUser() throws Exception {
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThat(messageIdManager.accessibleMessages(ImmutableList.of(messageId), bobSession))
            .isEmpty();
    }

    private Predicate<MessageResult> inMailbox(final MailboxId mailboxId) {
        return messageResult -> messageResult.getMailboxId().equals(mailboxId);
    }

    @Test
    void getMessagesShouldReturnMessagesWhenReadDelegated() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Read))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        List<MessageResult> messages = messageIdManager.getMessage(messageId,
            FetchGroup.MINIMAL, bobSession);

        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
    }

    @Test
    void getMessagesShouldNotReturnMessagesWhenNotReadDelegated() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.Read))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);

        assertThat(messages)
            .isEmpty();
    }

    @Test
    void setFlagsShouldUpdateFlagsWhenWriteDelegated() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Write))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        Flags newFlags = new Flags(Flags.Flag.SEEN);
        messageIdManager.setFlags(newFlags, MessageManager.FlagsUpdateMode.REPLACE,
            messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .extracting(MessageResult::getFlags)
            .containsOnly(newFlags);
    }

    @Test
    void setFlagsShouldNotUpdateFlagsWhenNotWriteDelegated() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.Write))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() ->
            messageIdManager.setFlags(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.REPLACE,
                messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession))
            .isInstanceOf(MailboxNotFoundException.class);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .extracting(MessageResult::getFlags)
            .containsOnly(FLAGS);
    }

    @Test
    void setInMailboxesShouldAllowCopyingMessageFromReadOnlySharedMailbox() throws Exception {
        //Given
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Read))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        //When
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()), bobSession);

        //Then
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);
        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId, messageId);
        assertThat(messages)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId());
    }

    @Test
    void setInMailboxesShouldDenyCopyingMessageFromNotReadSharedMailbox() throws Exception {
        //Given
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.Read))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        //When
        assertThatThrownBy(() ->
            messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()),
                bobSession))
            .isInstanceOf(MailboxNotFoundException.class);

        //Then
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);
        assertThat(messages)
            .isEmpty();
    }

    @Test
    void setInMailboxesShouldAllowCopyingToAInsertSharedMailbox() throws Exception {
        //Given
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Insert, Right.Read))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        //When
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()), bobSession);

        //Then
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
        assertThat(messages)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(aliceMailbox1.getMailboxId());
    }

    @Test
    void setInMailboxesShouldDenyCopyingToANonInsertSharedMailbox() throws Exception {
        //Given
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.Insert))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, FLAGS, bobSession);

        //When
        assertThatThrownBy(() ->
            messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(aliceMailbox1.getMailboxId(), bobMailbox1.getMailboxId()),
                bobSession))
            .isInstanceOf(MailboxNotFoundException.class);

        //Then
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);
        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
        assertThat(messages)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(bobMailbox1.getMailboxId());
    }

    @Test
    void setInMailboxesShouldAllowMovingMessagesFromASharedMailboxWhenDeleteRight() throws Exception {
        //Given
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Lookup, Right.Read, Right.DeleteMessages))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        //When
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(bobMailbox1.getMailboxId()), bobSession);

        //Then
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);
        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
        assertThat(messages)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(bobMailbox1.getMailboxId());
    }

    @Test
    void setInMailboxesShouldDenyMovingMessagesFromASharedMailboxWhenNoDeleteRight() throws Exception {
        //Given
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.DeleteMessages))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        //When
        assertThatThrownBy(() ->
            messageIdManager.setInMailboxes(messageId,
                ImmutableList.of(bobMailbox1.getMailboxId()),
                bobSession))
            .isInstanceOf(MailboxNotFoundException.class);

        //Then
        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);
        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
        assertThat(messages)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(aliceMailbox1.getMailboxId());
    }

    @Test
    void setInMailboxShouldAllowDistinctMailboxSetForShareeAndOwner() throws Exception {
        //Given
        testingData.setACL(aliceMailbox2.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(MailboxACL.FULL_RIGHTS)
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);
        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()), aliceSession);

        //When
        messageIdManager.setInMailboxes(messageId,
            ImmutableList.of(bobMailbox1.getMailboxId(), aliceMailbox2.getMailboxId()),
            bobSession);

        //Then
        List<MessageResult> messagesForSharee = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, bobSession);
        assertThat(messagesForSharee)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId, messageId);
        assertThat(messagesForSharee)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(aliceMailbox2.getMailboxId(), bobMailbox1.getMailboxId());

        List<MessageResult> messagesForOwner = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messagesForOwner)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId, messageId);
        assertThat(messagesForOwner)
            .extracting(MessageResult::getMailboxId)
            .containsOnly(aliceMailbox1.getMailboxId(), aliceMailbox2.getMailboxId());
    }

    @Test
    void deleteShouldRemoveMessagesFromSharedMailboxWhenAllowed() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Read, Right.Lookup, Right.DeleteMessages))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .isEmpty();
    }

    @Test
    void deleteShouldNotRemoveMessagesFromSharedMailboxWhenNotAllowed() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.DeleteMessages))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        assertThatThrownBy(() ->
            messageIdManager.delete(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession))
            .isInstanceOf(MailboxNotFoundException.class);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .extracting(MessageResult::getMessageId)
            .containsOnly(messageId);
    }

    @Test
    void accessibleMessagesShouldReturnMessagesWhenReadDelegated() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Read, Right.Lookup))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        Set<MessageId> messages = messageIdManager.accessibleMessages(ImmutableList.of(messageId), bobSession);

        assertThat(messages)
            .containsOnly(messageId);
    }

    @Test
    void accessibleMessagesShouldNotReturnMessagesWhenNotReadDelegated() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(Rfc4314Rights.allExcept(Right.Read))
                    .asAddition()),
            aliceSession);
        MessageId messageId = testingData.persist(aliceMailbox1.getMailboxId(), messageUid1, FLAGS, aliceSession);

        Set<MessageId> messages = messageIdManager.accessibleMessages(ImmutableList.of(messageId), bobSession);

        assertThat(messages)
            .isEmpty();
    }

    @Test
    void setInMailboxesShouldSanitizeFlagsWhenNoWriteRight() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Read, Right.Lookup, Right.Insert))
                    .asAddition()),
            aliceSession);
        Flags flags = new Flags(Flags.Flag.FLAGGED);
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, flags, bobSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .extracting(MessageResult::getFlags)
            .containsOnly(new Flags());
    }

    @Test
    void setInMailboxesShouldPreserveFlagsWhenWriteRight() throws Exception {
        testingData.setACL(aliceMailbox1.getMailboxId(),
            MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .forUser(BOB)
                    .rights(new Rfc4314Rights(Right.Read, Right.Lookup, Right.Insert, Right.Write))
                    .asAddition()),
            aliceSession);
        Flags flags = new Flags(Flags.Flag.FLAGGED);
        MessageId messageId = testingData.persist(bobMailbox1.getMailboxId(), messageUid1, flags, bobSession);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(aliceMailbox1.getMailboxId()), bobSession);

        List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, aliceSession);
        assertThat(messages)
            .extracting(MessageResult::getFlags)
            .containsOnly(flags);
    }
}
