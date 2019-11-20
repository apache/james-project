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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.data.MapEntry;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public abstract class MessageIdMapperTest {
    private static final Username BENWA = Username.of("benwa");

    private static final char DELIMITER = '.';
    private static final int BODY_START = 16;
    protected static final long UID_VALIDITY = 42;

    private MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;
    private MessageIdMapper sut;

    private Mailbox benwaInboxMailbox;
    private Mailbox benwaWorkMailbox;
    
    protected SimpleMailboxMessage message1;
    protected SimpleMailboxMessage message2;
    protected SimpleMailboxMessage message3;
    protected SimpleMailboxMessage message4;

    private MapperProvider mapperProvider;

    protected abstract MapperProvider provideMapper();

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
        assertThat(sut.find(ImmutableList.of(), FetchType.Full)).isEmpty();
    }

    @Test
    void findShouldReturnOneMessageWhenIdListContainsOne() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1);
    }

    @Test
    void findShouldReturnMultipleMessagesWhenIdContainsMultiple() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId(), message2.getMessageId(), message3.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1, message2, message3);
    }

    @Test
    void findShouldReturnMultipleMessagesWhenIdContainsMultipleInDifferentMailboxes() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId(), message4.getMessageId(), message3.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1, message4, message3);
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
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void saveShouldSaveAMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1);
    }

    @Test
    void saveShouldThrowWhenMailboxDoesntExist() throws Exception {
        Mailbox notPersistedMailbox = new Mailbox(MailboxPath.forUser(BENWA, "mybox"), UID_VALIDITY);
        notPersistedMailbox.setMailboxId(mapperProvider.generateId());
        SimpleMailboxMessage message = createMessage(notPersistedMailbox, "Subject: Test \n\nBody\n.\n", BODY_START, new PropertyBuilder());
        message.setUid(mapperProvider.generateMessageUid());
        message.setModSeq(mapperProvider.generateModSeq(notPersistedMailbox));

        assertThatThrownBy(() -> sut.save(message))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void saveShouldSaveMessageInAnotherMailboxWhenMessageAlreadyInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void saveShouldWorkWhenSavingTwoTimesWithSameMessageIdAndSameMailboxId() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(message1.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid());
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(copiedMessage);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaInboxMailbox.getMailboxId());
    }

    @Test
    void copyInMailboxShouldThrowWhenMailboxDoesntExist() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        Mailbox notPersistedMailbox = new Mailbox(MailboxPath.forUser(BENWA, "mybox"), UID_VALIDITY);
        notPersistedMailbox.setMailboxId(mapperProvider.generateId());

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(notPersistedMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));

        assertThatThrownBy(() -> sut.copyInMailbox(message1InOtherMailbox))
            .isInstanceOf(MailboxNotFoundException.class);
    }

    @Test
    void copyInMailboxShouldSaveMessageInAnotherMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.copyInMailbox(message1InOtherMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void copyInMailboxShouldWorkWhenSavingTwoTimesWithSameMessageIdAndSameMailboxId() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid());
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));

        sut.copyInMailbox(copiedMessage);
        sut.copyInMailbox(copiedMessage);

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
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.Full);
        assertThat(messages).isEmpty();
    }

    @Test
    void deleteShouldDeleteMessageIndicesWhenStoredInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).isEmpty();
    }

    @Test
    void deleteShouldDeleteMessageIndicesWhenStoredTwoTimesInTheSameMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(message1.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid());
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(copiedMessage);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).isEmpty();
    }

    @Test
    void deleteWithMailboxIdsShouldNotDeleteIndicesWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.of());

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @Test
    void deleteWithMailboxIdsShouldDeleteOneIndexWhenMailboxIdsContainsOneElement() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).containsOnly(benwaWorkMailbox.getMailboxId());
    }

    @Test
    void deleteWithMailboxIdsShouldDeleteIndicesWhenMailboxIdsContainsMultipleElements() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.of(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId()));

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).isEmpty();
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.ADD);

        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .modSeq(modSeq)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();
        assertThat(flags).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags));
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenReplaceMode() throws Exception {
        Flags messageFlags = new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
            .build();

        message1.setUid(mapperProvider.generateMessageUid());
        message1.setFlags(messageFlags);
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED)
            .add("userflag")
            .build();

        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.REPLACE);

        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .modSeq(modSeq)
            .oldFlags(messageFlags)
            .newFlags(newFlags)
            .build();

        assertThat(flags).contains(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags));
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenRemoveMode() throws Exception {
        Flags messageFlags = new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
            .build();

        message1.setUid(mapperProvider.generateMessageUid());
        message1.setFlags(messageFlags);
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED)
            .add("userflag")
            .build();

        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.REMOVE);

        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .modSeq(modSeq)
            .oldFlags(messageFlags)
            .newFlags(new Flags(Flags.Flag.RECENT))
            .build();

        assertThat(flags).contains(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags));
    }

    @Test
    void setFlagsShouldUpdateMessageFlagsWhenRemoveMode() throws Exception {
        Flags messageFlags = new FlagsBuilder().add(Flags.Flag.RECENT, Flags.Flag.FLAGGED)
            .build();

        message1.setUid(mapperProvider.generateMessageUid());
        message1.setFlags(messageFlags);
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.FLAGGED)
            .add("userflag")
            .build();

        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isRecent()).isTrue();
        assertThat(messages.get(0).isFlagged()).isFalse();
    }

    @Test
    void setFlagsShouldReturnEmptyWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(), newFlags, FlagsUpdateMode.REMOVE);

        assertThat(flags).isEmpty();
    }

    @Test
    void setFlagsShouldReturnEmptyWhenMessageIdDoesntExist() throws Exception {
        MessageId unknownMessageId = mapperProvider.generateMessageId();
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(unknownMessageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.RECENT), FlagsUpdateMode.REMOVE);

        assertThat(flags).isEmpty();
    }

    @Test
    void setFlagsShouldAddFlagsWhenAddUpdateMode() throws Exception {
        Flags initialFlags = new Flags(Flag.RECENT);
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(initialFlags);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();

        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD);

        Flags newFlags = new FlagsBuilder()
            .add(Flag.RECENT)
            .add(Flag.ANSWERED)
            .build();
        ModSeq modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .modSeq(modSeq)
            .oldFlags(initialFlags)
            .newFlags(newFlags)
            .build();
        assertThat(flags).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags));
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenMessageIsInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1InOtherMailbox.getMailboxId()), newFlags, FlagsUpdateMode.ADD);

        ModSeq modSeqBenwaInboxMailbox = mapperProvider.highestModSeq(benwaInboxMailbox);
        ModSeq modSeqBenwaWorkMailbox = mapperProvider.highestModSeq(benwaWorkMailbox);
        UpdatedFlags expectedUpdatedFlags = UpdatedFlags.builder()
            .uid(message1.getUid())
            .modSeq(modSeqBenwaInboxMailbox)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();
        UpdatedFlags expectedUpdatedFlags2 = UpdatedFlags.builder()
            .uid(message1InOtherMailbox.getUid())
            .modSeq(modSeqBenwaWorkMailbox)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();
        assertThat(flags).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags),
                MapEntry.entry(message1InOtherMailbox.getMailboxId(), expectedUpdatedFlags2));
    }

    @Test
    void setFlagsShouldUpdateFlagsWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldNotModifyModSeqWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        sut.setFlags(messageId, ImmutableList.of(), newFlags, FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isEqualTo(modSeq);
    }

    @Test
    void setFlagsShouldUpdateModSeqWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isGreaterThan(modSeq);
    }

    @Test
    void setFlagsShouldNotModifyFlagsWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags initialFlags = new Flags(Flags.Flag.DRAFT);
        message1.setFlags(initialFlags);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        sut.setFlags(messageId, ImmutableList.of(), newFlags, FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags()).isEqualTo(initialFlags);
    }

    @Test
    void setFlagsShouldUpdateFlagsWhenMessageIsInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1InOtherMailbox.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).isAnswered()).isTrue();
        assertThat(messages.get(1).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldWorkWhenCalledOnFirstMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        message2.setUid(mapperProvider.generateMessageUid());
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);
        message3.setUid(mapperProvider.generateMessageUid());
        message3.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message3);
        message4.setUid(mapperProvider.generateMessageUid());
        message4.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message4);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldWorkWhenCalledOnDuplicatedMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        message2.setUid(mapperProvider.generateMessageUid());
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);
        message3.setUid(mapperProvider.generateMessageUid());
        message3.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message3);
        message4.setUid(mapperProvider.generateMessageUid());
        message4.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message4);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @Test
    void setFlagsShouldWorkWithConcurrencyWithAdd() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        int threadCount = 2;
        int updateCount = 10;
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> sut.setFlags(message1.getMessageId(),
                ImmutableList.of(message1.getMailboxId()),
                new Flags("custom-" + threadNumber + "-" + step),
                FlagsUpdateMode.ADD))
            .threadCount(threadCount)
            .operationCount(updateCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags().getUserFlags()).hasSize(threadCount * updateCount);
    }

    @Test
    void setFlagsShouldWorkWithConcurrencyWithRemove() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));
        message1.setUid(mapperProvider.generateMessageUid());
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
                        FlagsUpdateMode.ADD);
                } else {
                    sut.setFlags(message1.getMessageId(),
                        ImmutableList.of(message1.getMailboxId()),
                        new Flags("custom-" + threadNumber + "-" + (updateCount - step - 1)),
                        FlagsUpdateMode.REMOVE);
                }
            })
            .threadCount(threadCount)
            .operationCount(updateCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags().getUserFlags()).isEmpty();
    }

    @Test
    void countMessageShouldReturnWhenCreateNewMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(1);
    }

    @Test
    void countUnseenMessageShouldBeEmptyWhenMessageIsSeen() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldReturnWhenMessageIsNotSeen() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(1);
    }

    @Test
    void countMessageShouldBeEmptyWhenDeleteMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        sut.delete(message1.getMessageId(), ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldBeEmptyWhenDeleteMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        sut.delete(message1.getMessageId(), ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldReturnWhenDeleteMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        message2.setUid(mapperProvider.generateMessageUid());
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);

        sut.delete(message1.getMessageId(), ImmutableList.of(benwaInboxMailbox.getMailboxId()));

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(1);
    }

    @Test
    void countUnseenMessageShouldTakeCareOfMessagesMarkedAsRead() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.SEEN), FlagsUpdateMode.ADD);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    void countUnseenMessageShouldTakeCareOfMessagesMarkedAsUnread() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.SEEN), FlagsUpdateMode.REMOVE);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(1);
    }

    @Test
    void setFlagsShouldNotUpdateModSeqWhenNoop() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            new Flags(Flag.SEEN),
            FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isEqualTo(modSeq);
    }

    @Test
    void addingFlagToAMessageThatAlreadyHasThisFlagShouldResultInNoChange() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags flags = new Flags(Flag.SEEN);
        message1.setFlags(flags);
        sut.save(message1);

        sut.setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            flags,
            FlagsUpdateMode.ADD);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags()).isEqualTo(flags);
    }

    @Test
    void setFlagsShouldReturnUpdatedFlagsWhenNoop() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        ModSeq modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags flags = new Flags(Flag.SEEN);
        message1.setFlags(flags);
        sut.save(message1);

        Map<MailboxId, UpdatedFlags> mailboxIdUpdatedFlagsMap = sut.setFlags(message1.getMessageId(),
            ImmutableList.of(message1.getMailboxId()),
            flags,
            FlagsUpdateMode.ADD);

        assertThat(mailboxIdUpdatedFlagsMap)
            .containsOnly(MapEntry.entry(message1.getMailboxId(),
                UpdatedFlags.builder()
                    .modSeq(modSeq)
                    .uid(message1.getUid())
                    .newFlags(flags)
                    .oldFlags(flags)
                    .build()));
    }

    @Test
    void countUnseenMessageShouldNotTakeCareOfOtherFlagsUpdates() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.RECENT));
        sut.save(message1);

        sut.setFlags(message1.getMessageId(), ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), FlagsUpdateMode.REMOVE);

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(1);
    }

    @Test
    void deletesShouldOnlyRemoveConcernedMessages() throws Exception {
        saveMessages();

        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid());
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.copyInMailbox(copiedMessage);

        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaWorkMailbox.getMailboxId())
                .put(message2.getMessageId(), benwaInboxMailbox.getMailboxId())
                .build());

        ImmutableList<Pair<MessageId, MailboxId>> storedMessages =
            sut.find(ImmutableList.of(message1.getMessageId(), message2.getMessageId()), FetchType.Metadata)
                .stream()
                .map(message -> Pair.of(message.getMessageId(), message.getMailboxId()))
                .collect(Guavate.toImmutableList());

        assertThat(storedMessages)
            .containsOnly(Pair.of(message1.getMessageId(), benwaInboxMailbox.getMailboxId()));
    }

    @Test
    void deletesShouldUpdateMessageCount() throws Exception {
        saveMessages();

        SimpleMailboxMessage copiedMessage = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        copiedMessage.setUid(mapperProvider.generateMessageUid());
        copiedMessage.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.copyInMailbox(copiedMessage);

        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaWorkMailbox.getMailboxId())
                .put(message2.getMessageId(), benwaInboxMailbox.getMailboxId())
                .build());

        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox))
            .isEqualTo(2);
    }

    @Test
    void deletesShouldUpdateUnreadCount() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        message1.setFlags(new Flags(Flag.SEEN));
        sut.save(message1);

        message2.setUid(mapperProvider.generateMessageUid());
        message2.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message2);

        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaInboxMailbox.getMailboxId())
                .put(message2.getMessageId(), benwaInboxMailbox.getMailboxId())
                .build());

        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox))
            .isEqualTo(0);
    }

    @Test
    void deletesShouldNotFailUponMissingMessage() {
        sut.delete(
            ImmutableMultimap.<MessageId, MailboxId>builder()
                .put(message1.getMessageId(), benwaWorkMailbox.getMailboxId())
                .build());
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) throws MailboxException {
        Mailbox mailbox = new Mailbox(mailboxPath, UID_VALIDITY);
        mailbox.setMailboxId(mapperProvider.generateId());
        mailboxMapper.save(mailbox);
        return mailbox;
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
        return new SimpleMailboxMessage(mapperProvider.generateMessageId(), 
                new Date(), 
                content.length(), 
                bodyStart, 
                new SharedByteArrayInputStream(content.getBytes()), 
                new Flags(), 
                propertyBuilder, 
                mailbox.getMailboxId());
    }
}
