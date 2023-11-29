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
import static org.apache.james.mailbox.store.mail.model.ListMessagePropertiesAssert.assertProperties;
import static org.apache.james.mailbox.store.mail.model.MessageAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.core.Username;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MapperProvider.Capabilities;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public abstract class MessageMapperTest {

    private static final char DELIMITER = '.';
    private static final int LIMIT = 10;
    private static final int BODY_START = 16;
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);

    private static final String CUSTOMS_USER_FLAGS_VALUE = "CustomsFlags";
    private static final Username BENWA = Username.of("benwa");

    protected static final String USER_FLAG = "userFlag";

    protected MapperProvider mapperProvider;
    protected MessageMapper messageMapper;
    private MailboxMapper mailboxMapper;

    protected Mailbox benwaInboxMailbox;
    protected Mailbox benwaWorkMailbox;

    protected MailboxMessage message1;
    protected MailboxMessage message2;
    protected MailboxMessage message3;
    protected MailboxMessage message4;
    protected MailboxMessage message5;
    protected MailboxMessage message6;

    protected abstract MapperProvider createMapperProvider();

    protected abstract UpdatableTickingClock updatableTickingClock();

    @BeforeEach
    void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();

        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MESSAGE));

        this.messageMapper = mapperProvider.createMessageMapper();
        this.mailboxMapper = mapperProvider.createMailboxMapper();

        initData();
    }

    private void initData() throws MailboxException {
        benwaInboxMailbox = createMailbox(MailboxPath.forUser(BENWA, "INBOX"));
        benwaWorkMailbox = createMailbox(MailboxPath.forUser(BENWA, "INBOX" + DELIMITER + "work"));

        message1 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
        message2 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test2 \n\nBody2\n.\n", BODY_START, new PropertyBuilder());
        message3 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test3 \n\nBody3\n.\n", BODY_START, new PropertyBuilder());
        message4 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test4 \n\nBody4\n.\n", BODY_START, new PropertyBuilder());
        message5 = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: Test5 \n\nBody5\n.\n", BODY_START, new PropertyBuilder());
        message6 = createMessage(benwaWorkMailbox, mapperProvider.generateMessageId(), "Subject: Test6 \n\nBody6\n.\n", BODY_START, new PropertyBuilder());
    }

    @Test
    void emptyMailboxShouldHaveZeroMessageCount() throws MailboxException {
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    void mailboxContainingMessagesShouldHaveTheGoodMessageCount() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(5);
    }

    @Test
    void mailboxCountShouldBeDecrementedAfterAMessageDelete() throws MailboxException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(4);
    }

    @Test
    void emptyMailboxShouldNotHaveUnseenMessages() throws MailboxException {
        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(0);
    }

    @Test
    void mailboxContainingMessagesShouldHaveTheGoodUnseenMessageCount() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(5);
    }

    @Test
    void mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedSeen() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE);

        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(4);
    }

    @Test
    void mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedUnSeen() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE));
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(), FlagsUpdateMode.REPLACE));
        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(5);
    }

    @Test
    void mailboxUnSeenCountShouldBeDecrementedAfterAMessageDelete() throws MailboxException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(4);
    }

    @Test
    void deletedMessagesShouldBeRemovedFromStorage() throws MailboxException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), MessageMapper.FetchType.METADATA, LIMIT))
            .toIterable()
            .isEmpty();
    }

    @Test
    void deletingUnExistingMessageShouldHaveNoSideEffect() throws MailboxException, IOException {
        saveMessages();
        message6.setUid(messageMapper.getLastUid(benwaInboxMailbox).get().next());
        messageMapper.delete(benwaInboxMailbox, message6);

        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);
        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message3, message4, message5);
    }

    @Test
    void noMessageShouldBeRetrievedInEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(MessageUid.MIN_VALUE), MessageMapper.FetchType.METADATA, LIMIT))
            .toIterable()
            .isEmpty();
    }

    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeOne() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        int limit = 10;
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, limit).next())
            .isEqualToWithoutAttachment(message1, fetchType);
    }

    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeRange() throws MailboxException, IOException {
        saveMessages();
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.range(message1.getUid(), message4.getUid()), MessageMapper.FetchType.FULL, LIMIT);

        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message3, message4);
    }
    
    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeRangeContainingAHole() throws MailboxException, IOException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message3);
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper
            .findInMailbox(benwaInboxMailbox, MessageRange.range(message1.getUid(), message4.getUid()), MessageMapper.FetchType.FULL, LIMIT);
        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message4);
    }

    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeFrom() throws MailboxException, IOException {
        saveMessages();
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.from(message3.getUid()), MessageMapper.FetchType.FULL, LIMIT);
        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message3, message4, message5);
    }

    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeFromContainingAHole() throws MailboxException, IOException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message4);
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.from(message3.getUid()), MessageMapper.FetchType.FULL, LIMIT);
        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message3, message5);
    }

    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeAll() throws MailboxException, IOException {
        saveMessages();
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);
        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message3, message4, message5);
    }

    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeAllContainingHole() throws MailboxException, IOException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);
        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message2, message3, message4, message5);
    }

    @Test
    void messagesRetrievedUsingFetchTypeMetadataShouldHaveAtLastMetadataDataLoaded() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.METADATA;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next()).isEqualTo(message1, fetchType);
        assertThat(retrievedMessageIterator)
            .toIterable()
            .isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeHeaderShouldHaveHeaderDataLoaded() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.HEADERS;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next()).isEqualTo(message1, fetchType);
        assertThat(retrievedMessageIterator)
            .toIterable()
            .isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeFullShouldHaveBodyDataLoaded() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = FetchType.FULL;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next()).isEqualToWithoutAttachment(message1, fetchType);
        assertThat(retrievedMessageIterator)
            .toIterable()
            .isEmpty();
    }

    @Test
    void retrievingMessagesWithALimitShouldLimitTheNumberOfMessages() throws MailboxException {
        int limit = 2;
        saveMessages();
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, limit))
            .toIterable()
            .hasSize(2);
    }

    @Test
    void findRecentUidsInMailboxShouldReturnEmptyListWhenNoMessagesMarkedAsRecentArePresentInMailbox() throws MailboxException {
        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).isEmpty();
    }

    @Test
    void findRecentUidsInMailboxShouldReturnListOfMessagesHoldingFlagsRecent() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setRecent = new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message2.getUid(), setRecent);
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), setRecent);
        messageMapper.updateFlags(benwaWorkMailbox, message6.getUid(), setRecent);
        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox))
            .containsOnly(message2.getUid(), message4.getUid());
    }

    @Test
    void resetRecentsShouldReturnEmptyListWhenNoMessagesMarkedAsRecentArePresentInMailbox() throws MailboxException {
        assertThat(messageMapper.resetRecent(benwaInboxMailbox)).isEmpty();
    }

    @Test
    void resetRecentsShouldRemoveAllRecentFlags() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setRecent = new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message2.getUid(), setRecent);
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), setRecent);
        messageMapper.updateFlags(benwaWorkMailbox, message6.getUid(), setRecent);

        messageMapper.resetRecent(benwaInboxMailbox);

        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox))
            .isEmpty();
    }

    @Test
    void resetRecentsShouldReturnUpdatedFlags() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setRecent = new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message2.getUid(), setRecent);
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), setRecent);
        messageMapper.updateFlags(benwaWorkMailbox, message6.getUid(), setRecent);

        assertThat(messageMapper.resetRecent(benwaInboxMailbox))
            .extracting(UpdatedFlags::getUid)
            .containsOnly(message2.getUid(), message4.getUid());
    }

    @Test
    void deleteShouldUpdateRecentWhenNeeded() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setRecent = new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message2.getUid(), setRecent);
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), setRecent);
        messageMapper.updateFlags(benwaWorkMailbox, message6.getUid(), setRecent);

        messageMapper.delete(benwaInboxMailbox, message2);

        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).containsOnly(message4.getUid());
    }

    @Test
    void deleteShouldNotUpdateRecentWhenNotNeeded() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setRecent = new FlagsUpdateCalculator(new Flags(Flag.RECENT), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message2.getUid(), setRecent);
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), setRecent);
        messageMapper.updateFlags(benwaWorkMailbox, message6.getUid(), setRecent);

        messageMapper.delete(benwaInboxMailbox, message1);

        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).containsOnly(message2.getUid(), message4.getUid());
    }

    @Test
    void deleteMessagesShouldDecrementUnseenToOneWhenDeletingTwoUnseenMessagesOutOfThree() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setSeen = new FlagsUpdateCalculator(new Flags(Flag.SEEN), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message2.getUid(), setSeen);
        messageMapper.updateFlags(benwaInboxMailbox, message3.getUid(), setSeen);
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), setSeen);

        messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message1.getUid(), message2.getUid(), message3.getUid()));

        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox))
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(benwaInboxMailbox.getMailboxId())
                .count(2)
                .unseen(1)
                .build());
    }

    @Test
    void addShouldUpdateRecentWhenNeeded() throws MailboxException {
        message1.setFlags(new Flags(Flags.Flag.RECENT));
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).containsOnly(message1.getUid());
    }

    @Test
    void addShouldNotUpdateRecentWhenNotNeeded() throws MailboxException {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));

        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).isEmpty();
    }

    @Test
    void findFirstUnseenMessageUidShouldReturnNullWhenNoUnseenMessagesCanBeFound() throws MailboxException {
        assertThat(messageMapper.findFirstUnseenMessageUid(benwaInboxMailbox)).isNull();
    }

    @Test
    void findFirstUnseenMessageUidShouldReturnUid1WhenUid1isNotSeen() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.findFirstUnseenMessageUid(benwaInboxMailbox)).isEqualTo(message1.getUid());
    }

    @Test
    void findFirstUnseenMessageUidShouldReturnUid2WhenUid2isSeen() throws MailboxException {
        saveMessages();
        FlagsUpdateCalculator setSeen = new FlagsUpdateCalculator(new Flags(Flag.SEEN), FlagsUpdateMode.REPLACE);
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), setSeen);
        messageMapper.updateFlags(benwaInboxMailbox, message3.getUid(), setSeen);
        messageMapper.updateFlags(benwaWorkMailbox, message5.getUid(), setSeen);
        assertThat(messageMapper.findFirstUnseenMessageUid(benwaInboxMailbox)).isEqualTo(message2.getUid());
    }

    @Test
    void retrieveMessagesMarkedForDeletionInMailboxShouldReturnEmptyResultOnEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.retrieveMessagesMarkedForDeletion(benwaInboxMailbox, MessageRange.all())).isEmpty();
    }

    @Test
    void deleteMessagesInMailboxShouldReturnEmptyResultOnEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of())).isEmpty();
    }

    @Test
    void retrieveMessagesMarkedForDeletionInMailboxShouldReturnEmptyResultWhenNoMessageInMailboxIsMarkedAsDeleted() throws MailboxException, IOException {
        saveMessages();
        assertThat(messageMapper.retrieveMessagesMarkedForDeletion(benwaInboxMailbox, MessageRange.all())).isEmpty();
    }

    @Test
    void deleteMessagesInMailboxShouldReturnEmptyResultWhenNoMessageInMailboxIsDeleted() throws MailboxException, IOException {
        saveMessages();
        assertThat(messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of())).isEmpty();
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);

        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message3, message4, message5);
    }

    @Test
    void retrieveMessagesMarkedForDeletionShouldReturnCorrectUidsWithRangeAll() throws MailboxException {
        saveMessages();
        assertThat(markThenPerformRetrieveMessagesMarkedForDeletion(MessageRange.all()))
            .containsOnly(message1.getUid(), message4.getUid());
    }

    @Test
    void deleteMessagesShouldReturnCorrectMetadataWithRangeAll() throws MailboxException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformDeleteMessages(MessageRange.all()))
            .hasSize(2)
            .containsMetadataForMessages(message1, message4);
    }

    @Test
    void deleteMessagesShouldModifyUnderlyingStorageWithRangeAll() throws MailboxException, IOException {
        saveMessages();
        markThenPerformDeleteMessages(MessageRange.all());

        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);

        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message2, message3, message5);
    }

    @Test
    void retrieveMessagesMarkedForDeletionShouldReturnCorrectMetadataWithRangeOne() throws MailboxException {
        saveMessages();
        assertThat(markThenPerformRetrieveMessagesMarkedForDeletion(MessageRange.one(message1.getUid())))
            .containsOnly(message1.getUid());
    }

    @Test
    void deleteMessagesShouldReturnCorrectMetadataWithRangeOne() throws MailboxException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformDeleteMessages(MessageRange.one(message1.getUid())))
            .hasSize(1)
            .containsMetadataForMessages(message1);
    }

    @Test
    void deleteMessagesShouldModifyUnderlyingStorageWithRangeOne() throws MailboxException, IOException {
        saveMessages();
        markThenPerformDeleteMessages(MessageRange.one(message1.getUid()));

        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);

        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message4, message2, message3, message5);
    }

    @Test
    void retrieveMessagesMarkedForDeletionShouldReturnCorrectMetadataWithRangeFrom() throws MailboxException {
        saveMessages();
        assertThat(markThenPerformRetrieveMessagesMarkedForDeletion(MessageRange.from(message3.getUid())))
            .containsOnly(message4.getUid());
    }

    @Test
    void deleteMessagesShouldReturnCorrectMetadataWithRangeFrom() throws MailboxException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformDeleteMessages(MessageRange.from(message3.getUid())))
            .hasSize(1)
            .containsMetadataForMessages(message4);
    }

    @Test
    void deleteMessagesShouldModifyUnderlyingStorageWithRangeFrom() throws MailboxException, IOException {
        saveMessages();
        markThenPerformDeleteMessages(MessageRange.from(message3.getUid()));

        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);

        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message3, message5);
    }

    @Test
    void retrieveMessagesMarkedForDeletionShouldReturnCorrectMetadataWithRange() throws MailboxException {
        saveMessages();
        assertThat(markThenPerformRetrieveMessagesMarkedForDeletion(MessageRange.range(message3.getUid(), message5.getUid())))
            .containsOnly(message4.getUid());
    }

    @Test
    void deleteMessagesShouldReturnCorrectMetadataWithRange() throws MailboxException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformDeleteMessages(MessageRange.range(message3.getUid(), message5.getUid())))
            .hasSize(1)
            .containsMetadataForMessages(message4);
    }

    @Test
    void deleteMessagesShouldModifyUnderlyingStorageWithRange() throws MailboxException, IOException {
        saveMessages();
        markThenPerformDeleteMessages(MessageRange.range(message3.getUid(), message5.getUid()));

        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.FULL, LIMIT);

        assertMessages(Lists.newArrayList(retrievedMessageIterator)).containOnly(message1, message2, message3, message5);
    }

    @Test
    void getHighestMoseqShouldBeEqualToZeroOnEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.getHighestModSeq(benwaInboxMailbox)).isEqualTo(ModSeq.first());
    }

    @Test
    void insertingAMessageShouldIncrementModSeq() throws MailboxException {
        messageMapper.add(benwaInboxMailbox, message1);
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(modSeq).isGreaterThan(ModSeq.first());
        messageMapper.add(benwaInboxMailbox, message2);
        assertThat(messageMapper.getHighestModSeq(benwaInboxMailbox)).isGreaterThan(modSeq);
    }

    @Test
    void getLastUidShouldReturnEmptyOnEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.getLastUid(benwaInboxMailbox)).isEqualTo(Optional.empty());
    }

    @Test
    void insertingAMessageShouldIncrementLastUid() throws MailboxException {
        messageMapper.add(benwaInboxMailbox, message1);
        Optional<MessageUid> uid = messageMapper.getLastUid(benwaInboxMailbox);
        assertThat(uid).isNotEqualTo(Optional.empty());
        messageMapper.add(benwaInboxMailbox, message2);
        assertThat(messageMapper.getLastUid(benwaInboxMailbox).get()).isGreaterThan(uid.get());
    }

    @Test
    void copyShouldIncrementUid() throws MailboxException {
        saveMessages();
        MessageUid uid = messageMapper.getLastUid(benwaInboxMailbox).get();
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        messageMapper.copy(benwaInboxMailbox, message);
        assertThat(messageMapper.getLastUid(benwaInboxMailbox).get()).isGreaterThan(uid);
    }

    @Test
    void copyShouldIncrementMessageCount() throws MailboxException {
        saveMessages();
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        messageMapper.copy(benwaInboxMailbox, message);
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(6);
    }

    @Test
    void copyOfUnSeenMessageShouldIncrementUnSeenMessageCount() throws MailboxException {
        saveMessages();
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        messageMapper.copy(benwaInboxMailbox, message);
        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(6);
    }

    @Test
    void copyShouldIncrementModSeq() throws MailboxException, IOException {
        saveMessages();
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        messageMapper.copy(benwaInboxMailbox, message);
        assertThat(messageMapper.getHighestModSeq(benwaInboxMailbox)).isGreaterThan(modSeq);
    }

    @Test
    void copyShouldCreateAMessageInDestination() throws MailboxException, IOException {
        saveMessages();
        MailboxMessage message7 = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.FULL, 1).next();
        messageMapper.copy(benwaInboxMailbox, message7);
        message7.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        assertThat(messageMapper.getLastUid(benwaInboxMailbox).get()).isGreaterThan(message6.getUid());

        MailboxMessage result = messageMapper.findInMailbox(benwaInboxMailbox,
            MessageRange.one(messageMapper.getLastUid(benwaInboxMailbox).get()),
            MessageMapper.FetchType.FULL,
            LIMIT)
            .next();

        assertThat(result).isEqualToWithoutUidAndAttachment(message7, MessageMapper.FetchType.FULL);
        assertThat(result.getUid()).isEqualTo(messageMapper.getLastUid(benwaInboxMailbox).get());
    }

    @Test
    void copyOfSeenMessageShouldNotIncrementUnSeenMessageCount() throws MailboxException {
        message6.setFlags(new Flags(Flags.Flag.SEEN));
        saveMessages();
        long expectedUnseenMessages = messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen();

        MailboxMessage message = messageMapper.findInMailbox(benwaWorkMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next()
            .copy(benwaInboxMailbox);
        messageMapper.copy(benwaInboxMailbox, message);
        assertThat(messageMapper.getMailboxCounters(benwaInboxMailbox).getUnseen()).isEqualTo(expectedUnseenMessages);
    }

    @Test
    void copiedMessageShouldBeMarkedAsRecent() throws MailboxException {
        saveMessages();
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        MessageMetaData metaData = messageMapper.copy(benwaInboxMailbox, message);
        assertThat(
            messageMapper.findInMailbox(benwaInboxMailbox,
                MessageRange.one(metaData.getUid()),
                MessageMapper.FetchType.METADATA,
                LIMIT
            ).next()
            .isRecent()
        ).isTrue();
    }

    @Test
    void copiedRecentMessageShouldBeMarkedAsRecent() throws MailboxException {
        saveMessages();
        message6.setFlags(new Flags(Flags.Flag.RECENT));
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        MessageMetaData metaData = messageMapper.copy(benwaInboxMailbox, message);
        assertThat(
            messageMapper.findInMailbox(benwaInboxMailbox,
                MessageRange.one(metaData.getUid()),
                MessageMapper.FetchType.METADATA,
                LIMIT
            ).next()
                .isRecent()
        ).isTrue();
    }

    @Test
    void copiedMessageShouldNotChangeTheFlagsOnOriginalMessage() throws MailboxException {
        saveMessages();
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message6.getUid()), FetchType.METADATA, 1).next();
        messageMapper.copy(benwaInboxMailbox, message);
        assertThat(
            messageMapper.findInMailbox(benwaWorkMailbox,
                MessageRange.one(message6.getUid()),
                MessageMapper.FetchType.METADATA,
                LIMIT
            ).next()
            .isRecent()
        ).isFalse();
    }

    @Test
    void flagsReplacementShouldReplaceStoredMessageFlags() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE));
        assertThat(retrieveMessageFromStorage(message1)).hasFlags(new Flags(Flags.Flag.FLAGGED));
    }

    @Test
    protected void flagsReplacementShouldReturnAnUpdatedFlagHighlightingTheReplacement() throws MailboxException {
        saveMessages();
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        Optional<UpdatedFlags> updatedFlags = messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(),
                new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE));
        assertThat(updatedFlags)
            .contains(UpdatedFlags.builder()
                .uid(message1.getUid())
                .messageId(message1.getMessageId())
                .modSeq(modSeq.next())
                .oldFlags(new Flags())
                .newFlags(new Flags(Flags.Flag.FLAGGED))
                .build());
    }

    @Test
    protected void flagsAdditionShouldReturnAnUpdatedFlagHighlightingTheAddition() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE));
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD)))
            .contains(UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .messageId(message1.getMessageId())
                    .modSeq(modSeq.next())
                    .oldFlags(new Flags(Flags.Flag.FLAGGED))
                    .newFlags(new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build())
                    .build());
    }

    @Test
    void flagsAdditionShouldUpdateStoredMessageFlags() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE));
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD));
        assertThat(retrieveMessageFromStorage(message1)).hasFlags(new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build());
    }

    @Test
    void flagsAdditionShouldHaveNoEffectOnStoredFlagsWhenNoop() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE));

        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flag.FLAGGED), FlagsUpdateMode.ADD));
        assertThat(retrieveMessageFromStorage(message1))
            .hasFlags(new FlagsBuilder().add(Flags.Flag.FLAGGED).build());
    }

    @Test
    protected void flagsRemovalShouldReturnAnUpdatedFlagHighlightingTheRemoval() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new FlagsBuilder().add(Flags.Flag.FLAGGED, Flags.Flag.SEEN).build(), FlagsUpdateMode.REPLACE));
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REMOVE)))
            .contains(
                UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .messageId(message1.getMessageId())
                    .modSeq(modSeq.next())
                    .oldFlags(new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build())
                    .newFlags(new Flags(Flags.Flag.FLAGGED))
                    .build());
    }

    @Test
    void flagsRemovalShouldUpdateStoredMessageFlags() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new FlagsBuilder().add(Flags.Flag.FLAGGED, Flags.Flag.SEEN).build(), FlagsUpdateMode.REPLACE));
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REMOVE));
        assertThat(retrieveMessageFromStorage(message1)).hasFlags(new Flags(Flags.Flag.FLAGGED));
    }

    @Test
    void updateFlagsOnRangeShouldAffectMessagesContainedInThisRange() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.range(message1.getUid(), message3.getUid())))
            .toIterable()
            .hasSize(3);
    }

    @Test
    void updateFlagsWithRangeFromShouldAffectMessagesContainedInThisRange() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.from(message3.getUid())))
            .toIterable()
            .hasSize(3);
    }

    @Test
    void updateFlagsWithRangeAllRangeShouldAffectAllMessages() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.all()))
            .toIterable()
            .hasSize(5);
    }

    @Test
    void messagePropertiesShouldBeStored() throws Exception {
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setMediaType("text");
        propBuilder.setSubType("html");
        propBuilder.setTextualLineCount(2L);
        propBuilder.setContentTransferEncoding("7bit");
        propBuilder.setCharset("US-ASCII");

        MailboxMessage messageWithProperties = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: messagePropertiesShouldBeStored \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.FULL, 1).next();

        assertProperties(message.getProperties().toProperties()).containsOnly(propBuilder.toProperties());
    }
    
    @Test
    void messagePropertiesShouldBeStoredWhenDuplicateEntries() throws Exception {
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setContentLanguage(ImmutableList.of("us", "fr"));

        MailboxMessage messageWithProperties = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.FULL, 1).next();

        assertProperties(message.getProperties().toProperties()).containsOnly(propBuilder.toProperties());
    }

    @Test
    void messagePropertiesShouldBeStoredWhenNoProperty() throws Exception {
        MailboxMessage messageWithProperties = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: messagePropertiesShouldBeStoredWhenNoProperty \n\nBody\n.\n", BODY_START, new PropertyBuilder());
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.FULL, 1).next();
        assertThat(message.getProperties().toProperties()).isEmpty();
    }

    @Test
    void textualLineCountShouldBeWellStored() throws Exception {
        long textualLineCount = 48L;
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setTextualLineCount(textualLineCount);

        MailboxMessage messageWithProperties = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.FULL, 1).next();
        assertThat(message.getTextualLineCount()).isEqualTo(textualLineCount);
    }

    @Test
    void mediaTypeShouldBeWellStored() throws Exception {
        String mediaType = "plain";
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setMediaType(mediaType);

        MailboxMessage messageWithProperties = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.FULL, 1).next();
        assertThat(message.getMediaType()).isEqualTo(mediaType);
    }

    @Test
    void subTypeShouldBeWellStored() throws Exception {
        String subType = "text";
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setSubType(subType);

        MailboxMessage messageWithProperties = createMessage(benwaInboxMailbox, mapperProvider.generateMessageId(), "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        MailboxMessage message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.FULL, 1).next();
        assertThat(message.getSubType()).isEqualTo(subType);
    }

    @Test
    void userFlagsShouldBeSupported() throws Exception {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(USER_FLAG), FlagsUpdateMode.ADD));
        assertThat(retrieveMessageFromStorage(message1)).hasFlags(new Flags(USER_FLAG));
    }

    @Test
    protected void userFlagsUpdateShouldReturnCorrectUpdatedFlags() throws Exception {
        saveMessages();
        ModSeq modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(USER_FLAG), FlagsUpdateMode.ADD)))
            .contains(
                UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .messageId(message1.getMessageId())
                    .modSeq(modSeq.next())
                    .oldFlags(new Flags())
                    .newFlags(new Flags(USER_FLAG))
                    .build());
    }

    @Test
    protected void userFlagsUpdateShouldReturnCorrectUpdatedFlagsWhenNoop() throws Exception {
        saveMessages();

        assertThat(
            messageMapper.updateFlags(benwaInboxMailbox,message1.getUid(),
                new FlagsUpdateCalculator(new Flags(USER_FLAG), FlagsUpdateMode.REMOVE)))
            .contains(
                UpdatedFlags.builder()
                    .uid(message1.getUid())
                    .messageId(message1.getMessageId())
                    .modSeq(message1.getModSeq())
                    .oldFlags(new Flags())
                    .newFlags(new Flags())
                    .build());
    }

    @Test
    public void userFlagsUpdateShouldWorkInConcurrentEnvironment() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));

        saveMessages();

        int threadCount = 8;
        int updateCount = 40;
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(),
                new FlagsUpdateCalculator(new Flags("custom-" + threadNumber + "-" + step), FlagsUpdateMode.ADD)))
            .threadCount(threadCount)
            .operationCount(updateCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()),
            FetchType.METADATA, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().createFlags().getUserFlags()).hasSize(threadCount * updateCount);
    }

    @Test
    public void setFlagsShouldWorkWithConcurrencyWithRemove() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));
        saveMessages();

        int threadCount = 8;
        int updateCount = 40;
        ConcurrentTestRunner.builder()
            .operation((threadNumber, step) -> {
                if (step  < updateCount / 2) {
                    messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(),
                        new FlagsUpdateCalculator(new Flags("custom-" + threadNumber + "-" + step), FlagsUpdateMode.ADD));
                } else {
                    messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(),
                        new FlagsUpdateCalculator(new Flags("custom-" + threadNumber + "-" + (updateCount - step - 1)),
                            FlagsUpdateMode.REMOVE));
                }
            })
            .threadCount(threadCount)
            .operationCount(updateCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        Iterator<MailboxMessage> messages = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()),
            FetchType.METADATA, 1);
        assertThat(messages.hasNext()).isTrue();
        assertThat(messages.next().createFlags().getUserFlags()).isEmpty();
    }

    @Test
    void messagesShouldBeSavedWithTheirUserFlags() throws Exception {
        MailboxMessage message = SimpleMailboxMessage.copy(benwaInboxMailbox.getMailboxId(), message1);
        messageMapper.add(benwaInboxMailbox, message);
        assertThat(retrieveMessageFromStorage(message)).hasFlags(new Flags(USER_FLAG));
    }

    @Test
    void getApplicableFlagShouldUnionAllMessageFlags() throws Exception {
        String customFlags1 = "custom1";
        String customFlags2 = "custom2";
        message1.setFlags(new Flags(customFlags1));
        message2.setFlags(new Flags(customFlags2));

        saveMessages();

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlags1, customFlags2)
                .build());
    }

    @Test
    void getApplicableFlagShouldUnionAllMessageFlagsExceptRecentAndUser() throws Exception {
        message1.setFlags(new Flags(Flag.ANSWERED));
        message2.setFlags(new Flags(Flag.DELETED));

        Flags recentAndUser = new Flags(Flag.RECENT);
        recentAndUser.add(Flag.USER);
        recentAndUser.add(CUSTOMS_USER_FLAGS_VALUE);
        message3.setFlags(recentAndUser);

        saveMessages();

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(CUSTOMS_USER_FLAGS_VALUE)
                .build());
    }

    @Test
    void getApplicableFlagShouldContainSystemFlagsByDefault() throws Exception {
        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .build());
    }


    @Test
    void getApplicableFlagShouldHaveEffectWhenUpdateFlagsByAddingThenComputingApplicableFlagsFromCurrentMailboxState() throws Exception {
        message1.setFlags(new Flags(Flag.ANSWERED));
        message2.setFlags(new Flags(Flag.DELETED));

        String customFlag = "custom1";
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(customFlag), FlagsUpdateMode.ADD);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlag)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveNotEffectWhenUpdateFlagsByReplaceThenIncrementalApplicableFlags() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlags = "custom";
        message1.setFlags(new Flags(customFlags));
        message2.setFlags(new Flags(Flag.DELETED));
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(new FlagsBuilder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlags)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveEffectWhenUpdateFlagsByReplaceThenComputingApplicableFlagsFromCurrentMailboxState() throws Exception {
        Assume.assumeFalse(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlags = "custom";
        message1.setFlags(new Flags(customFlags));
        message2.setFlags(new Flags(Flag.DELETED));
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveNotEffectWhenUpdateFlagsByRemoveThenIncrementalApplicableFlags() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlags = "custom";
        message1.setFlags(new FlagsBuilder().add(Flag.ANSWERED).add(customFlags).build());
        message2.setFlags(new Flags(Flag.DELETED));
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(customFlags), FlagsUpdateMode.REMOVE);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(new FlagsBuilder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlags)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveEffectWhenUpdateFlagsByRemoveThenComputingApplicableFlagsFromCurrentMailboxState() throws Exception {
        Assume.assumeFalse(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlags = "custom";
        message1.setFlags(new FlagsBuilder().add(Flag.ANSWERED).add(customFlags).build());
        message2.setFlags(new Flags(Flag.DELETED));
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(customFlags), FlagsUpdateMode.REMOVE);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveEffectWhenUnsetMessageFlagThenComputingApplicableFlagsFromCurrentMailboxState() throws Exception {
        Assume.assumeFalse(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlag1 = "custom1";
        String customFlag2 = "custom2";
        String customFlag3 = "custom3";
        message1.setFlags(new FlagsBuilder().add(customFlag1, customFlag2).build());
        message2.setFlags(new Flags(customFlag3));
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(), FlagsUpdateMode.REPLACE);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlag3)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveNotEffectWhenUnsetMessageFlagThenIncrementalApplicableFlags() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.THREAD_SAFE_FLAGS_UPDATE));
        String customFlag1 = "custom1";
        String customFlag2 = "custom2";
        message1.setFlags(new Flags(customFlag1));
        message2.setFlags(new Flags(customFlag2));
        FlagsUpdateCalculator newFlags = new FlagsUpdateCalculator(new Flags(), FlagsUpdateMode.REPLACE);

        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), newFlags);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(new FlagsBuilder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlag1, customFlag2)
                .build());
    }


    @Test
    void getApplicableFlagShouldHaveNotEffectWhenDeleteMessageThenIncrementalApplicableFlags() throws Exception {
        Assume.assumeTrue(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlag1 = "custom1";
        String customFlag2 = "custom2";
        message1.setFlags(new Flags(customFlag1));
        message2.setFlags(new Flags(customFlag2));

        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(new FlagsBuilder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlag1, customFlag2)
                .build());
    }

    @Test
    void getApplicableFlagShouldReturnDefaultApplicableFlagsWhenMailboxEmpty() throws Exception {
        Mailbox emptyMailbox = createMailbox(MailboxPath.forUser(BENWA, "EMPTY"));

        assertThat(messageMapper.getApplicableFlag(emptyMailbox))
            .isEqualTo(new FlagsBuilder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .build());
    }

    @Test
    void getApplicableFlagShouldHaveEffectWhenDeleteMessageThenComputingApplicableFlagsFromCurrentMailboxState() throws Exception {
        Assume.assumeFalse(mapperProvider.getSupportedCapabilities().contains(Capabilities.INCREMENTAL_APPLICABLE_FLAGS));
        String customFlag1 = "custom1";
        String customFlag2 = "custom2";
        message1.setFlags(new Flags(customFlag1));
        message2.setFlags(new Flags(customFlag2));

        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);

        assertThat(messageMapper.getApplicableFlag(benwaInboxMailbox))
            .isEqualTo(FlagsBuilder.builder()
                .add(Flag.DELETED, Flag.ANSWERED, Flag.DRAFT, Flag.FLAGGED, Flag.SEEN)
                .add(customFlag2)
                .build());
    }

    @Test
    void getUidsShouldReturnUidsOfMessagesInTheMailbox() throws Exception {
        saveMessages();

        assertThat(messageMapper.listAllMessageUids(benwaInboxMailbox).collectList().block())
            .containsOnly(message1.getUid(),
                message2.getUid(),
                message3.getUid(),
                message4.getUid(),
                message5.getUid());
    }

    @Test
    void deleteMessagesShouldNotRequireMessagesToBeMarkedAsDeleted() throws Exception {
        saveMessages();

        messageMapper.deleteMessages(benwaInboxMailbox, ImmutableList.of(message2.getUid(), message3.getUid()));

        assertThat(messageMapper.listAllMessageUids(benwaInboxMailbox).collectList().block())
            .containsOnly(message1.getUid(),
                message4.getUid(),
                message5.getUid());
    }

    @Test
    void getUidsShouldNotReturnUidsOfDeletedMessages() throws Exception {
        saveMessages();

        messageMapper.updateFlags(benwaInboxMailbox,
            new FlagsUpdateCalculator(new Flags(Flag.DELETED), FlagsUpdateMode.ADD),
            MessageRange.range(message2.getUid(), message4.getUid())).forEachRemaining(any -> {});
        List<MessageUid> uids = messageMapper.retrieveMessagesMarkedForDeletion(benwaInboxMailbox, MessageRange.all());
        messageMapper.deleteMessages(benwaInboxMailbox, uids);

        assertThat(messageMapper.listAllMessageUids(benwaInboxMailbox).collectList().block())
            .containsOnly(message1.getUid(), message5.getUid());
    }

    @Nested
    class SaveDateTests {
        @Test
        void addMessageShouldSetNewSaveDate() throws MailboxException {
            MailboxMessage messageWithoutSaveDate = createMessage(Optional.empty());

            MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithoutSaveDate);

            assertThat(messageMetaData.getSaveDate()).isPresent();
        }

        @Test
        void deleteMessageShouldReturnMetaDataContainsSaveDate() throws MailboxException {
            MessageMetaData toBeDeletedMessage = messageMapper.add(benwaInboxMailbox, createMessage(Optional.empty()));

            assertThat(messageMapper.deleteMessages(benwaInboxMailbox, List.of(toBeDeletedMessage.getUid()))
                .values()
                .stream()
                .allMatch(messageMetaData -> messageMetaData.getSaveDate().equals(toBeDeletedMessage.getSaveDate())))
                .isTrue();
        }

        @Test
        void copyMessageShouldSetNewSaveDate() throws MailboxException {
            MailboxMessage originalMessage = createMessage(Optional.of(new Date()));
            MessageUid uid = messageMapper.add(benwaInboxMailbox, originalMessage).getUid();

            updatableTickingClock().setInstant(updatableTickingClock().instant().plusSeconds(1000));

            MessageMetaData copiedMessageMetaData = messageMapper.copy(benwaInboxMailbox,
                messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(uid), FetchType.METADATA, 1).next());

            assertThat(copiedMessageMetaData.getSaveDate()).isNotEqualTo(originalMessage.getSaveDate());
        }

        @Test
        void copyListOfMessagesShouldSetNewSaveDate() throws MailboxException {
            MailboxMessage originalMessage = createMessage(Optional.of(new Date()));
            MessageUid uid = messageMapper.add(benwaInboxMailbox, originalMessage).getUid();

            updatableTickingClock().setInstant(updatableTickingClock().instant().plusSeconds(1000));

            List<MessageMetaData> copiedMessageMetaData = messageMapper.copy(benwaInboxMailbox,
                List.of(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(uid), FetchType.METADATA, 1).next()));

            assertThat(copiedMessageMetaData.get(0).getSaveDate()).isNotEqualTo(originalMessage.getSaveDate());
        }

        @Test
        void moveMessageShouldSetNewSaveDate() throws MailboxException {
            MailboxMessage originalMessage = createMessage(Optional.of(new Date()));
            MessageUid uid = messageMapper.add(benwaInboxMailbox, originalMessage).getUid();

            updatableTickingClock().setInstant(updatableTickingClock().instant().plusSeconds(1000));

            MessageMetaData movedMessageMetaData = messageMapper.move(benwaInboxMailbox,
                messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(uid), FetchType.METADATA, 1).next());

            assertThat(movedMessageMetaData.getSaveDate()).isNotEqualTo(originalMessage.getSaveDate());
        }

        @Test
        void moveListOfMessagesShouldSetNewSaveDate() throws MailboxException {
            MailboxMessage originalMessage = createMessage(Optional.of(new Date()));
            MessageUid uid = messageMapper.add(benwaInboxMailbox, originalMessage).getUid();

            updatableTickingClock().setInstant(updatableTickingClock().instant().plusSeconds(1000));

            List<MessageMetaData> movedMessageMetaData = messageMapper.move(benwaInboxMailbox,
                List.of(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(uid), FetchType.METADATA, 1).next()));

            assertThat(movedMessageMetaData.get(0).getSaveDate()).isNotEqualTo(originalMessage.getSaveDate());
        }

        private SimpleMailboxMessage createMessage(Optional<Date> saveDate) throws MailboxException {
            Content content = new ByteContent("Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n".getBytes());
            return SimpleMailboxMessage.builder()
                .messageId(mapperProvider.generateMessageId())
                .mailboxId(benwaInboxMailbox.getMailboxId())
                .threadId(ThreadId.fromBaseMessageId(mapperProvider.generateMessageId()))
                .internalDate(new Date())
                .saveDate(saveDate)
                .bodyStartOctet(16)
                .size(content.size())
                .content(content)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build();
        }
    }

    private List<MessageUid> markThenPerformRetrieveMessagesMarkedForDeletion(MessageRange range) throws MailboxException {
        messageMapper.updateFlags(benwaInboxMailbox, message1.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REPLACE));
        messageMapper.updateFlags(benwaInboxMailbox, message4.getUid(), new FlagsUpdateCalculator(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REPLACE));
        return messageMapper.retrieveMessagesMarkedForDeletion(benwaInboxMailbox, range);
    }

    private Map<MessageUid, MessageMetaData> markThenPerformDeleteMessages(MessageRange range) throws MailboxException {
        List<MessageUid> uids = markThenPerformRetrieveMessagesMarkedForDeletion(range);
        return messageMapper.deleteMessages(benwaInboxMailbox, uids);
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) throws MailboxException {
        return mailboxMapper.create(mailboxPath, UID_VALIDITY).block();
    }

    protected void saveMessages() throws MailboxException {
        messageMapper.add(benwaInboxMailbox, message1);
        message1.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        messageMapper.add(benwaInboxMailbox, message2);
        message2.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        messageMapper.add(benwaInboxMailbox, message3);
        message3.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        messageMapper.add(benwaInboxMailbox, message4);
        message4.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        messageMapper.add(benwaInboxMailbox, message5);
        message5.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        messageMapper.add(benwaWorkMailbox, message6);
        message6.setModSeq(messageMapper.getHighestModSeq(benwaWorkMailbox));
    }

    private MailboxMessage retrieveMessageFromStorage(MailboxMessage message) throws MailboxException {
        return messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message.getUid()), MessageMapper.FetchType.METADATA, LIMIT).next();
    }
    
    private MailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMailboxMessage(messageId, ThreadId.fromBaseMessageId(messageId), new Date(), content.length(), bodyStart, new ByteContent(content.getBytes()), new Flags(), propertyBuilder.build(), mailbox.getMailboxId());
    }
}
