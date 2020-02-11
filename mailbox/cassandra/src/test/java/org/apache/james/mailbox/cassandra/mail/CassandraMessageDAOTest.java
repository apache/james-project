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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO.MessageIdAttachmentIds;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;

import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.core.publisher.Flux;

class CassandraMessageDAOTest {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);
    private static final List<MessageAttachment> NO_ATTACHMENT = ImmutableList.of();

    public static final CassandraModule MODULES = CassandraModule.aggregateModules(
            CassandraMessageModule.MODULE,
            CassandraBlobModule.MODULE,
            CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            MODULES);

    private CassandraMessageDAO testee;
    private CassandraMessageId.Factory messageIdFactory;

    private SimpleMailboxMessage message;
    private CassandraMessageId messageId;
    private List<ComposedMessageIdWithMetaData> messageIds;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        messageIdFactory = new CassandraMessageId.Factory();
        messageId = messageIdFactory.generate();
        CassandraBlobStore blobStore = CassandraBlobStore.forTesting(cassandra.getConf());
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        testee = new CassandraMessageDAO(cassandra.getConf(), cassandra.getTypesProvider(), blobStore, blobIdFactory,
            new CassandraMessageId.Factory());

        messageIds = ImmutableList.of(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(MAILBOX_ID, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build());
    }

    @Test
    void saveShouldSaveNullValueForTextualLineCountAsZero() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).block();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Metadata, Limit.unlimited()));

        assertThat(attachmentRepresentation.getPropertyBuilder().getTextualLineCount())
            .isEqualTo(0L);
    }

    @Test
    void saveShouldSaveTextualLineCount() throws Exception {
        long textualLineCount = 10L;
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        message = createMessage(messageId, CONTENT, BODY_START, propertyBuilder, NO_ATTACHMENT);

        testee.save(message).block();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Metadata, Limit.unlimited()));

        assertThat(attachmentRepresentation.getPropertyBuilder().getTextualLineCount()).isEqualTo(textualLineCount);
    }

    @Test
    void saveShouldStoreMessageWithFullContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).block();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Full, Limit.unlimited()));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), StandardCharsets.UTF_8))
            .isEqualTo(CONTENT);
    }

    @Test
    void saveShouldStoreMessageWithBodyContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).block();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Body, Limit.unlimited()));

        byte[] expected = Bytes.concat(
            new byte[BODY_START],
            CONTENT.substring(BODY_START).getBytes(StandardCharsets.UTF_8));
        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), StandardCharsets.UTF_8))
            .isEqualTo(IOUtils.toString(new ByteArrayInputStream(expected), StandardCharsets.UTF_8));
    }

    @Test
    void saveShouldStoreMessageWithHeaderContent() throws Exception {
        message = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);

        testee.save(message).block();

        MessageWithoutAttachment attachmentRepresentation =
            toMessage(testee.retrieveMessages(messageIds, MessageMapper.FetchType.Headers, Limit.unlimited()));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent(), StandardCharsets.UTF_8))
            .isEqualTo(CONTENT.substring(0, BODY_START));
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, String content, int bodyStart, PropertyBuilder propertyBuilder, Collection<MessageAttachment> attachments) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .mailboxId(MAILBOX_ID)
            .uid(messageUid)
            .internalDate(new Date())
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new SharedByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .propertyBuilder(propertyBuilder)
            .addAttachments(attachments)
            .build();
    }

    private MessageWithoutAttachment toMessage(Flux<CassandraMessageDAO.MessageResult> read) {
        return read.toStream()
            .map(CassandraMessageDAO.MessageResult::message)
            .map(Pair::getLeft)
            .findAny()
            .orElseThrow(() -> new IllegalStateException("Collection is not supposed to be empty"));
    }

    @Test
    void retrieveAllMessageIdAttachmentIdsShouldReturnEmptyWhenNone() {
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().toStream();
        
        assertThat(actual).isEmpty();
    }

    @Test
    void retrieveAllMessageIdAttachmentIdsShouldReturnOneWhenStored() throws Exception {
        //Given
        MessageAttachment attachment = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment));
        testee.save(message1).block();
        MessageIdAttachmentIds expected = new MessageIdAttachmentIds(messageId, ImmutableSet.of(attachment.getAttachmentId()));
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().toStream();
        
        //Then
        assertThat(actual).containsOnly(expected);
    }

    @Test
    void retrieveAllMessageIdAttachmentIdsShouldReturnOneWhenStoredWithTwoAttachments() throws Exception {
        //Given
        MessageAttachment attachment1 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        MessageAttachment attachment2 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("other content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment1, attachment2));
        testee.save(message1).block();
        MessageIdAttachmentIds expected = new MessageIdAttachmentIds(messageId, ImmutableSet.of(attachment1.getAttachmentId(), attachment2.getAttachmentId()));
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().toStream();
        
        //Then
        assertThat(actual).containsOnly(expected);
    }
    
    @Test
    void retrieveAllMessageIdAttachmentIdsShouldReturnAllWhenStoredWithAttachment() throws Exception {
        //Given
        MessageId messageId1 = messageIdFactory.generate();
        MessageId messageId2 = messageIdFactory.generate();
        MessageAttachment attachment1 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        MessageAttachment attachment2 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("other content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId1, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment1));
        SimpleMailboxMessage message2 = createMessage(messageId2, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachment2));
        testee.save(message1).block();
        testee.save(message2).block();
        MessageIdAttachmentIds expected1 = new MessageIdAttachmentIds(messageId1, ImmutableSet.of(attachment1.getAttachmentId()));
        MessageIdAttachmentIds expected2 = new MessageIdAttachmentIds(messageId2, ImmutableSet.of(attachment2.getAttachmentId()));
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().toStream();
        
        //Then
        assertThat(actual).containsOnly(expected1, expected2);
    }
    
    @Test
    void retrieveAllMessageIdAttachmentIdsShouldReturnEmtpyWhenStoredWithoutAttachment() throws Exception {
        //Given
        SimpleMailboxMessage message1 = createMessage(messageId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);
        testee.save(message1).block();
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().toStream();
        
        //Then
        assertThat(actual).isEmpty();
    }
    
    @Test
    void retrieveAllMessageIdAttachmentIdsShouldFilterMessagesWithoutAttachment() throws Exception {
        //Given
        MessageId messageId1 = messageIdFactory.generate();
        MessageId messageId2 = messageIdFactory.generate();
        MessageId messageId3 = messageIdFactory.generate();
        MessageAttachment attachmentFor1 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        MessageAttachment attachmentFor3 = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("other content".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .build();
        SimpleMailboxMessage message1 = createMessage(messageId1, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachmentFor1));
        SimpleMailboxMessage message2 = createMessage(messageId2, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT);
        SimpleMailboxMessage message3 = createMessage(messageId3, CONTENT, BODY_START, new PropertyBuilder(), ImmutableList.of(attachmentFor3));
        testee.save(message1).block();
        testee.save(message2).block();
        testee.save(message3).block();
        
        //When
        Stream<MessageIdAttachmentIds> actual = testee.retrieveAllMessageIdAttachmentIds().toStream();
        
        //Then
        assertThat(actual).extracting(MessageIdAttachmentIds::getMessageId)
            .containsOnly(messageId1, messageId3);
    }

    @Test
    void messageIdAttachmentIdsShouldMatchBeanContract() {
        EqualsVerifier.forClass(MessageIdAttachmentIds.class)
            .verify();
    }

    @Test
    void messageIdAttachmentIdsShouldThrowOnNullMessageId() {
        assertThatThrownBy(() -> new MessageIdAttachmentIds(null, ImmutableSet.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void messageIdAttachmentIdsShouldThrowOnNullAttachmentIds() {
        assertThatThrownBy(() -> new MessageIdAttachmentIds(messageIdFactory.generate(), null))
            .isInstanceOf(NullPointerException.class);
    }
}
