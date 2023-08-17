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
package org.apache.james.pop3server.mailbox.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.pop3server.mailbox.CassandraPop3MetadataStore;
import org.apache.james.pop3server.mailbox.Pop3MetadataModule;
import org.apache.james.pop3server.mailbox.Pop3MetadataStore;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.Context;
import org.apache.james.pop3server.mailbox.task.MetaDataFixInconsistenciesService.RunningOptions;
import org.apache.james.task.Task;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MetaDataFixInconsistenciesServiceTest {

    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final CassandraMessageId MESSAGE_ID_1 = new CassandraMessageId.Factory().fromString("d2bee791-7e63-11ea-883c-95b84008f979");
    private static final CassandraMessageId MESSAGE_ID_2 = new CassandraMessageId.Factory().fromString("eeeeeeee-7e63-11ea-883c-95b84008f979");
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(1L);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(2L);
    private static final ModSeq MOD_SEQ_1 = ModSeq.of(1L);
    private static final ModSeq MOD_SEQ_2 = ModSeq.of(2L);
    private static final String CONTENT_MESSAGE = "CONTENT 123 BLA BLA";

    private static final HashBlobId HEADER_BLOB_ID_1 = new HashBlobId.Factory().forPayload("abc".getBytes());
    private static final CassandraMessageMetadata MESSAGE_1 = CassandraMessageMetadata.builder()
        .ids(ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
            .modSeq(MOD_SEQ_1)
            .flags(new Flags())
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
            .build())
        .internalDate(new Date())
        .bodyStartOctet(18L)
        .size(36L)
        .headerContent(Optional.of(HEADER_BLOB_ID_1))
        .build();

    private static final CassandraMessageMetadata MESSAGE_2 = CassandraMessageMetadata.builder()
        .ids(ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_2, MESSAGE_UID_2))
            .modSeq(MOD_SEQ_2)
            .flags(new Flags())
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_2))
            .build())
        .internalDate(new Date())
        .bodyStartOctet(18L)
        .size(36L)
        .headerContent(Optional.of(HEADER_BLOB_ID_1))
        .build();

    private static final Pop3MetadataStore.StatMetadata STAT_METADATA_1 = new Pop3MetadataStore.StatMetadata(MESSAGE_ID_1, CONTENT_MESSAGE.length());
    private static final Pop3MetadataStore.StatMetadata STAT_METADATA_2 = new Pop3MetadataStore.StatMetadata(MESSAGE_ID_2, CONTENT_MESSAGE.length());
    private static final MessageInconsistenciesEntry MESSAGE_INCONSISTENCIES_ENTRY_1 = MessageInconsistenciesEntry.builder()
        .mailboxId(MAILBOX_ID.serialize())
        .messageId(MESSAGE_ID_1.serialize());

    private static final MessageInconsistenciesEntry MESSAGE_INCONSISTENCIES_ENTRY_2 = MessageInconsistenciesEntry.builder()
        .mailboxId(MAILBOX_ID.serialize())
        .messageId(MESSAGE_ID_2.serialize());

    private static final SimpleMailboxMessage MAILBOX_MESSAGE_1 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_1)
        .mailboxId(MAILBOX_ID)
        .uid(MESSAGE_UID_1)
        .internalDate(new Date())
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
        .bodyStartOctet(16)
        .size(CONTENT_MESSAGE.length())
        .content(new ByteContent(CONTENT_MESSAGE.getBytes(StandardCharsets.UTF_8)))
        .flags(new Flags())
        .properties(new PropertyBuilder())
        .addAttachments(ImmutableList.of())
        .build();

    private static final SimpleMailboxMessage MAILBOX_MESSAGE_2 = SimpleMailboxMessage.builder()
        .messageId(MESSAGE_ID_2)
        .mailboxId(MAILBOX_ID)
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_2))
        .uid(MESSAGE_UID_2)
        .internalDate(new Date())
        .bodyStartOctet(16)
        .size(CONTENT_MESSAGE.length())
        .content(new ByteContent(CONTENT_MESSAGE.getBytes(StandardCharsets.UTF_8)))
        .flags(new Flags())
        .properties(new PropertyBuilder())
        .addAttachments(ImmutableList.of())
        .build();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMessageModule.MODULE,
            CassandraBlobModule.MODULE,
            Pop3MetadataModule.MODULE));

    private CassandraMessageIdToImapUidDAO imapUidDAO;
    private Pop3MetadataStore pop3MetadataStore;
    private CassandraMessageDAOV3 cassandraMessageDAOV3;
    private MetaDataFixInconsistenciesService testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        pop3MetadataStore = new CassandraPop3MetadataStore(cassandra.getConf());
        imapUidDAO = new CassandraMessageIdToImapUidDAO(
            cassandra.getConf(),
            new HashBlobId.Factory(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);

        cassandraMessageDAOV3 = new CassandraMessageDAOV3(
            cassandra.getConf(),
            cassandra.getTypesProvider(),
            CassandraBlobStoreFactory.forTesting(cassandra.getConf(), new RecordingMetricFactory())
                .passthrough(),
            new HashBlobId.Factory());
        testee = new MetaDataFixInconsistenciesService(imapUidDAO, pop3MetadataStore, cassandraMessageDAOV3);
    }

    @Test
    void fixInconsistenciesShouldReturnCompletedWhenNoData() {
        assertThat(testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixInconsistenciesShouldReturnCompletedWhenConsistentData() {
        imapUidDAO.insert(MESSAGE_1).block();
        Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();

        assertThat(testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixInconsistenciesShouldReturnPartialWhenFailure() {
        imapUidDAO.insert(MESSAGE_1).block();
        assertThat(testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.PARTIAL);
    }

    @Test
    void fixInconsistenciesShouldNotAlterStateWhenConsistentData() {
        imapUidDAO.insert(MESSAGE_1).block();
        Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
        testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                .containsExactlyInAnyOrder(MESSAGE_1);
            softly.assertThat(Flux.from(pop3MetadataStore.listAllEntries()).collectList().block())
                .containsExactlyInAnyOrder(new Pop3MetadataStore.FullMetadata(MAILBOX_ID, STAT_METADATA_1));
        });
    }

    @Test
    void contextShouldNotBeUpdatedWhenNoData() {
        Context context = new Context();
        testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();
        assertThat(context.snapshot())
            .isEqualTo(new Context().snapshot());
    }

    @Test
    void contextShouldBeUpdatedWhenConsistentData() {
        Context context = new Context();
        imapUidDAO.insert(MESSAGE_1).block();
        Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
        testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .processedPop3MetaDataStoreEntries(1)
                .build());
    }

    @Test
    void contextShouldBeUpdatedWhenStalePOP3Entries() throws MailboxException {
        Context context = new Context();
        Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
        cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();
        testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedPop3MetaDataStoreEntries(1)
                .stalePOP3Entries(1)
                .addFixedInconsistencies(MESSAGE_INCONSISTENCIES_ENTRY_1)
                .build());
    }

    @Test
    void contextShouldBeUpdatedWhenMissingPOP3Entries() throws MailboxException {
        Context context = new Context();
        imapUidDAO.insert(MESSAGE_1).block();
        cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();

        testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .missingPOP3Entries(1)
                .addFixedInconsistencies(MESSAGE_INCONSISTENCIES_ENTRY_1)
                .build());
    }

    @Test
    void contextShouldBeUpdatedWhenMissingPOP3EntriesAndMissingMailboxMessage() {
        Context context = new Context();
        imapUidDAO.insert(MESSAGE_1).block();
        testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .missingPOP3Entries(1)
                .errors(MESSAGE_INCONSISTENCIES_ENTRY_1)
                .build());
    }

    @Nested
    class ImapUidScanningTest {
        @Test
        void fixInconsistenciesShouldReturnCompletedWhenInconsistentData() throws MailboxException {
            imapUidDAO.insert(MESSAGE_1).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();

            assertThat(testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixInconsistenciesShouldResolveInconsistentData() throws MailboxException {
            imapUidDAO.insert(MESSAGE_1).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();
            testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactlyInAnyOrder(MESSAGE_1);
                softly.assertThat(Flux.from(pop3MetadataStore.listAllEntries()).collectList().block())
                    .containsExactlyInAnyOrder(new Pop3MetadataStore.FullMetadata(MAILBOX_ID, STAT_METADATA_1));
            });
        }

        @Test
        void fixInconsistenciesShouldResolveWhenMixCase() throws MailboxException {
            imapUidDAO.insert(MESSAGE_1).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();
            imapUidDAO.insert(MESSAGE_2).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_2).block();

            Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
            Context context = new Context();
            testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .hasSameElementsAs(ImmutableList.of(MESSAGE_1, MESSAGE_2));
                softly.assertThat(Flux.from(pop3MetadataStore.listAllEntries()).collectList().block())
                    .hasSameElementsAs(ImmutableList.of(new Pop3MetadataStore.FullMetadata(MAILBOX_ID, STAT_METADATA_1),
                        new Pop3MetadataStore.FullMetadata(MAILBOX_ID, STAT_METADATA_2)));
                softly.assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedPop3MetaDataStoreEntries(1)
                        .processedImapUidEntries(2)
                        .missingPOP3Entries(1)
                        .addFixedInconsistencies(MESSAGE_INCONSISTENCIES_ENTRY_2)
                        .build());
            });
        }

        @Test
        void pop3MetaDataShouldCorrectSizeWhenResolved() throws MailboxException {
            imapUidDAO.insert(MESSAGE_1).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();
            testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            assertThat(Flux.from(pop3MetadataStore.stat(MAILBOX_ID))
                .collectList()
                .block()
                .stream()
                .filter(statMetadata -> statMetadata.getMessageId().equals(MESSAGE_ID_1))
                .findFirst()
                .get()
                .getSize())
                .isEqualTo(CONTENT_MESSAGE.length());
        }
    }

    @Nested
    class POP3MetaDataStoreScanningTest {

        @Test
        void fixInconsistenciesShouldReturnCompletedWhenInconsistentData() throws MailboxException {
            Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();

            assertThat(testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixInconsistenciesShouldResolveInconsistentData() throws MailboxException {
            Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();

            testee.fixInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .hasSize(0);
                softly.assertThat(Flux.from(pop3MetadataStore.stat(MAILBOX_ID)).collectList().block())
                    .hasSize(0);
            });
        }

        @Test
        void fixInconsistenciesShouldResolveWhenMixCase() throws MailboxException {
            Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_1)).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_1).block();
            Mono.from(pop3MetadataStore.add(MAILBOX_ID, STAT_METADATA_2)).block();
            cassandraMessageDAOV3.save(MAILBOX_MESSAGE_2).block();

            imapUidDAO.insert(MESSAGE_1).block();
            Context context = new Context();
            testee.fixInconsistencies(context, RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .hasSameElementsAs(ImmutableList.of(MESSAGE_1));
                softly.assertThat(Flux.from(pop3MetadataStore.listAllEntries()).collectList().block())
                    .hasSameElementsAs(ImmutableList.of(new Pop3MetadataStore.FullMetadata(MAILBOX_ID, STAT_METADATA_1)));
                softly.assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedPop3MetaDataStoreEntries(2)
                        .processedImapUidEntries(1)
                        .stalePOP3Entries(1)
                        .addFixedInconsistencies(MESSAGE_INCONSISTENCIES_ENTRY_2)
                        .build());
            });
        }
    }
}
