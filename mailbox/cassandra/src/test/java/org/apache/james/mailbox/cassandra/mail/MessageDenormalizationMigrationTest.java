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

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreCacheCallback;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.cassandra.CassandraBlobDataDefinition;
import org.apache.james.blob.cassandra.CassandraBlobStoreDAO;
import org.apache.james.blob.cassandra.CassandraBucketDAO;
import org.apache.james.blob.cassandra.CassandraDefaultBucketDAO;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.migration.MessageDenormalizationMigration;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class MessageDenormalizationMigrationTest {
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final MessageUid MESSAGE_UID = MessageUid.of(1);
    private static final String CONTENT = "Subject: test\n\nBody\n";
    private static final int BODY_START = 15;

    public static final CassandraDataDefinition MODULES = CassandraDataDefinition.aggregateModules(
        CassandraMessageDataDefinition.MODULE,
        CassandraBlobDataDefinition.MODULE,
        CassandraSchemaVersionDataDefinition.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULES);

    private CassandraMessageIdDAO messageIdDAO;
    private CassandraMessageIdToImapUidDAO imapUidDAO;
    private CassandraMessageDAOV3 messageDAO;
    private MessageDenormalizationMigration testee;

    private CassandraMessageId messageId;
    private ComposedMessageIdWithMetaData ids;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        PlainBlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        CassandraBlobStoreDAO blobStoreDAO = new CassandraBlobStoreDAO(
            new CassandraDefaultBucketDAO(cassandra.getConf(), blobIdFactory),
            new CassandraBucketDAO(blobIdFactory, cassandra.getConf()),
            CassandraConfiguration.DEFAULT_CONFIGURATION, BucketName.DEFAULT, new RecordingMetricFactory());
        BlobStore blobStore = BlobStoreFactory.builder()
            .blobStoreDAO(blobStoreDAO)
            .blobIdFactory(blobIdFactory)
            .defaultBucketName()
            .passthrough();

        messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), blobIdFactory);
        imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), blobIdFactory,
            CassandraConfiguration.DEFAULT_CONFIGURATION);
        messageDAO = new CassandraMessageDAOV3(cassandra.getConf(), cassandra.getTypesProvider(), blobStore,
            blobStoreDAO, blobIdFactory, CassandraConfiguration.DEFAULT_CONFIGURATION, BlobStoreCacheCallback.NOOP);
        testee = new MessageDenormalizationMigration(messageIdDAO, imapUidDAO, messageDAO);

        messageId = new CassandraMessageId.Factory().generate();
        ids = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(MAILBOX_ID, messageId, MESSAGE_UID))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .build();
    }

    @Test
    void migrationShouldCompleteMessageIdRows() throws Exception {
        saveMessage();
        givenIncompleteRows();

        testee.apply();

        assertThat(retrieveMessageId()).hasValueSatisfying(metadata ->
            assertThat(metadata.isComplete()).isTrue());
    }

    @Test
    void migrationShouldCompleteImapUidRows() throws Exception {
        saveMessage();
        givenIncompleteRows();

        testee.apply();

        assertThat(retrieveImapUid()).hasValueSatisfying(metadata ->
            assertThat(metadata.isComplete()).isTrue());
    }

    @Test
    void migrationShouldCopyTheFieldsOfMessageV3() throws Exception {
        SimpleMailboxMessage message = saveMessage();
        givenIncompleteRows();

        testee.apply();

        CassandraMessageMetadata metadata = retrieveMessageId().get();
        assertThat(metadata.getInternalDate()).contains(message.getInternalDate());
        assertThat(metadata.getBodyStartOctet()).contains((long) BODY_START);
        assertThat(metadata.getSize()).contains((long) CONTENT.length());
        assertThat(metadata.getHeaderContent()).isNotEmpty();
    }

    @Test
    void migrationShouldNotAlterCompleteRows() throws Exception {
        saveMessage();
        givenCompleteRows();
        CassandraMessageMetadata before = retrieveMessageId().get();

        testee.apply();

        assertThat(retrieveMessageId()).contains(before);
    }

    @Test
    void migrationShouldIgnoreRowsWhoseMessageIsMissing() throws Exception {
        givenIncompleteRows();

        testee.apply();

        assertThat(retrieveMessageId()).hasValueSatisfying(metadata ->
            assertThat(metadata.isComplete()).isFalse());
    }

    /**
     * Backfilling is an upsert: a message deleted between the read and the write comes back as a row
     * carrying the denormalized columns alone. Writing one directly is the deterministic way to assert
     * such a row never surfaces, the race itself not being reproducible.
     */
    @Test
    void resurrectedMessageIdRowsShouldNotSurface() {
        messageIdDAO.updateDenormalizedFields(MAILBOX_ID, MESSAGE_UID, new Date(), BODY_START,
            CONTENT.length(), new PlainBlobId.Factory().of("headerBlobId")).block();

        assertThat(retrieveMessageId()).isEmpty();
    }

    @Test
    void resurrectedImapUidRowsShouldNotSurface() {
        imapUidDAO.updateDenormalizedFields(messageId, MAILBOX_ID, MESSAGE_UID, new Date(), BODY_START,
            CONTENT.length(), new PlainBlobId.Factory().of("headerBlobId")).block();

        assertThat(retrieveImapUid()).isEmpty();
    }

    @Test
    void resurrectedRowsShouldBeCleanedUp() {
        messageIdDAO.updateDenormalizedFields(MAILBOX_ID, MESSAGE_UID, new Date(), BODY_START,
            CONTENT.length(), new PlainBlobId.Factory().of("headerBlobId")).block();

        retrieveMessageId();

        Awaitility.await().atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(messageIdDAO.retrieveAllMessages().collectList().block()).isEmpty());
    }

    private SimpleMailboxMessage saveMessage() {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .mailboxId(MAILBOX_ID)
            .uid(MESSAGE_UID)
            .internalDate(new Date())
            .bodyStartOctet(BODY_START)
            .size(CONTENT.length())
            .content(new ByteContent(CONTENT.getBytes(StandardCharsets.UTF_8)))
            .flags(new Flags())
            .addAttachments(ImmutableList.of())
            .build();
        messageDAO.save(message).block();
        return message;
    }

    private void givenIncompleteRows() {
        CassandraMessageMetadata metadata = CassandraMessageMetadata.builder().ids(ids).build();
        messageIdDAO.insertNullInternalDateAndHeaderContent(metadata).block();
        imapUidDAO.insertNullInternalDateAndHeaderContent(metadata).block();
    }

    private void givenCompleteRows() {
        CassandraMessageMetadata metadata = CassandraMessageMetadata.builder()
            .ids(ids)
            .internalDate(new Date())
            .bodyStartOctet((long) BODY_START)
            .size((long) CONTENT.length())
            .headerContent(Optional.of(new PlainBlobId.Factory().of("headerBlobId")))
            .build();
        messageIdDAO.insert(metadata).block();
        imapUidDAO.insert(metadata).block();
    }

    private Optional<CassandraMessageMetadata> retrieveMessageId() {
        return messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID).block();
    }

    private Optional<CassandraMessageMetadata> retrieveImapUid() {
        return imapUidDAO.retrieve(messageId, Optional.of(MAILBOX_ID)).collectList().block()
            .stream().findFirst();
    }
}
