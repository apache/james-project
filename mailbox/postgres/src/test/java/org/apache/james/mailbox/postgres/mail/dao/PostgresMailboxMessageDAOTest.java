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

package org.apache.james.mailbox.postgres.mail.dao;

import static org.apache.james.mailbox.store.mail.MessageMapper.FetchType.METADATA;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxModule;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Limit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;

public class PostgresMailboxMessageDAOTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresMailboxModule.MODULE,
            PostgresMessageModule.MODULE));

    private final MessageId.Factory messageIdFactory = new PostgresMessageId.Factory();
    private final BlobId.Factory blobIdFactory = new PlainBlobId.Factory();

    private PostgresMailboxMessageDAO testee;
    private PostgresMessageDAO messageDAO;

    @BeforeAll
    static void setUpClass() {
        // We set the batch size to 10 to test the batching
        System.setProperty("james.postgresql.query.batch.size", "10");
    }

    @AfterAll
    static void tearDownClass() {
        System.clearProperty("james.postgresql.query.batch.size");
    }

    @BeforeEach
    void setUp() {
        testee = new PostgresMailboxMessageDAO(postgresExtension.getDefaultPostgresExecutor());
        messageDAO = new PostgresMessageDAO(postgresExtension.getDefaultPostgresExecutor(), blobIdFactory);
    }

    @Test
    void findAllRecentMessageMetadataShouldReturnAllMatchingEntryWhenBatchSizeIsSmallerThanAllEntries() {
        // Given 100 entries
        int sampleSize = 100;
        PostgresMailboxId mailboxId = PostgresMailboxId.generate();
        ArrayList<MessageId> messageIds = provisionMailboxMessage(sampleSize, mailboxId);

        // When retrieve all entries
        List<ComposedMessageIdWithMetaData> listResult = testee.findAllRecentMessageMetadata(mailboxId)
            .collectList().block();

        // Then return all entries
        assertThat(listResult).hasSize(sampleSize);

        assertThat(listResult.stream().map(metaData -> metaData.getComposedMessageId().getMessageId()).toList())
            .containsExactly(messageIds.toArray(MessageId[]::new));
    }

    private @NotNull ArrayList<MessageId> provisionMailboxMessage(int sampleSize, PostgresMailboxId mailboxId) {
        ArrayList<MessageId> messageIds = new ArrayList<>();
        Flux.range(1, sampleSize)
            .map(index -> {
                SimpleMailboxMessage mailboxMessage = generateSimpleMailboxMessage(index, mailboxId);
                messageIds.add(mailboxMessage.getMessageId());
                return mailboxMessage;
            })
            .flatMap(message -> messageDAO.insert(message, UUID.randomUUID().toString())
                .then(testee.insert(message)), ReactorUtils.DEFAULT_CONCURRENCY)
            .then().block();
        return messageIds;
    }

    @Test
    void findMessagesByMailboxIdShouldReturnAllMatchingEntryWhenBatchSizeIsSmallerThanAllEntries() {
        // Given 100 entries
        int sampleSize = 100;
        PostgresMailboxId mailboxId = PostgresMailboxId.generate();
        ArrayList<MessageId> messageIds = provisionMailboxMessage(sampleSize, mailboxId);

        // When retrieve all entries
        List<SimpleMailboxMessage> listResult = testee.findMessagesByMailboxId(mailboxId, Limit.unlimited(), METADATA)
            .map(e -> e.getKey().build())
            .collectList().block();

        // Then return all entries
        assertThat(listResult).hasSize(sampleSize);

        assertThat(listResult.stream().map(message -> message.getMessageId()).toList())
            .containsExactly(messageIds.toArray(MessageId[]::new));
    }

    @Test
    void findMessagesByMailboxIdAndBetweenUIDsShouldReturnAllMatchingEntryWhenBatchSizeIsSmallerThanAllEntries() {
        // Given 100 entries
        int sampleSize = 100;
        PostgresMailboxId mailboxId = PostgresMailboxId.generate();
        ArrayList<MessageId> messageIds = provisionMailboxMessage(sampleSize, mailboxId);

        // When retrieve all entries
        List<SimpleMailboxMessage> listResult = testee.findMessagesByMailboxIdAndBetweenUIDs(mailboxId, MessageUid.of(0), MessageUid.of(sampleSize + 1), Limit.unlimited(), METADATA)
            .map(e -> e.getKey().build())
            .collectList().block();

        // Then return all entries
        assertThat(listResult).hasSize(sampleSize);

        assertThat(listResult.stream().map(DelegatingMailboxMessage::getMessageId).toList())
            .containsExactly(messageIds.toArray(MessageId[]::new));
    }

    @Test
    void findMessagesByMailboxIdAndAfterUIDShouldReturnAllMatchingEntryWhenBatchSizeIsSmallerThanAllEntries() {
        // Given 100 entries
        int sampleSize = 100;
        PostgresMailboxId mailboxId = PostgresMailboxId.generate();
        ArrayList<MessageId> messageIds = provisionMailboxMessage(sampleSize, mailboxId);

        // When retrieve all entries
        List<SimpleMailboxMessage> listResult = testee.findMessagesByMailboxIdAndAfterUID(mailboxId, MessageUid.of(0), Limit.unlimited(), METADATA)
            .map(e -> e.getKey().build())
            .collectList().block();

        // Then return all entries
        assertThat(listResult).hasSize(sampleSize);

        assertThat(listResult.stream().map(DelegatingMailboxMessage::getMessageId).toList())
            .containsExactly(messageIds.toArray(MessageId[]::new));
    }

    @Test
    void findMessagesMetadataShouldReturnAllMatchingEntryWhenBatchSizeIsSmallerThanAllEntries() {
        // Given 100 entries
        int sampleSize = 100;
        PostgresMailboxId mailboxId = PostgresMailboxId.generate();
        ArrayList<MessageId> messageIds = provisionMailboxMessage(sampleSize, mailboxId);

        // When retrieve all entries
        List<ComposedMessageIdWithMetaData> listResult = testee.findMessagesMetadata(mailboxId, MessageRange.all())
            .collectList().block();

        // Then return all entries
        assertThat(listResult).hasSize(sampleSize);

        assertThat(listResult.stream().map(metaData -> metaData.getComposedMessageId().getMessageId()).toList())
            .containsExactly(messageIds.toArray(MessageId[]::new));
    }

    private SimpleMailboxMessage generateSimpleMailboxMessage(int index, PostgresMailboxId mailboxId) {
        MessageId messageId = messageIdFactory.generate();
        String messageContent = "Simple message content" + index;
        return SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .uid(MessageUid.of(index))
            .content(new ByteContent((messageContent.getBytes(StandardCharsets.UTF_8))))
            .size(messageContent.length())
            .internalDate(new Date())
            .bodyStartOctet(0)
            .flags(new Flags(Flags.Flag.RECENT))
            .properties(new PropertyBuilder())
            .mailboxId(mailboxId)
            .modseq(ModSeq.of(index))
            .build();
    }
}
