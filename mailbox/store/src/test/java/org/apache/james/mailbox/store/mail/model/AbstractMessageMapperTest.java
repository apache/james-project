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

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public abstract class AbstractMessageMapperTest<Id extends MailboxId> {

    private final static char DELIMITER = ':';
    private static final int LIMIT = 10;
    private static final int BODY_START = 16;
    public static final int UID_VALIDITY = 42;
    public static final String USER_FLAG = "userFlag";

    private MapperProvider<Id> mapperProvider;
    private MessageMapper<Id> messageMapper;

    private SimpleMailbox<Id> benwaInboxMailbox;
    private SimpleMailbox<Id> benwaWorkMailbox;
    
    private SimpleMessage<Id> message1;
    private SimpleMessage<Id> message2;
    private SimpleMessage<Id> message3;
    private SimpleMessage<Id> message4;
    private SimpleMessage<Id> message5;
    private SimpleMessage<Id> message6;

    public AbstractMessageMapperTest(MapperProvider<Id> mapperProvider) {
        this.mapperProvider = mapperProvider;
    }

    @Before
    public void setUp() throws MailboxException {
        mapperProvider.ensureMapperPrepared();
        messageMapper = mapperProvider.createMessageMapper();
        benwaInboxMailbox = createMailbox(new MailboxPath("#private", "benwa", "INBOX"));
        benwaWorkMailbox = createMailbox( new MailboxPath("#private", "benwa", "INBOX"+DELIMITER+"work"));
        message1 = createMessage(benwaInboxMailbox, "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
        message2 = createMessage(benwaInboxMailbox, "Subject: Test2 \n\nBody2\n.\n", BODY_START, new PropertyBuilder());
        message3 = createMessage(benwaInboxMailbox, "Subject: Test3 \n\nBody3\n.\n", BODY_START, new PropertyBuilder());
        message4 = createMessage(benwaInboxMailbox, "Subject: Test4 \n\nBody4\n.\n", BODY_START, new PropertyBuilder());
        message5 = createMessage(benwaInboxMailbox, "Subject: Test5 \n\nBody5\n.\n", BODY_START, new PropertyBuilder());
        message6 = createMessage(benwaWorkMailbox, "Subject: Test6 \n\nBody6\n.\n", BODY_START, new PropertyBuilder());
    }

    @After
    public void tearDown() throws MailboxException {
        mapperProvider.clearMapper();
    }
    
    @Test
    public void emptyMailboxShouldHaveZeroMessageCount() throws MailboxException {
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }
    
    @Test
    public void mailboxContainingMessagesShouldHaveTheGoodMessageCount() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(5);
    }

    @Test
    public void mailboxCountShouldBeDecrementedAfterAMessageDelete() throws MailboxException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(4);
    }
    
    @Test
    public void emptyMailboxShouldNotHaveUnseenMessages() throws MailboxException {
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }
    
    @Test
    public void mailboxContainingMessagesShouldHaveTheGoodUnseenMessageCount() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(5);
    }

    @Test
    public void mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedSeen() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid())).hasNext();
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(4);
    }

    @Test
    public void mailboxUnSeenCountShouldBeDecrementedAfterAMessageIsMarkedUnSeen() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(5);
    }
    
    @Test
    public void mailboxUnSeenCountShouldBeDecrementedAfterAMessageDelete() throws MailboxException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(4);
    }

    @Test
    public void deletedMessagesShouldBeRemovedFromStorage() throws MailboxException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), MessageMapper.FetchType.Metadata, LIMIT)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void deletingUnExistingMessageShouldHaveNoSideEffect() throws MailboxException, IOException {
        saveMessages();
        message6.setUid(messageMapper.getLastUid(benwaInboxMailbox) + 1);
        messageMapper.delete(benwaInboxMailbox, message6);
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message1, message2, message3, message4, message5);
    }

    @Test
    public void noMessageShouldBeRetrievedInEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), MessageMapper.FetchType.Metadata, LIMIT)).isEmpty();
    }

    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeOne() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        int limit =10;
        MessageAssert.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, limit).next())
            .isEqualTo(message1, fetchType);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeRange() throws MailboxException, IOException{
        saveMessages();
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.range(message1.getUid(), message4.getUid()), MessageMapper.FetchType.Full, LIMIT);
        assertThat(retrievedMessageIterator).containsOnly(message1, message2, message3, message4);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeRangeContainingAHole() throws MailboxException, IOException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message3);
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper
            .findInMailbox(benwaInboxMailbox, MessageRange.range(message1.getUid(), message4.getUid()), MessageMapper.FetchType.Full, LIMIT);
        assertThat(retrievedMessageIterator).containsOnly(message1, message2, message4);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeFrom() throws MailboxException, IOException {
        saveMessages();
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.from(message3.getUid()), MessageMapper.FetchType.Full, LIMIT);
        assertThat(retrievedMessageIterator).containsOnly(message3, message4, message5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeFromContainingAHole() throws MailboxException, IOException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message4);
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.from(message3.getUid()), MessageMapper.FetchType.Full, LIMIT);
        assertThat(retrievedMessageIterator).containsOnly(message3, message5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeAll() throws MailboxException, IOException {
        saveMessages();
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message1, message2, message3, message4, message5);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void messagesCanBeRetrievedInMailboxWithRangeTypeAllContainingHole() throws MailboxException, IOException {
        saveMessages();
        messageMapper.delete(benwaInboxMailbox, message1);
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper
                .findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT);
        assertThat(retrievedMessageIterator).containsOnly(message2, message3, message4, message5);
    }
    
    @Test
    public void messagesRetrievedUsingFetchTypeMetadataShouldHaveAtLastMetadataDataLoaded() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Metadata;
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, LIMIT);
        MessageAssert.assertThat(retrievedMessageIterator.next()).isEqualTo(message1, fetchType);
        assertThat(retrievedMessageIterator).isEmpty();
    }

    @Test
    public void messagesRetrievedUsingFetchTypeHeaderShouldHaveHeaderDataLoaded() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Headers;
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, LIMIT);
        MessageAssert.assertThat(retrievedMessageIterator.next()).isEqualTo(message1, fetchType);
        assertThat(retrievedMessageIterator).isEmpty();
    }

    @Test
    public void messagesRetrievedUsingFetchTypeBodyShouldHaveBodyDataLoaded() throws MailboxException, IOException{
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Body;
        Iterator<Message<Id>> retrievedMessageIterator = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message1.getUid()), fetchType, LIMIT);
        MessageAssert.assertThat(retrievedMessageIterator.next()).isEqualTo(message1, fetchType);
        assertThat(retrievedMessageIterator).isEmpty();
    }

    // No limit used for the moment
    @Ignore
    @Test
    public void retrievingMessagesWithALimitShouldLimitTheNumberOfMessages() throws MailboxException {
        int limit = 2;
        saveMessages();
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, limit)).hasSize(2);
    }
    
    @Test
    public void findRecentUidsInMailboxShouldReturnEmptyListWhenNoMessagesMarkedAsRecentArePresentInMailbox() throws MailboxException {
        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).isEmpty();
    }

    @Test
    public void findRecentUidsInMailboxShouldReturnListOfMessagesHoldingFlagsRecent() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.RECENT), FlagsUpdateMode.REPLACE), MessageRange.one(message2.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.RECENT), FlagsUpdateMode.REPLACE), MessageRange.one(message4.getUid()));
        messageMapper.updateFlags(benwaWorkMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.RECENT), FlagsUpdateMode.REPLACE), MessageRange.one(message6.getUid()));
        assertThat(messageMapper.findRecentMessageUidsInMailbox(benwaInboxMailbox)).containsOnly(message2.getUid(), message4.getUid());
    }
    
    @Test
    public void findFirstUnseenMessageUidShouldReturnNullWhenNoUnseenMessagesCanBeFound() throws MailboxException {
        assertThat(messageMapper.findFirstUnseenMessageUid(benwaInboxMailbox)).isNull();
    }

    @Test
    public void findFirstUnseenMessageUidShouldReturnUid1WhenUid1isNotSeen() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.findFirstUnseenMessageUid(benwaInboxMailbox)).isEqualTo(message1.getUid());
    }

    @Test
    public void findFirstUnseenMessageUidShouldReturnUid2WhenUid2isSeen() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.one(message3.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.one(message5.getUid()));
        assertThat(messageMapper.findFirstUnseenMessageUid(benwaInboxMailbox)).isEqualTo(message2.getUid());
    }
    
    @Test
    public void expungeMarkedForDeletionInMailboxShouldReturnEmptyResultOnEmptyMailbox() throws MailboxException, IOException {
        assertThat(messageMapper.expungeMarkedForDeletionInMailbox(benwaInboxMailbox, MessageRange.all())).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void expungeMarkedForDeletionInMailboxShouldReturnEmptyResultWhenNoMessageInMailboxIsDeleted() throws MailboxException, IOException {
        saveMessages();
        assertThat(messageMapper.expungeMarkedForDeletionInMailbox(benwaInboxMailbox, MessageRange.all())).isEmpty();
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message1, message2, message3, message4, message5);
    }

    @Test
    public void expungeShouldReturnCorrectMetadataWithRangeAll() throws MailboxException, IOException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformExpunge(MessageRange.all()))
            .hasSize(2)
            .containsMetadataForMessages(message1, message4);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void expungeShouldModifyUnderlyingStorageWithRangeAll() throws MailboxException, IOException {
        saveMessages();
        markThenPerformExpunge(MessageRange.all());
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message2, message3, message5);
    }

    @Test
    public void expungeShouldReturnCorrectMetadataWithRangeOne() throws MailboxException, IOException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformExpunge(MessageRange.one(message1.getUid())))
            .hasSize(1)
            .containsMetadataForMessages(message1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void expungeShouldModifyUnderlyingStorageWithRangeOne() throws MailboxException, IOException {
        saveMessages();
        markThenPerformExpunge(MessageRange.one(message1.getUid()));
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message4, message2, message3, message5);
    }

    @Test
    public void expungeShouldReturnCorrectMetadataWithRangeFrom() throws MailboxException, IOException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformExpunge(MessageRange.from(message3.getUid())))
            .hasSize(1)
            .containsMetadataForMessages(message4);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void expungeShouldModifyUnderlyingStorageWithRangeFrom() throws MailboxException, IOException {
        saveMessages();
        markThenPerformExpunge(MessageRange.from(message3.getUid()));
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message1, message2, message3, message5);
    }

    @Test
    public void expungeShouldReturnCorrectMetadataWithRange() throws MailboxException, IOException {
        saveMessages();
        MetadataMapAssert.assertThat(markThenPerformExpunge(MessageRange.range(message3.getUid(), message5.getUid())))
            .hasSize(1)
            .containsMetadataForMessages(message4);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void expungeShouldModifyUnderlyingStorageWithRange() throws MailboxException, IOException {
        saveMessages();
        markThenPerformExpunge(MessageRange.range(message3.getUid(), message5.getUid()));
        assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.all(), MessageMapper.FetchType.Full, LIMIT))
            .containsOnly(message1, message2, message3, message5);
    }

    @Test
    public void getHighestMoseqShouldBeEqualToZeroOnEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.getHighestModSeq(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    public void insertingAMessageShouldIncrementModSeq() throws MailboxException {
        messageMapper.add(benwaInboxMailbox, message1);
        long modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(modSeq).isGreaterThan(0);
        messageMapper.add(benwaInboxMailbox, message2);
        assertThat(messageMapper.getHighestModSeq(benwaInboxMailbox)).isGreaterThan(modSeq);
    }

    @Test
    public void getLastUidShouldReturn0OnEmptyMailbox() throws MailboxException {
        assertThat(messageMapper.getLastUid(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    public void insertingAMessageShouldIncrementLastUid() throws MailboxException {
        messageMapper.add(benwaInboxMailbox, message1);
        long uid = messageMapper.getLastUid(benwaInboxMailbox);
        assertThat(uid).isGreaterThan(0);
        messageMapper.add(benwaInboxMailbox, message2);
        assertThat(messageMapper.getLastUid(benwaInboxMailbox)).isGreaterThan(uid);
    }

    @Test
    public void copyShouldIncrementUid() throws MailboxException, IOException {
        saveMessages();
        long uid = messageMapper.getLastUid(benwaInboxMailbox);
        messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(messageMapper.getLastUid(benwaInboxMailbox)).isGreaterThan(uid);
    }

    @Test
    public void copyShouldIncrementMessageCount() throws MailboxException, IOException {
        saveMessages();
        messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(messageMapper.countMessagesInMailbox(benwaInboxMailbox)).isEqualTo(6);
    }

    @Test
    public void copyOfUnSeenMessageShouldIncrementUnSeenMessageCount() throws MailboxException, IOException {
        saveMessages();
        messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(6);
    }

    @Test
    public void copyShouldIncrementModSeq() throws MailboxException, IOException {
        saveMessages();
        long modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(messageMapper.getHighestModSeq(benwaInboxMailbox)).isGreaterThan(modSeq);
    }

    @Test
    public void copyShouldCreateAMessageInDestination() throws MailboxException, IOException {
        saveMessages();
        Message<Id> message7 = new SimpleMessage<Id>(benwaInboxMailbox, message6);
        messageMapper.copy(benwaInboxMailbox, message7);
        message7.setModSeq(messageMapper.getHighestModSeq(benwaInboxMailbox));
        MessageAssert.assertThat(messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message7.getUid()), MessageMapper.FetchType.Full, LIMIT).next())
            .isEqualTo(message7, MessageMapper.FetchType.Full);
    }
    
    @Test
    public void copyOfSeenMessageShouldNotIncrementUnSeenMessageCount() throws MailboxException {
        message6.setFlags(new Flags(Flags.Flag.SEEN));
        messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(messageMapper.countUnseenMessagesInMailbox(benwaInboxMailbox)).isEqualTo(0);
    }

    @Test
    public void copiedMessageShouldBeMarkedAsRecent() throws MailboxException {
        MessageMetaData metaData = messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(
            messageMapper.findInMailbox(benwaInboxMailbox,
                MessageRange.one(metaData.getUid()),
                MessageMapper.FetchType.Metadata,
                LIMIT
            ).next()
            .isRecent()
        ).isTrue();
    }

    @Test
    public void copiedRecentMessageShouldBeMarkedAsRecent() throws MailboxException {
        message6.setFlags(new Flags(Flags.Flag.RECENT));
        MessageMetaData metaData = messageMapper.copy(benwaInboxMailbox, new SimpleMessage<Id>(benwaInboxMailbox, message6));
        assertThat(
            messageMapper.findInMailbox(benwaInboxMailbox,
                MessageRange.one(metaData.getUid()),
                MessageMapper.FetchType.Metadata,
                LIMIT
            ).next()
                .isRecent()
        ).isTrue();
    }

    @Test
    public void flagsReplacementShouldReplaceStoredMessageFlags() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        MessageAssert.assertThat(retrieveMessageFromStorage(message1)).hasFlags(new Flags(Flags.Flag.FLAGGED));
    }

    @Test
    public void flagsReplacementShouldReturnAnUpdatedFlagHighlightingTheReplacement() throws MailboxException {
        saveMessages();
        long modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid())))
            .containsOnly(new UpdatedFlags(message1.getUid(), modSeq + 1, new Flags(), new Flags(Flags.Flag.FLAGGED)));
    }

    @Test
    public void flagsAdditionShouldReturnAnUpdatedFlagHighlightingTheAddition() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        long modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD), MessageRange.one(message1.getUid())))
            .containsOnly(new UpdatedFlags(message1.getUid(), modSeq + 1, new Flags(Flags.Flag.FLAGGED), new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build()));
    }

    @Test
    public void flagsAdditionShouldUpdateStoredMessageFlags() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.FLAGGED), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.ADD), MessageRange.one(message1.getUid()));
        MessageAssert.assertThat(retrieveMessageFromStorage(message1)).hasFlags(new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build());
    }

    @Test
    public void flagsRemovalShouldReturnAnUpdatedFlagHighlightingTheRemoval() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new FlagsBuilder().add(Flags.Flag.FLAGGED, Flags.Flag.SEEN).build(), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        long modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REMOVE), MessageRange.one(message1.getUid())))
            .containsOnly(new UpdatedFlags(message1.getUid(), modSeq + 1, new FlagsBuilder().add(Flags.Flag.SEEN, Flags.Flag.FLAGGED).build(), new Flags(Flags.Flag.FLAGGED)));
    }

    @Test
    public void flagsRemovalShouldUpdateStoredMessageFlags() throws MailboxException {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new FlagsBuilder().add(Flags.Flag.FLAGGED, Flags.Flag.SEEN).build(), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REMOVE), MessageRange.one(message1.getUid()));
        MessageAssert.assertThat(retrieveMessageFromStorage(message1)).hasFlags(new Flags(Flags.Flag.FLAGGED));
    }

    @Test
    public void updateFlagsOnRangeShouldAffectMessagesContainedInThisRange() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.range(message1.getUid(), message3.getUid())))
            .hasSize(3);
    }

    @Test
    public void updateFlagsWithRangeFromShouldAffectMessagesContainedInThisRange() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.from(message3.getUid()))).hasSize(3);
    }

    @Test
    public void updateFlagsWithRangeAllRangeShouldAffectAllMessages() throws MailboxException {
        saveMessages();
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.SEEN), FlagsUpdateMode.REPLACE), MessageRange.all())).hasSize(5);
    }
        
    @Test
    public void messagePropertiesShouldBeStored() throws Exception {
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setMediaType("text");
        propBuilder.setSubType("html");
        propBuilder.setTextualLineCount(2L);
        propBuilder.setProperty(StandardNames.NAMESPACE_RFC_2045, StandardNames.MIME_CONTENT_TRANSFER_ENCODING_NAME, "7bit");
        propBuilder.setProperty(StandardNames.MIME_CONTENT_TYPE_PARAMETER_SPACE, StandardNames.MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME, "US-ASCII");

        SimpleMessage<Id> messageWithProperties = createMessage(benwaWorkMailbox, "Subject: messagePropertiesShouldBeStored \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        Message<Id> message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.Body, 1).next();
        assertThat(message.getProperties()).containsOnlyElementsOf(propBuilder.toProperties());
    }

    @Test
    public void messagePropertiesShouldBeStoredWhenDuplicateEntries() throws Exception {
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setProperty(StandardNames.MIME_CONTENT_LANGUAGE_SPACE, StandardNames.MIME_CONTENT_LANGUAGE_NAME, "us");
        propBuilder.setProperty(StandardNames.MIME_CONTENT_LANGUAGE_SPACE, StandardNames.MIME_CONTENT_LANGUAGE_NAME, "fr");

        SimpleMessage<Id> messageWithProperties = createMessage(benwaWorkMailbox, "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        Message<Id> message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.Body, 1).next();
        assertThat(message.getProperties()).containsOnlyElementsOf(propBuilder.toProperties());
    }

    @Test
    public void messagePropertiesShouldBeStoredWhenNoProperty() throws Exception {
        SimpleMessage<Id> messageWithProperties = createMessage(benwaWorkMailbox, "Subject: messagePropertiesShouldBeStoredWhenNoProperty \n\nBody\n.\n", BODY_START, new PropertyBuilder());
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        Message<Id> message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.Body, 1).next();
        assertThat(message.getProperties()).isEmpty();
    }

    @Test
    public void textualLineCountShouldBeWellStored() throws Exception {
        long textualLineCount = 48L;
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setTextualLineCount(textualLineCount);

        SimpleMessage<Id> messageWithProperties = createMessage(benwaWorkMailbox, "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        Message<Id> message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.Body, 1).next();
        assertThat(message.getTextualLineCount()).isEqualTo(textualLineCount);
    }

    @Test
    public void mediaTypeShouldBeWellStored() throws Exception {
        String mediaType = "plain";
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setMediaType(mediaType);

        SimpleMessage<Id> messageWithProperties = createMessage(benwaWorkMailbox, "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        Message<Id> message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.Body, 1).next();
        assertThat(message.getMediaType()).isEqualTo(mediaType);
    }

    @Test
    public void subTypeShouldBeWellStored() throws Exception {
        String subType = "text";
        PropertyBuilder propBuilder = new PropertyBuilder();
        propBuilder.setSubType(subType);

        SimpleMessage<Id> messageWithProperties = createMessage(benwaWorkMailbox, "Subject: messagePropertiesShouldBeStoredWhenDuplicateEntries \n\nBody\n.\n", BODY_START, propBuilder);
        MessageMetaData messageMetaData = messageMapper.add(benwaInboxMailbox, messageWithProperties);
        Message<Id> message = messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(messageMetaData.getUid()), FetchType.Body, 1).next();
        assertThat(message.getSubType()).isEqualTo(subType);
    }

    @Test
    public void userFlagsShouldBeSupported() throws Exception {
        saveMessages();
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(USER_FLAG), FlagsUpdateMode.ADD), MessageRange.one(message1.getUid()));
        MessageAssert.assertThat(retrieveMessageFromStorage(message1)).hasFlags(new Flags(USER_FLAG));
    }

    @Test
    public void userFlagsUpdateShouldReturnCorrectUpdatedFlags() throws Exception {
        saveMessages();
        long modSeq = messageMapper.getHighestModSeq(benwaInboxMailbox);
        assertThat(messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(USER_FLAG), FlagsUpdateMode.ADD), MessageRange.one(message1.getUid())))
            .containsOnly(new UpdatedFlags(message1.getUid(), modSeq + 1, new Flags(), new Flags(USER_FLAG)));
    }

    @Test
    public void messagesShouldBeSavedWithTheirUserFlags() throws Exception {
        Message<Id> message = new SimpleMessage<Id>(benwaInboxMailbox, message1);
        messageMapper.add(benwaInboxMailbox, message);
        MessageAssert.assertThat(retrieveMessageFromStorage(message)).hasFlags(new Flags(USER_FLAG));
    }

    private Map<Long, MessageMetaData> markThenPerformExpunge(MessageRange range) throws MailboxException {
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REPLACE), MessageRange.one(message1.getUid()));
        messageMapper.updateFlags(benwaInboxMailbox, new FlagsUpdateCalculator(new Flags(Flags.Flag.DELETED), FlagsUpdateMode.REPLACE), MessageRange.one(message4.getUid()));
        return messageMapper.expungeMarkedForDeletionInMailbox(benwaInboxMailbox, range);
    }

    private SimpleMailbox<Id> createMailbox(MailboxPath mailboxPath) {
        SimpleMailbox<Id> mailbox = new SimpleMailbox<Id>(mailboxPath, UID_VALIDITY);
        Id id = mapperProvider.generateId();
        mailbox.setMailboxId(id);
        return mailbox;
    }
    
    private void saveMessages() throws MailboxException {
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

    private Message<Id> retrieveMessageFromStorage(Message<Id> message) throws MailboxException {
        return messageMapper.findInMailbox(benwaInboxMailbox, MessageRange.one(message.getUid()), MessageMapper.FetchType.Metadata, LIMIT).next();
    }
    
    private SimpleMessage<Id> createMessage(Mailbox<Id> mailbox, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMessage<Id>(new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId());
    }
}
