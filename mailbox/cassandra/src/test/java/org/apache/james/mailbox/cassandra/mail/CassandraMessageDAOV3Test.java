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

import static org.apache.james.mailbox.store.mail.model.MailboxMessage.EMPTY_SAVE_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

class CassandraMessageDAOV3Test {
    private static final int BODY_START = 16;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final String CONTENT = "Subject: Test7 \n\nBody7\n.\n";
    private static final String CONTENT_2 = "Subject: Test3 \n\nBody23\n.\n";
    private static final MessageUid messageUid = MessageUid.of(1);
    private static final List<MessageAttachmentMetadata> NO_ATTACHMENT = ImmutableList.of();

    public static final CassandraModule MODULES = CassandraModule.aggregateModules(
            CassandraMessageModule.MODULE,
            CassandraBlobModule.MODULE,
            CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            MODULES);

    private CassandraMessageDAOV3 testee;

    private SimpleMailboxMessage message;
    private CassandraMessageId messageId;
    private CassandraMessageId messageId2;
    private ThreadId threadId;
    private ComposedMessageIdWithMetaData messageIdWithMetadata;
    private MessageBlobReferenceSource blobReferenceSource;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        messageId = messageIdFactory.generate();
        messageId2 = messageIdFactory.generate();
        threadId = ThreadId.fromBaseMessageId(messageId);
        BlobStore blobStore = CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
            .passthrough();
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        testee = new CassandraMessageDAOV3(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            blobStore,
            blobIdFactory);

        messageIdWithMetadata = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(MAILBOX_ID, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(threadId)
                .build();
        blobReferenceSource = new MessageBlobReferenceSource(testee);
    }

    @Test
    void saveShouldSaveNullValueForTextualLineCountAsZero() throws Exception {
        message = createMessage(messageId, threadId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT, EMPTY_SAVE_DATE);

        testee.save(message).block();

        MessageRepresentation attachmentRepresentation =
            toMessage(testee.retrieveMessage(messageIdWithMetadata, MessageMapper.FetchType.METADATA));

        assertThat(attachmentRepresentation.getProperties().getTextualLineCount())
            .isEqualTo(0L);
    }

    @Test
    void saveShouldSaveTextualLineCount() throws Exception {
        long textualLineCount = 10L;
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        message = createMessage(messageId, threadId, CONTENT, BODY_START, propertyBuilder, NO_ATTACHMENT, EMPTY_SAVE_DATE);

        testee.save(message).block();

        MessageRepresentation attachmentRepresentation =
            toMessage(testee.retrieveMessage(messageIdWithMetadata, MessageMapper.FetchType.METADATA));

        assertThat(attachmentRepresentation.getProperties().getTextualLineCount()).isEqualTo(textualLineCount);
    }

    @Test
    void saveShouldStoreMessageWithFullContent() throws Exception {
        message = createMessage(messageId, threadId, CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT, EMPTY_SAVE_DATE);

        testee.save(message).block();

        MessageRepresentation attachmentRepresentation =
            toMessage(testee.retrieveMessage(messageIdWithMetadata, MessageMapper.FetchType.FULL));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent().getInputStream(), StandardCharsets.UTF_8))
            .isEqualTo(CONTENT);
    }

    @Test
    void saveShouldStoreMessageWithHeaderContent() throws Exception {
        message = createMessage(messageId, threadId,  CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT, EMPTY_SAVE_DATE);

        testee.save(message).block();

        MessageRepresentation attachmentRepresentation =
            toMessage(testee.retrieveMessage(messageIdWithMetadata, MessageMapper.FetchType.HEADERS));

        assertThat(IOUtils.toString(attachmentRepresentation.getContent().getInputStream(), StandardCharsets.UTF_8))
            .isEqualTo(CONTENT.substring(0, BODY_START));
    }

    @Test
    void blobReferencesShouldBeEmptyByDefault() {
        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .isEmpty();
    }

    @Test
    void blobReferencesShouldReturnAllBlobs() throws Exception {
        message = createMessage(messageId, threadId,  CONTENT, BODY_START, new PropertyBuilder(), NO_ATTACHMENT, EMPTY_SAVE_DATE);
        MailboxMessage message2 = createMessage(messageId2, threadId,  CONTENT_2, BODY_START, new PropertyBuilder(), NO_ATTACHMENT, EMPTY_SAVE_DATE);
        testee.save(message).block();
        testee.save(message2).block();

        assertThat(blobReferenceSource.listReferencedBlobs().collectList().block())
            .hasSize(4);
    }

    private SimpleMailboxMessage createMessage(MessageId messageId, ThreadId threadId, String content, int bodyStart, PropertyBuilder propertyBuilder,
                                               Collection<MessageAttachmentMetadata> attachments, Optional<Date> saveDate) {
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(threadId)
            .mailboxId(MAILBOX_ID)
            .uid(messageUid)
            .internalDate(new Date())
            .saveDate(saveDate)
            .bodyStartOctet(bodyStart)
            .size(content.length())
            .content(new ByteContent(content.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .properties(propertyBuilder)
            .addAttachments(attachments)
            .build();
    }

    private MessageRepresentation toMessage(Mono<MessageRepresentation> read) {
        return read.blockOptional()
            .orElseThrow(() -> new IllegalStateException("Collection is not supposed to be empty"));
    }
}
