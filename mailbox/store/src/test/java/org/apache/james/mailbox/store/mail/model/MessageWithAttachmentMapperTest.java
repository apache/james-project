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

import static org.apache.james.mailbox.store.mail.model.MessageAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public abstract class MessageWithAttachmentMapperTest {

    private static final int LIMIT = 10;
    private static final int BODY_START = 16;
    private static final UidValidity UID_VALIDITY = UidValidity.of(42);

    private MapperProvider mapperProvider;
    private MessageMapper messageMapper;
    private AttachmentMapper attachmentMapper;

    private Mailbox attachmentsMailbox;
    
    private SimpleMailboxMessage messageWithoutAttachment;
    private SimpleMailboxMessage messageWith1Attachment;
    private SimpleMailboxMessage messageWith2Attachments;

    protected abstract MapperProvider createMapperProvider();

    @BeforeEach
    void setUp() throws Exception {
        this.mapperProvider = createMapperProvider();

        Assumptions.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.MESSAGE));
        Assumptions.assumeTrue(mapperProvider.getSupportedCapabilities().contains(MapperProvider.Capabilities.ATTACHMENT));

        this.messageMapper = mapperProvider.createMessageMapper();
        this.attachmentMapper = mapperProvider.createAttachmentMapper();

        attachmentsMailbox = createMailbox(MailboxPath.forUser(Username.of("benwa"), "Attachments"));
        ParsedAttachment attachment1 = ParsedAttachment.builder()
            .contentType("content")
            .content(new ByteArrayInputStream("attachment".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline();
        ParsedAttachment attachment2 = ParsedAttachment.builder()
            .contentType("content")
            .content(new ByteArrayInputStream("attachment2".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline();
        ParsedAttachment attachment3 = ParsedAttachment.builder()
            .contentType("content")
            .content(new ByteArrayInputStream("attachment3".getBytes(StandardCharsets.UTF_8)))
            .noName()
            .cid(Cid.from("cid"))
            .inline(false);

        MessageId messageId1 = mapperProvider.generateMessageId();
        MessageId messageId2 = mapperProvider.generateMessageId();
        List<MessageAttachment> message1Attachments = attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment1), messageId1);
        List<MessageAttachment> message2Attachments = attachmentMapper.storeAttachmentsForMessage(ImmutableList.of(attachment2, attachment3), messageId2);

        messageWith1Attachment = createMessage(attachmentsMailbox, messageId1, "Subject: Test7 \n\nBody7\n.\n", BODY_START, new PropertyBuilder(),
                message1Attachments);
        messageWith2Attachments = createMessage(attachmentsMailbox, messageId2, "Subject: Test8 \n\nBody8\n.\n", BODY_START, new PropertyBuilder(),
                message2Attachments);
        messageWithoutAttachment = createMessage(attachmentsMailbox, mapperProvider.generateMessageId(), "Subject: Test1 \n\nBody1\n.\n", BODY_START, new PropertyBuilder());
    }

    @Test
    void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenOneAttachment() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(messageWith1Attachment.getAttachments());
    }

    @Test
    void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsLoadedWhenTwoAttachments() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith2Attachments.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(messageWith2Attachments.getAttachments());
    }

    @Test
    void messagesRetrievedUsingFetchTypeBodyShouldHaveAttachmentsLoadedWhenOneAttachment() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Body;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEqualTo(messageWith1Attachment.getAttachments());
    }

    @Test
    void messagesRetrievedUsingFetchTypeHeadersShouldHaveAttachmentsEmptyWhenOneAttachment() throws MailboxException {
        Assumptions.assumeTrue(mapperProvider.supportPartialAttachmentFetch());
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Headers;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeMetadataShouldHaveAttachmentsEmptyWhenOneAttachment() throws MailboxException {
        Assumptions.assumeTrue(mapperProvider.supportPartialAttachmentFetch());
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Metadata;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }

    @Test
    void messagesRetrievedUsingFetchTypeFullShouldHaveAttachmentsEmptyWhenNoAttachment() throws MailboxException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWithoutAttachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next().getAttachments()).isEmpty();
    }
    
    @Test
    void messagesCanBeRetrievedInMailboxWithRangeTypeOne() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Full;
        assertThat(messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT).next())
            .isEqualTo(messageWith1Attachment, fetchType);
    }

    @Test
    void messagesRetrievedUsingFetchTypeBodyShouldHaveBodyDataLoaded() throws MailboxException, IOException {
        saveMessages();
        MessageMapper.FetchType fetchType = MessageMapper.FetchType.Body;
        Iterator<MailboxMessage> retrievedMessageIterator = messageMapper.findInMailbox(attachmentsMailbox, MessageRange.one(messageWith1Attachment.getUid()), fetchType, LIMIT);
        assertThat(retrievedMessageIterator.next()).isEqualTo(messageWith1Attachment, fetchType);
        assertThat(retrievedMessageIterator).toIterable()
            .isEmpty();
    }

    private Mailbox createMailbox(MailboxPath mailboxPath) {
        return new Mailbox(mailboxPath, UID_VALIDITY, mapperProvider.generateId());
    }
    
    private void saveMessages() throws MailboxException {
        messageMapper.add(attachmentsMailbox, messageWithoutAttachment);
        messageWithoutAttachment.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
        messageMapper.add(attachmentsMailbox, messageWith1Attachment);
        messageWith1Attachment.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
        messageMapper.add(attachmentsMailbox, messageWith2Attachments);
        messageWith2Attachments.setModSeq(messageMapper.getHighestModSeq(attachmentsMailbox));
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder, List<MessageAttachment> attachments) {
        return new SimpleMailboxMessage(messageId, new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId(), attachments);
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox, MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder) {
        return new SimpleMailboxMessage(messageId, new Date(), content.length(), bodyStart, new SharedByteArrayInputStream(content.getBytes()), new Flags(), propertyBuilder, mailbox.getMailboxId());
    }
}
