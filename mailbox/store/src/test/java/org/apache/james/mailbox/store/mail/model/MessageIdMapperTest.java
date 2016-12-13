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

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.assertj.core.data.MapEntry;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractTest;
import org.xenei.junit.contract.IProducer;

import com.google.common.collect.ImmutableList;

@Contract(MapperProvider.class)
public class MessageIdMapperTest<T extends MapperProvider> {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private final static char DELIMITER = '.';
    private static final int BODY_START = 16;
    private final static long UID_VALIDITY = 42;

    private IProducer<T> producer;
    private MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;
    private MessageIdMapper sut;

    private SimpleMailbox benwaInboxMailbox;
    private SimpleMailbox benwaWorkMailbox;
    
    private SimpleMailboxMessage message1;
    private SimpleMailboxMessage message2;
    private SimpleMailboxMessage message3;
    private SimpleMailboxMessage message4;

    @Rule
    public ExpectedException expected = ExpectedException.none();
    private T mapperProvider;

    @Contract.Inject
    public final void setProducer(IProducer<T> producer) throws MailboxException {
        this.producer = producer;
        this.mapperProvider = producer.newInstance();
        Assume.assumeFalse(mapperProvider.getNotImplemented().contains(MapperProvider.Capabilities.UNIQUE_MESSAGE_ID));

        this.mapperProvider.ensureMapperPrepared();
        this.sut = mapperProvider.createMessageIdMapper();
        this.messageMapper = mapperProvider.createMessageMapper();
        this.mailboxMapper = mapperProvider.createMailboxMapper();

        benwaInboxMailbox = createMailbox(new MailboxPath("#private", "benwa", "INBOX"));
        benwaWorkMailbox = createMailbox( new MailboxPath("#private", "benwa", "INBOX"+DELIMITER+"work"));

        message1 = createMessage(benwaInboxMailbox, "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
        message2 = createMessage(benwaInboxMailbox, "Subject: Test2 \n\nBody2\n.\n", BODY_START, new PropertyBuilder());
        message3 = createMessage(benwaInboxMailbox, "Subject: Test3 \n\nBody3\n.\n", BODY_START, new PropertyBuilder());
        message4 = createMessage(benwaWorkMailbox, "Subject: Test4 \n\nBody4\n.\n", BODY_START, new PropertyBuilder());
    }

    @After
    public void tearDown() throws MailboxException {
        producer.cleanUp();
    }

    @ContractTest
    public void findShouldReturnEmptyWhenIdListIsEmpty() throws MailboxException {
        assertThat(sut.find(ImmutableList.<MessageId> of(), FetchType.Full)).isEmpty();
    }

    @ContractTest
    public void findShouldReturnOneMessageWhenIdListContainsOne() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1);
    }

    @ContractTest
    public void findShouldReturnMultipleMessagesWhenIdContainsMultiple() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId(), message2.getMessageId(), message3.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1, message2, message3);
    }

    @ContractTest
    public void findShouldReturnMultipleMessagesWhenIdContainsMultipleInDifferentMailboxes() throws MailboxException {
        saveMessages();
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId(), message4.getMessageId(), message3.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1, message4, message3);
    }

    @ContractTest
    public void findMailboxesShouldReturnEmptyWhenMessageDoesntExist() throws MailboxException {
        assertThat(sut.findMailboxes(mapperProvider.generateMessageId())).isEmpty();
    }

    @ContractTest
    public void findMailboxesShouldReturnOneMailboxWhenMessageExistsInOneMailbox() throws MailboxException {
        saveMessages();
        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId());
    }

    @ContractTest
    public void findMailboxesShouldReturnTwoMailboxesWhenMessageExistsInTwoMailboxes() throws MailboxException {
        saveMessages();

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        List<MailboxId> mailboxes = sut.findMailboxes(message1.getMessageId());
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @ContractTest
    public void saveShouldSaveAMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);
        List<MailboxMessage> messages = sut.find(ImmutableList.of(message1.getMessageId()), FetchType.Full);
        assertThat(messages).containsOnly(message1);
    }

    @ContractTest
    public void saveShouldThrowWhenMailboxDoesntExist() throws Exception {
        SimpleMailbox notPersistedMailbox = new SimpleMailbox(new MailboxPath("#private", "benwa", "mybox"), UID_VALIDITY);
        notPersistedMailbox.setMailboxId(mapperProvider.generateId());
        SimpleMailboxMessage message = createMessage(notPersistedMailbox, "Subject: Test \n\nBody\n.\n", BODY_START, new PropertyBuilder());
        message.setUid(mapperProvider.generateMessageUid());
        message.setModSeq(mapperProvider.generateModSeq(notPersistedMailbox));

        expectedException.expect(MailboxNotFoundException.class);
        sut.save(message);
    }

    @ContractTest
    public void saveShouldSaveMessageInAnotherMailboxWhenMessageAlreadyInOneMailbox() throws Exception {
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

    @ContractTest
    public void saveShouldWorkWhenSavingTwoTimesWithSameMessageIdAndSameMailboxId() throws Exception {
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

    @ContractTest
    public void deleteShouldNotThrowWhenUnknownMessage() {
        sut.delete(message1.getMessageId());
    }

    @ContractTest
    public void deleteShouldDeleteAMessage() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), FetchType.Full);
        assertThat(messages).isEmpty();
    }

    @ContractTest
    public void deleteShouldDeleteMessageIndicesWhenStoredInTwoMailboxes() throws Exception {
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

    @ContractTest
    public void deleteShouldDeleteMessageIndicesWhenStoredTwoTimesInTheSameMailbox() throws Exception {
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

    @ContractTest
    public void deleteWithMailboxIdsShouldNotDeleteIndicesWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.delete(messageId, ImmutableList.<MailboxId> of());

        List<MailboxId> mailboxes = sut.findMailboxes(messageId);
        assertThat(mailboxes).containsOnly(benwaInboxMailbox.getMailboxId(), benwaWorkMailbox.getMailboxId());
    }

    @ContractTest
    public void deleteWithMailboxIdsShouldDeleteOneIndexWhenMailboxIdsContainsOneElement() throws Exception {
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

    @ContractTest
    public void deleteWithMailboxIdsShouldDeleteIndicesWhenMailboxIdsContainsMultipleElements() throws Exception {
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

    @ContractTest
    public void setFlagsShouldReturnUpdatedFlagsWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, MessageManager.FlagsUpdateMode.REMOVE);

        long modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = new UpdatedFlags(message1.getUid(), modSeq, new Flags(), newFlags);
        assertThat(flags).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags));
    }

    @ContractTest
    public void setFlagsShouldReturnEmptyWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.<MailboxId> of(), newFlags, MessageManager.FlagsUpdateMode.REMOVE);

        assertThat(flags).isEmpty();
    }

    @ContractTest
    public void setFlagsShouldReturnEmptyWhenMessageIdDoesntExist() throws Exception {
        MessageId unknownMessageId = mapperProvider.generateMessageId();
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(unknownMessageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.RECENT), MessageManager.FlagsUpdateMode.REMOVE);

        assertThat(flags).isEmpty();
    }

    @ContractTest
    public void setFlagsShouldAddFlagsWhenAddUpdateMode() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags initialFlags = new Flags(Flag.RECENT);
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), initialFlags, MessageManager.FlagsUpdateMode.REMOVE);

        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), MessageManager.FlagsUpdateMode.ADD);

        Flags newFlags = new FlagsBuilder()
            .add(Flag.RECENT)
            .add(Flag.ANSWERED)
            .build();
        long modSeq = mapperProvider.highestModSeq(benwaInboxMailbox);
        UpdatedFlags expectedUpdatedFlags = new UpdatedFlags(message1.getUid(), modSeq, initialFlags, newFlags);
        assertThat(flags).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags));
    }

    @ContractTest
    public void setFlagsShouldReturnUpdatedFlagsWhenMessageIsInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        Map<MailboxId, UpdatedFlags> flags = sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), newFlags, MessageManager.FlagsUpdateMode.REMOVE);

        long modSeqBenwaInboxMailbox = mapperProvider.highestModSeq(benwaInboxMailbox);
        long modSeqBenwaWorkMailbox = mapperProvider.highestModSeq(benwaWorkMailbox);
        UpdatedFlags expectedUpdatedFlags = new UpdatedFlags(message1.getUid(), modSeqBenwaInboxMailbox, new Flags(), newFlags);
        UpdatedFlags expectedUpdatedFlags2 = new UpdatedFlags(message1InOtherMailbox.getUid(), modSeqBenwaWorkMailbox, new Flags(), newFlags);
        assertThat(flags).containsOnly(MapEntry.entry(benwaInboxMailbox.getMailboxId(), expectedUpdatedFlags),
                MapEntry.entry(benwaWorkMailbox.getMailboxId(), expectedUpdatedFlags2));
    }

    @ContractTest
    public void setFlagsShouldUpdateFlagsWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), MessageManager.FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).isAnswered()).isTrue();
    }

    @ContractTest
    public void setFlagsShouldNotModifyModSeqWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        long modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        sut.setFlags(messageId, ImmutableList.<MailboxId> of(), newFlags, MessageManager.FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isGreaterThan(modSeq);
    }

    @ContractTest
    public void setFlagsShouldUpdateModSeqWhenMessageIsInOneMailbox() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        long modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId()), new Flags(Flag.ANSWERED), MessageManager.FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getModSeq()).isGreaterThan(modSeq);
    }

    @ContractTest
    public void setFlagsShouldNotModifyFlagsWhenMailboxIdsIsEmpty() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        long modSeq = mapperProvider.generateModSeq(benwaInboxMailbox);
        message1.setModSeq(modSeq);
        Flags initialFlags = new Flags(Flags.Flag.DRAFT);
        message1.setFlags(initialFlags);
        sut.save(message1);

        MessageId messageId = message1.getMessageId();
        Flags newFlags = new Flags(Flag.ANSWERED);
        sut.setFlags(messageId, ImmutableList.<MailboxId> of(), newFlags, MessageManager.FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).createFlags()).isEqualTo(initialFlags);
    }

    @ContractTest
    public void setFlagsShouldUpdateFlagsWhenMessageIsInTwoMailboxes() throws Exception {
        message1.setUid(mapperProvider.generateMessageUid());
        message1.setModSeq(mapperProvider.generateModSeq(benwaInboxMailbox));
        sut.save(message1);

        SimpleMailboxMessage message1InOtherMailbox = SimpleMailboxMessage.copy(benwaWorkMailbox.getMailboxId(), message1);
        message1InOtherMailbox.setUid(mapperProvider.generateMessageUid());
        message1InOtherMailbox.setModSeq(mapperProvider.generateModSeq(benwaWorkMailbox));
        sut.save(message1InOtherMailbox);

        MessageId messageId = message1.getMessageId();
        sut.setFlags(messageId, ImmutableList.of(message1.getMailboxId(), message1InOtherMailbox.getMailboxId()), new Flags(Flag.ANSWERED), MessageManager.FlagsUpdateMode.REMOVE);

        List<MailboxMessage> messages = sut.find(ImmutableList.of(messageId), MessageMapper.FetchType.Body);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).isAnswered()).isTrue();
        assertThat(messages.get(1).isAnswered()).isTrue();
    }

    private SimpleMailbox createMailbox(MailboxPath mailboxPath) throws MailboxException {
        SimpleMailbox mailbox = new SimpleMailbox(mailboxPath, UID_VALIDITY);
        mailbox.setMailboxId(mapperProvider.generateId());
        mailboxMapper.save(mailbox);
        return mailbox;
    }
    
    private void saveMessages() throws MailboxException {
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
