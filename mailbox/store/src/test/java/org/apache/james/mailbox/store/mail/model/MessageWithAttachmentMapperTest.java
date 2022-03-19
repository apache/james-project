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

import static org.apache.james.mailbox.store.mail.model.MailboxMessage.EMPTY_SAVE_DATE;
import static org.apache.james.mailbox.store.mail.model.MessageAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

public abstract class MessageWithAttachmentMapperTest {

    protected static final int LIMIT = 10;
    private static final int BODY_START = 16;
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);

    protected MapperProvider mapperProvider;
    protected MessageMapper messageMapper;
    private AttachmentMapper attachmentMapper;

    private MailboxMapper mailBoxMapper;

    protected Mailbox attachmentsMailbox;
    
    protected SimpleMailboxMessage messageWithoutAttachment;
    protected SimpleMailboxMessage messageWith1Attachment;
    protected SimpleMailboxMessage messageWith2Attachments;

    protected abstract MapperProvider createMapperProvider();

    @BeforeEach
    void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();

        Assumptions.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MESSAGE));
        Assumptions.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.ATTACHMENT));

        this.messageMapper = mapperProvider.createMessageMapper();
        this.attachmentMapper = mapperProvider.createAttachmentMapper();
        this.mailBoxMapper = mapperProvider.createMailboxMapper();

        attachmentsMailbox = createMailbox(MailboxPath.forUser(Username.of("benwa"), "Attachments"));
        ParsedAttachment attachment1 = ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("attachment".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline();
        ParsedAttachment attachment2 = ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("attachment2".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline();
        ParsedAttachment attachment3 = ParsedAttachment.builder()
            .contentType("content")
            .content(ByteSource.wrap("attachment3".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline(false);

        MessageId messageId1 = mapperProvider.generateMessageId();
        ThreadId threadId1 = ThreadId.fromBaseMessageId(messageId1);
        MessageId messageId2 = mapperProvider.generateMessageId();
        ThreadId threadId2 = ThreadId.fromBaseMessageId(messageId2);
        MessageId messageId3 = mapperProvider.generateMessageId();
        ThreadId threadId3 = ThreadId.fromBaseMessageId(messageId3);
        List<MessageAttachmentMetadata> message1Attachments = attachmentMapper.storeAttachments(ImmutableList.of(attachment1), messageId1);
        List<MessageAttachmentMetadata> message2Attachments = attachmentMapper.storeAttachments(ImmutableList.of(attachment2, attachment3), messageId2);

        messageWith1Attachment = createMessage(attachmentsMailbox, messageId1, threadId1, "Subject: Test7 \n\nBody7\n.\n", BODY_START, new PropertyBuilder(),
                message1Attachments);
        messageWith2Attachments = createMessage(attachmentsMailbox, messageId2, threadId2, "Subject: Test8 \n\nBody8\n.\n", BODY_START, new PropertyBuilder(),
                message2Attachments);
        messageWithoutAttachment = createMessage(attachmentsMailbox, messageId3, threadId3, "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
    }

    @Test
    protected void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenOneAttachment() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(messageWith1Attachment.getAttachments());
    }

    @Test
    protected void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenTwoAttachments() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith2Attachments.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(messageWith2Attachments.getAttachments());
    }

    @Test
    void messagesRetrievedUsingFetchTypeHeadersShouldHaveAttachmentsEmptyWhenOneAttachment() throws MailboxException {
        Assumptions.assumeTrue(mapperProvider.supportPartialAttachmentFetch());
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.HEADERS;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeMetadataShouldHaveAttachmentsEmptyWhenOneAttachment() throws MailboxException {
        Assumptions.assumeTrue(mapperProvider.supportPartialAttachmentFetch());
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.METADATA;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsEmptyWhenNoAttachment() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWithoutAttachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }
    
    @Test
    protected void messagesCanBeRetrievedInMailboxWithRangeTypeOne() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.FULL;
        assertThat(messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT).next())
            .isEqualTo(messageWith1Attachment, fetchType);
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) {
        return mailBoxMapper.create(mailboxPath, UID_VALIDITY).block();
    }
    
    protected void saveMessages() throws MailboxException {
        messageMapper.add(attachmentsMailbox, messageWithoutAttachment);
        messageWithoutAttachment.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
        messageMapper.add(attachmentsMailbox, messageWith1Attachment);
        messageWith1Attachment.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
        messageMapper.add(attachmentsMailbox, messageWith2Attachments);
        messageWith2Attachments.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, MessageId messageId, ThreadId threadId, String content, int bodyStart, PropertyBuilder propertyBuilder, List<MessageAttachmentMetadata> attachments) {
        return new SimpleMailboxMessage(messageId, threadId, new Date(), content.length(), bodyStart, new ByteContent(content.getBytes()), new Flags(), propertyBuilder.build(), mailbox.getMailboxId(), attachments, EMPTY_SAVE_DATE);
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, MessageId messageId, ThreadId threadId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMailboxMessage(messageId, threadId, new Date(), content.length(), bodyStart, new ByteContent(content.getBytes()), new Flags(), propertyBuilder.build(), mailbox.getMailboxId());
    }
}
