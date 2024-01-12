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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMessageAssert.assertMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.UpdatableTickingClock;
import org.assertj.core.data.MapEntry;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public abstract class MessageIdMapperTest {
    private static final Username BENWA = Username.of("benwa");

    private static final char DELIMITER = '.';
    private static final int BODY_START = 16;
    protected static final UidValidity UID_VALIDITY = UidValidity.of(42);

    private MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;
    protected MessageIdMapper sut;

    protected Mailbox benwaInboxMailbox;
    private Mailbox benwaWorkMailbox;
    
    protected SimpleMailboxMessage message1;
    protected SimpleMailboxMessage message2;
    protected SimpleMailboxMessage message3;
    protected SimpleMailboxMessage message4;

    protected MapperProvider mapperProvider;

    protected abstract MapperProvider provideMapper();

    protected abstract UpdatableTickingClock updatableTickingClock();

    @BeforeEach
    void setUp() throws MailboxException {
        this.mapperProvider = provideMapper();
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.UNIQUE_MESSAGE_ID));

        this.sut = mapperProvider.createMessageIdMapper();
        this.messageMapper = mapperProvider.createMessageMapper();
        this.mailboxMapper = mapperProvider.createMailboxMapper();

        benwaInboxMailbox = createMailbox(MailboxPath.forUser(BENWA, "INBOX"));
        benwaWorkMailbox = createMailbox(MailboxPath.forUser(BENWA, "INBOX" + DELIMITER + "work"));

        message1 = createMessage(benwaInboxMailbox, "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
        message2 = createMessage(benwaInboxMailbox, "Subject: Test2 \n\nBody2\n.\n", BODY_START, new PropertyBuilder());
        message3 = createMessage(benwaInboxMailbox, "Subject: Test3 \n\nBody3\n.\n", BODY_START, new PropertyBuilder());
        message4 = createMessage(benwaWorkMailbox, "Subject: Test4 \n\nBody4\n.\n", BODY_START, new PropertyBuilder());
    }

    @Test
    void findShouldReturnEmptyWhenIdListIsEmpty() {
        assertThat(sut.find(ImmutableList.of(), FetchType.FULL)).isEmpty();
    }

    @Test
    void findShouldReturnOneMessageWhenIdListContainsOne() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.FULL);
        assertMessages(messages).containOnly(message1);
    }

    @Test
    void findShouldReturnMultipleMessagesWhenIdContainsMultiple() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId(), message2.getMessageId(), message3.getMessageId()), FetchType.FULL);
        assertMessages(messages).containOnly(message1, message2, message3);
    }

    @Test
    void findShouldReturnMultipleMessagesWhenIdContainsMultipleInDifferentMailboxes() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId(), message4.getMessageId(), message3.getMessageId()), FetchType.FULL);
        assertMessages(messages).containOnly(message1, message4, message3);
    }

    @Test
    void findMailboxesShouldReturnEmptyWhenMessageDoesntExist() {
        assertThat(sut.findMailboxes(mapperProvider.generateMessageId())).isEmpty();
    }

    @Test
    void findMailboxesShouldReturnOneMailboxWhenMessageExistsInOneMailbox() throws MailboxException {
        saveMessages();
        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId());
    }

    @Test
    void findMailboxesShouldReturnTwoMailboxesWhenMessageExistsInTwoMailboxes() throws MailboxException {
        saveMessages();

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void saveShouldSaveAMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.FULL);
        assertMessages(messages).containOnly(message1);
    }

    @Test
    void saveShouldThrowWhenMailboxDoesntExist() throws Exception {
        Mailbox notPersistedMailbox = new Mailbox(MailboxPath.forUser(BENWA, "mybox"), UID_VALIDITY, mapperProvider.generateId());
        SimpleMailboxMessage message = createMessage(notPersistedMailbox, "Subject: Test \n\nBody\n.\n", BODY_START, new PropertyBuilder());
        message.setUid(mapperProvider.generateMessageUid(notPersistedMailbox));
        message.setModSeq(mapperProvider.generateModSeq(notPersistedMailbox));

        assertThatThrownBy(() -> sut.save(message))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void saveShouldSaveMessageInAnotherMailboxWhenMessageAlreadyInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void saveShouldWorkWhenSavingTwoTimesWithSameMessageIdAndSameMailboxId() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(message1.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(copiedMessage);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaInboxMailbox.getMailboxId());
    }

    @Test
    void copyInMailboxShouldSaveMessageInAnotherMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MailboxMessage message1InOtherMailbox = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA).get(0)
            .copy(benwaWorkMailbox);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.copyInMailbox(message1InOtherMailbox, benwaWorkMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void copyInMailboxShouldWorkWhenSavingTwoTimesWithSameMessageIdAndSameMailboxId() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        MailboxMessage copiedMessage = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA).get(0)
            .copy(benwaWorkMailbox);
        copiedMessage.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));

        sut.copyInMailbox(copiedMessage, benwaWorkMailbox);
        sut.copyInMailbox(copiedMessage, benwaWorkMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes)
            .containsOnly(
                benwaInboxMailbox.getMailboxId(),
                benwaWorkMailbox.getMailboxId());
    }

    @Test
    void deleteShouldNotThrowWhenUnknownMessage() {
        sut.delete(message1.getMessageId());
    }

    @Test
    void deleteShouldDeleteAMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).isEmpty();
    }

    @Test
    void deleteShouldDeleteMessageIndicesWhenStoredInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).isEmpty();
    }

    @Test
    void deleteShouldDeleteMessageIndicesWhenStoredTwoTimesInTheSameMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(message1.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(copiedMessage);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).isEmpty();
    }

    @Test
    void deleteWithMailboxIdsShouldNotDeleteIndicesWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.of());

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void deleteWithMailboxIdsShouldDeleteOneIndexWhenMailboxIdsContainsOneElement() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).containsOnly(benwaWorkMailbox.getMailboxId());
    }

    @Test
    void deleteWithMailboxIdsShouldDeleteIndicesWhenMailboxIdsContainsMultipleElements() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.of(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId()));

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).isEmpty();
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.ADD).block();

        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .messageId(messageId)
            .modSeq(modSeq)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();
        assertThat(flags.asMap()).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), ImmutableList.of(expectedUpdatedFlags)));
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenReplaceMode() throws Exception {
        Flags messageFlags = new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
            .build();

        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setFlags(messageFlags);
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED)
            .add("userflag")
            .build();

        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.REPLACE).block();

        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .messageId(messageId)
            .modSeq(modSeq)
            .oldFlags(messageFlags)
            .newFlags(newFlags)
            .build();

        assertThat(flags.asMap())
            .contains(MapEntry.entry(benwaInboxMailbox.getMailboxId(), ImmutableList.of(expectedUpdatedFlags)));
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenRemoveMode() throws Exception {
        Flags messageFlags = new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
            .build();

        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setFlags(messageFlags);
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED)
            .add("userflag")
            .build();

        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.REMOVE).block();

        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .messageId(messageId)
            .modSeq(modSeq)
            .oldFlags(messageFlags)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .build();

        assertThat(flags.asMap())
            .contains(MapEntry.entry(benwaInboxMailbox.getMailboxId(), ImmutableList.of(expectedUpdatedFlags)));
    }

    @Test
    void setFlagsShouldUpdateMessageFlagsWhenRemoveMode() throws Exception {
        Flags messageFlags = new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
            .build();

        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setFlags(messageFlags);
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED)
            .add("userflag")
            .build();

        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.REMOVE).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isRecent()).isTrue();
        assertThat(messages.get(0).isFlagged()).isFalse();
    }

    @Test
    void setFlagsShouldReturnEmptyWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(), newFlags, FlagsUpdateMode.REMOVE).block();

        assertThat(flags.asMap()).isEmpty();
    }

    @Test
    void setFlagsShouldReturnEmptyWhenMessageIdDoesntExist() throws Exception {
        MessageId unknownMessageId = mapperProvider.generateMessageId();
        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(unknownMessageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.RECENT), FlagsUpdateMode.REMOVE).block();

        assertThat(flags.asMap()).isEmpty();
    }

    @Test
    void setFlagsShouldAddFlagsWhenAddUpdateMode() throws Exception {
        Flags initialFlags = new Flags(Flag.RECENT);
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(initialFlags);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();

        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        Flags newFlags = new FlagsBuilder()
            .add(Flag.RECENT)
            .add(Flag.ANSWERED)
            .build();
        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .messageId(messageId)
            .modSeq(modSeq)
            .oldFlags(initialFlags)
            .newFlags(newFlags)
            .build();
        assertThat(flags.asMap())
            .containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), ImmutableList.of(expectedUpdatedFlags)));
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenMessageIsInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Multimap<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1InOtherMailbox.getMailboxId()), newFlags, FlagsUpdateMode.ADD).block();

        ModSeq modSeqBenwaInboxMailbox = mapperProvider.highestModSeq(benwaInboxMailbox);
        ModSeq modSeqBenwaWorkMailbox = mapperProvider.highestModSeq(benwaWorkMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .messageId(messageId)
            .modSeq(modSeqBenwaInboxMailbox)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();
        UpdatedFlags expectedUpdatedFlags2 = UpdatedFlags.builder()
            .uid(message1InOtherMailbox.getUid())
            .messageId(messageId)
            .modSeq(modSeqBenwaWorkMailbox)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();
        assertThat(flags.asMap())
            .containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), ImmutableList.of(expectedUpdatedFlags)),
                MapEntry.entry(message1InOtherMailbox.getMailboxId(), ImmutableList.of(expectedUpdatedFlags2)));
    }

    @Test
    void setFlagsShouldUpdateFlagsWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldNotModifyModSeqWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        sut.setFlags(messageId, ImmutableList.of(), newFlags, FlagsUpdateMode.REMOVE).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isEqualTo(modSeq);
    }

    @Test
    void setFlagsShouldUpdateModSeqWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isGreaterThan(modSeq);
    }

    @Test
    void setFlagsShouldNotModifyFlagsWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags initialFlags = new Flags(Flags.Flag.DRAFT);
        message1.setFlags(initialFlags);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        sut.setFlags(messageId, ImmutableList.of(), newFlags, FlagsUpdateMode.REMOVE).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags()).isEqualTo(initialFlags);
    }

    @Test
    void setFlagsShouldUpdateFlagsWhenMessageIsInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1InOtherMailbox.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).isAnswered()).isTrue();
        assertThat(messages.get(1).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldWorkWhenCalledOnFirstMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        message2.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);
        message3.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message3.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message3);
        message4.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message4.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message4);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldWorkWhenCalledOnDuplicatedMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        message2.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);
        message3.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message3.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message3);
        message4.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message4.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message4);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @Test
    public void setFlagsShouldWorkWithConcurrencyWithAdd() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        int threadCount = 2;
        int updateCount = 10;
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> sut.setFlags(message1.getMessageId(),
                ImmutableList.of(message1.getMailboxId()),
                new Flags("custom-" + threadNumber + "-" + step),
                FlagsUpdateMode.ADD).block())
            .threadCount(threadCount)
            .operationCount(updateCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags().getUserFlags()).hasSize(threadCount * updateCount);
    }

    @Test
    public void setFlagsShouldWorkWithConcurrencyWithRemove() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        int threadCount = 4;
        int updateCount = 20;
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                if (step  < updateCount / 2) {
                    sut.setFlags(message1.getMessageId(),
                        ImmutableList.of(message1.getMailboxId()),
                        new Flags("custom-" + threadNumber + "-" + step),
                        FlagsUpdateMode.ADD)
                        .block();
                } else {
                    sut.setFlags(message1.getMessageId(),
                        ImmutableList.of(message1.getMailboxId()),
                        new Flags("custom-" + threadNumber + "-" + (updateCount - step - 1)),
                        FlagsUpdateMode.REMOVE)
                        .block();
                }
            })
            .threadCount(threadCount)
            .operationCount(updateCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags().getUserFlags()).isEmpty();
    }

    @Test
    void countMessageShouldReturnWhenCreateNewMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(1);
    }

    @Test
    void countUnseenMessageShouldBeEmptyWhenMessageIsSeen() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldReturnWhenMessageIsNotSeen() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(1);
    }

    @Test
    void countMessageShouldBeEmptyWhenDeleteMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        sut.delete(message1.getMessageId(), ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldBeEmptyWhenDeleteMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        sut.delete(message1.getMessageId(), ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldReturnWhenDeleteMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        message2.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);

        sut.delete(message1.getMessageId(), ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(1);
    }

    @Test
    void countUnseenMessageShouldTakeCareOfMessagesMarkedAsRead() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.SEEN), FlagsUpdateMode.ADD).block();

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldTakeCareOfMessagesMarkedAsUnread() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.SEEN), FlagsUpdateMode.REMOVE).block();

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(1);
    }

    @Test
    void setFlagsShouldNotUpdateModSeqWhenNoop() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            new Flags(Flag.SEEN),
            FlagsUpdateMode.ADD)
            .block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isEqualTo(modSeq);
    }

    @Test
    void addingFlagToAMessageThatAlreadyHasThisFlagShouldResultInNoChange() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags flags = new Flags(Flag.SEEN);
        message1.setFlags(flags);
        sut.save(message1);

        sut.setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            flags,
            FlagsUpdateMode.ADD)
            .block();

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.FULL);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags()).isEqualTo(flags);
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenNoop() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags flags = new Flags(Flag.SEEN);
        message1.setFlags(flags);
        sut.save(message1);

        Multimap<MailboxId, UpdatedFlags> mailboxIdUpdatedFlagsMap = sut.setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            flags,
            FlagsUpdateMode.ADD)
            .block();

        assertThat(mailboxIdUpdatedFlagsMap.asMap())
            .containsOnly(MapEntry.entry(message1.getMailboxId(),
                ImmutableList.of(UpdatedFlags.builder()
                    .modSeq(modSeq)
                    .uid(message1.getUid())
                    .messageId(message1.getMessageId())
                    .newFlags(flags)
                    .oldFlags(flags)
                    .build())));
    }

    @Test
    void countUnseenMessageShouldNotTakeCareOfOtherFlagsUpdates() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.RECENT));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.REMOVE).block();

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(1);
    }

    @Test
    void deletesShouldOnlyRemoveConcernedMessages() throws Exception {
        saveMessages();

        MailboxMessage copiedMessage = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA).get(0);
        copiedMessage.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.copyInMailbox(copiedMessage, benwaWorkMailbox);

        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaWorkMailbox.getMailboxId())
                .put(message2.getMessageId(), benwaInboxMailbox.getMailboxId())
                .build());

        ImmutableList<Pair<MessageId, MailboxId>> storedMessages =
            sut.find(ImmutableList.of(message1.getMessageId(), message2.getMessageId()), FetchType.METADATA)
                .stream()
                .map(message -> Pair.of(message.getMessageId(), message.getMailboxId()))
                .collect(ImmutableList.toImmutableList());

        assertThat(storedMessages)
            .containsOnly(Pair.of(message1.getMessageId(), benwaInboxMailbox.getMailboxId()));
    }

    @Test
    void deletesShouldUpdateMessageCount() throws Exception {
        saveMessages();

        MailboxMessage copiedMessage = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA).get(0);
        copiedMessage.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.copyInMailbox(copiedMessage, benwaWorkMailbox);

        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaWorkMailbox.getMailboxId())
                .put(message2.getMessageId(), benwaInboxMailbox.getMailboxId())
                .build());

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox))
            .isEqualTo(2);
    }

    @Test
    void setFlagsShouldUpdateTwoMessagesInTheSameMailboxWithTheSameMessageId() throws Exception {
        addMessageAndSetModSeq(benwaInboxMailbox, message1);
        addMessageAndSetModSeq(benwaInboxMailbox, message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        assertThat(sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA))
            .extracting(MailboxMessage::createFlags)
            .containsExactly(
                new Flags(Flag.ANSWERED),
                new Flags(Flag.ANSWERED));
    }

    @Test
    void setFlagsShouldReturnAllUp() throws Exception {
        addMessageAndSetModSeq(benwaInboxMailbox, message1);
        addMessageAndSetModSeq(benwaInboxMailbox, message1);

        Multimap<MailboxId, UpdatedFlags> map = sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD).block();

        assertThat(map.values()).hasSize(2);
        assertThat(map.asMap()).hasSize(1);
    }

    @Test
    void deletesShouldUpdateUnreadCount() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        message2.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);

        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaInboxMailbox.getMailboxId())
                .put(message2.getMessageId(), benwaInboxMailbox.getMailboxId())
                .build());

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen())
            .isEqualTo(0);
    }

    @Test
    void deletesShouldNotFailUponMissingMessage() {
        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaWorkMailbox.getMailboxId())
                .build());
    }

    @Nested
    class SaveDateTests {
        @Test
        void saveMessagesShouldSetNewSaveDate() throws MailboxException {
            message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
            message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
            message1.setFlags(new Flags(Flag.SEEN));

            message2.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
            message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));

            sut.save(message1);
            updatableTickingClock().setInstant(updatableTickingClock().instant().plusSeconds(1000));
            sut.save(message2);

            MailboxMessage firstMessage = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA).get(0);
            MailboxMessage secondMessage = sut.find(ImmutableList.of(message2.getMessageId()), FetchType.METADATA).get(0);

            assertThat(firstMessage.getSaveDate()).isNotEqualTo(secondMessage.getSaveDate());
        }

        @Test
        void copyInMailboxReactiveShouldSetNewSaveDate() throws MailboxException, InterruptedException {
            message1.setUid(mapperProvider.generateMessageUid(benwaInboxMailbox));
            message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
            message1.setFlags(new Flags(Flag.SEEN));
            sut.save(message1);

            MailboxMessage copy = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA).get(0)
                .copy(benwaWorkMailbox);
            copy.setUid(mapperProvider.generateMessageUid(benwaWorkMailbox));
            copy.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));

            updatableTickingClock().setInstant(updatableTickingClock().instant().plus(8, ChronoUnit.DAYS));

            sut.copyInMailboxReactive(copy, benwaWorkMailbox).block();

            List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.METADATA);
            MailboxMessage firstMessage = messages.get(0);
            MailboxMessage secondMessage = messages.get(1);

            assertThat(firstMessage.getSaveDate()).isNotEqualTo(secondMessage.getSaveDate());
        }
    }


    private Mailbox createMailbox(MailboxPath mailboxPath) throws MailboxException {
        return mailboxMapper.create(mailboxPath, UID_VALIDITY).block();
    }
    
    protected void saveMessages() throws MailboxException {
        addMessageAndSetModSeq(benwaInboxMailbox, message1);
        addMessageAndSetModSeq(benwaInboxMailbox, message2);
        addMessageAndSetModSeq(benwaInboxMailbox, message3);
        addMessageAndSetModSeq(benwaWorkMailbox, message4);
    }

    private void addMessageAndSetModSeq(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        messageMapper.add(mailbox, message);
        message1.setModSeq(mapperProvider.generateModSeq(mailbox));
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        MessageId messageId = mapperProvider.generateMessageId();
        ThreadId threadId = ThreadId.fromBaseMessageId(messageId);
        return new SimpleMailboxMessage(messageId,
                threadId,
                new Date(), 
                content.length(), 
                bodyStart, 
                new ByteContent(content.getBytes()),
                new Flags(), 
                propertyBuilder.build(),
                mailbox.getMailboxId());
    }
}
