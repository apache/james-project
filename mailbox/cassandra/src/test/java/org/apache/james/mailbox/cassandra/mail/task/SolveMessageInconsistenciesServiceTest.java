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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.apache.james.backends.cassandra.Scenario.Builder.awaitOn;
import static org.apache.james.backends.cassandra.Scenario.Builder.executeNormally;
import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.Scenario;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageMetadata;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.RunningOptions;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageDataDefinition;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.task.Task;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class SolveMessageInconsistenciesServiceTest {

    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final CassandraMessageId MESSAGE_ID_1 = new CassandraMessageId.Factory().fromString("d2bee791-7e63-11ea-883c-95b84008f979");
    private static final CassandraMessageId MESSAGE_ID_2 = new CassandraMessageId.Factory().fromString("eeeeeeee-7e63-11ea-883c-95b84008f979");
    private static final CassandraMessageId MESSAGE_ID_3 = new CassandraMessageId.Factory().fromString("ffffffff-7e63-11ea-883c-95b84008f979");
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(1L);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(2L);
    private static final MessageUid MESSAGE_UID_3 = MessageUid.of(3L);
    private static final ModSeq MOD_SEQ_1 = ModSeq.of(1L);
    private static final ModSeq MOD_SEQ_2 = ModSeq.of(2L);
    private static final PlainBlobId HEADER_BLOB_ID = new PlainBlobId.Factory().of("header");
    private static final PlainBlobId BODY_BLOB_ID = new PlainBlobId.Factory().of("body");
    private static final Date INTERNAL_DATE = new Date(1586000000000L);
    private static final long SIZE = 36L;
    private static final int BODY_START_OCTET = 18;

    private static final CassandraMessageMetadata MESSAGE_1 = metadata(ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
        .modSeq(MOD_SEQ_1)
        .flags(new Flags())
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
        .build());

    private static final CassandraMessageMetadata MESSAGE_1_WITH_SEEN_FLAG = metadata(ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
        .modSeq(MOD_SEQ_1)
        .flags(new Flags(Flags.Flag.SEEN))
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
        .build());

    private static final CassandraMessageMetadata MESSAGE_1_WITH_MOD_SEQ_2 = metadata(ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
        .modSeq(MOD_SEQ_2)
        .flags(new Flags(Flags.Flag.SEEN))
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
        .build());

    private static final CassandraMessageMetadata MESSAGE_2 = metadata(ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_2, MESSAGE_UID_2))
        .modSeq(MOD_SEQ_2)
        .flags(new Flags())
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_2))
        .build());

    // No content is stored for this message within MessageV3
    private static final CassandraMessageMetadata MESSAGE_3 = metadata(ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_3, MESSAGE_UID_3))
        .modSeq(MOD_SEQ_1)
        .flags(new Flags())
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_3))
        .build());

    private static CassandraMessageMetadata metadata(ComposedMessageIdWithMetaData ids) {
        return CassandraMessageMetadata.builder()
            .ids(ids)
            .internalDate(INTERNAL_DATE)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .headerContent(Optional.of(HEADER_BLOB_ID))
            .build();
    }

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(
            CassandraSchemaVersionDataDefinition.MODULE,
            CassandraMessageDataDefinition.MODULE));

    CassandraMessageIdToImapUidDAO imapUidDAO;
    CassandraMessageIdDAO messageIdDAO;
    CassandraMessageDAOV3 messageDAOV3;
    SolveMessageInconsistenciesService testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        PlainBlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), blobIdFactory, CassandraConfiguration.DEFAULT_CONFIGURATION);
        messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), blobIdFactory);
        // Only MessageV3 metadata is read: blobs are never accessed
        messageDAOV3 = new CassandraMessageDAOV3(cassandra.getConf(), cassandra.getTypesProvider(), mock(BlobStore.class),
            blobIdFactory);
        testee = new SolveMessageInconsistenciesService(imapUidDAO, messageIdDAO, messageDAOV3, CassandraConfiguration.DEFAULT_CONFIGURATION);

        saveContent(MESSAGE_ID_1);
        saveContent(MESSAGE_ID_2);
    }

    private void saveContent(CassandraMessageId messageId) {
        messageDAOV3.save(new MessageRepresentation(messageId, INTERNAL_DATE, SIZE, BODY_START_OCTET,
                new ByteContent(new byte[0]), new PropertyBuilder().build(), ImmutableList.of(), HEADER_BLOB_ID, BODY_BLOB_ID))
            .block();
    }

    @Test
    void fixMessageInconsistenciesShouldReturnCompletedWhenNoData() {
        assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixMessageInconsistenciesShouldReturnCompletedWhenConsistentData() {
        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1).block();

        assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenEmpty() {
        testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block()).isEmpty();
            softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block()).isEmpty();
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenConsistent() {
        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                .containsExactlyInAnyOrder(MESSAGE_1);
            softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                .containsExactlyInAnyOrder(MESSAGE_1);
        });
    }

    @Nested
    class ImapUidScanningTest {

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentData() {
            imapUidDAO.insert(MESSAGE_1).block();

            assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void shouldNotConsiderPendingMessageUpdatesAsInconsistency(CassandraCluster cassandra) throws Exception {
            imapUidDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();
            messageIdDAO.insert(MESSAGE_1).block();

            Scenario.Barrier barrier = new Scenario.Barrier(1);
            cassandra.getConf()
                .registerScenario(awaitOn(barrier)
                    .thenExecuteNormally()
                    .times(1)
                    .whenQueryStartsWith("SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

            Context context = new Context();
            Mono<Task.Result> task = testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).subscribeOn(Schedulers.boundedElastic()).cache();
            task.subscribe();

            barrier.awaitCaller();
            messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();
            barrier.releaseCaller();

            task.block();

            // Verify that no inconsistency is fixed
            assertThat(context.snapshot())
                .isEqualTo(Context.Snapshot.builder()
                    .processedImapUidEntries(1)
                    .processedMessageIdEntries(1)
                    .build());
        }

        @Test
        void shouldNotConsiderPendingMessageInsertsAsInconsistency(CassandraCluster cassandra) throws Exception {
            imapUidDAO.insert(MESSAGE_1).block();

            Scenario.Barrier barrier = new Scenario.Barrier(1);
            cassandra.getConf()
                .registerScenario(awaitOn(barrier)
                    .thenExecuteNormally()
                    .times(1)
                    .whenQueryStartsWith("SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

            Context context = new Context();
            Mono<Task.Result> task = testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).subscribeOn(Schedulers.boundedElastic()).cache();
            task.subscribe();

            barrier.awaitCaller();
            messageIdDAO.insert(MESSAGE_1).block();
            barrier.releaseCaller();

            task.block();

            // Verify that no inconsistency is fixed
            assertThat(context.snapshot())
                .isEqualTo(Context.Snapshot.builder()
                    .processedImapUidEntries(1)
                    .processedMessageIdEntries(0)
                    .build());
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentData() {
            imapUidDAO.insert(MESSAGE_1).block();

            testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_1).block().get())
                    .isEqualTo(MESSAGE_1);
            });
        }

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentFlags() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();

            assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentFlags() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();

            testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_1).block().get())
                    .isEqualTo(MESSAGE_1);
            });
        }

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentModSeq() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_MOD_SEQ_2).block();

            assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentModSeq() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_MOD_SEQ_2).block();

            testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_1).block().get())
                    .isEqualTo(MESSAGE_1);
            });
        }

        @Nested
        class FailureTesting {
            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenError(CassandraCluster cassandra) {
                imapUidDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .forever()
                        .whenQueryStartsWith("UPDATE messageidtable SET threadid=:threadid, messageid=:messageid"));

                assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenPartialError(CassandraCluster cassandra) {
                imapUidDAO.insert(MESSAGE_1).block();
                imapUidDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("UPDATE messageidtable SET threadid=:threadid, messageid=:messageid"));

                assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldResolveSuccessPartially(CassandraCluster cassandra) {
                imapUidDAO.insert(MESSAGE_1).block();
                imapUidDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("UPDATE messageidtable SET threadid=:threadid, messageid=:messageid"));

                testee.fixMessageInconsistencies(new Context(), new RunningOptions(1)).block();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_2, Optional.of(MAILBOX_ID)).collectList().block())
                        .containsExactly(MESSAGE_2);
                    softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_2).block().get())
                        .isEqualTo(MESSAGE_2);
                });
            }

            @Test
            void fixMessageInconsistenciesShouldUpdateContextWhenFailedToRetrieveImapUidRecord(CassandraCluster cassandra) {
                Context context = new Context();

                imapUidDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

                testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

                assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedImapUidEntries(1)
                        .errors(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                        .build());
            }

            @Test
            void fixMessageInconsistenciesShouldUpdateContextWhenFailedToRetrieveMessageIdRecord(CassandraCluster cassandra) {
                Context context = new Context();

                imapUidDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

                testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

                assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedImapUidEntries(1)
                        .errors(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                        .build());
            }
        }
    }

    @Nested
    class MessageIdScanningTest {

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();

            assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void shouldNotConsiderPendingMessageDeleteAsInconsistency(CassandraCluster cassandra) throws Exception {
            messageIdDAO.insert(MESSAGE_1).block();

            Scenario.Barrier barrier = new Scenario.Barrier(1);
            cassandra.getConf()
                .registerScenario(awaitOn(barrier)
                    .thenExecuteNormally()
                    .times(1)
                    .whenQueryStartsWith("SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

            Context context = new Context();
            Mono<Task.Result> task = testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).subscribeOn(Schedulers.boundedElastic()).cache();
            task.subscribe();

            barrier.awaitCaller();
            messageIdDAO.delete(MAILBOX_ID, MESSAGE_UID_1).block();
            barrier.releaseCaller();

            task.block();

            // Verify that no inconsistency is fixed
            assertThat(context.snapshot())
                .isEqualTo(Context.Snapshot.builder()
                    .processedImapUidEntries(0)
                    .processedMessageIdEntries(1)
                    .build());
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();

            testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
            });
        }

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenPartialInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_2).block();

            imapUidDAO.insert(MESSAGE_1).block();

            assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolvePartialInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_2).block();

            imapUidDAO.insert(MESSAGE_1).block();

            testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_1);
            });
        }

        @Nested
        class FailureTesting {
            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenError(CassandraCluster cassandra) {
                messageIdDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .forever()
                        .whenQueryStartsWith("DELETE FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

                assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenPartialError(CassandraCluster cassandra) {
                messageIdDAO.insert(MESSAGE_1).block();
                messageIdDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("DELETE FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

                assertThat(testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldResolveSuccessPartially(CassandraCluster cassandra) {
                messageIdDAO.insert(MESSAGE_1).block();
                messageIdDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("DELETE FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

                testee.fixMessageInconsistencies(new Context(), new RunningOptions(1)).block();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                        .isEmpty();
                    softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                        .containsExactly(MESSAGE_1);
                });
            }

            @Test
            void fixMailboxInconsistenciesShouldUpdateContextWhenFailedToRetrieveMessageIdRecord(CassandraCluster cassandra) {
                Context context = new Context();

                messageIdDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

                testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

                assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedMessageIdEntries(1)
                        .errors(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                        .build());
            }

            @Test
            void fixMailboxInconsistenciesShouldUpdateContextWhenFailedToRetrieveImapUidRecord(CassandraCluster cassandra) {
                Context context = new Context();

                messageIdDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM imapuidtable WHERE messageid=:messageid AND mailboxid=:mailboxid"));

                testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

                assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedMessageIdEntries(1)
                        .errors(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                        .build());
            }
        }
    }

    @Test
    void fixMailboxInconsistenciesShouldNotUpdateContextWhenNoData() {
        Context context = new Context();

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot()).isEqualTo(new Context().snapshot());
    }

    @Test
    void fixMessageInconsistenciesShouldUpdateContextWhenConsistentData() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .processedMessageIdEntries(1)
                .build());
    }

    @Test
    void fixMessageInconsistenciesShouldUpdateContextWhenOrphanImapUidMessage() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .addedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                .build());
    }

    @Test
    void orphanImapUidEntryWithoutContentShouldNotBePropagated() {
        imapUidDAO.insert(MESSAGE_3).block();

        testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_3, Optional.of(MAILBOX_ID)).collectList().block())
                .containsExactly(MESSAGE_3);
            softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_3).block())
                .isEmpty();
        });
    }

    @Test
    void orphanImapUidEntryWithoutContentShouldBeReportedAsError() {
        Context context = new Context();
        imapUidDAO.insert(MESSAGE_3).block();

        Task.Result result = testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(Task.Result.PARTIAL);
            softly.assertThat(context.snapshot())
                .isEqualTo(Context.Snapshot.builder()
                    .processedImapUidEntries(1)
                    .errors(MESSAGE_3.getComposedMessageId().getComposedMessageId())
                    .build());
        });
    }

    @Nested
    class CleanupEntriesWithoutContentTest {
        private static final RunningOptions CLEANUP = new RunningOptions(100, true);

        @Test
        void cleanupShouldRemoveEntriesWithoutContent() {
            // Resurrected in both tables: ImapUid and MessageId are consistent with each other
            imapUidDAO.insert(MESSAGE_3).block();
            messageIdDAO.insert(MESSAGE_3).block();

            testee.fixMessageInconsistencies(new Context(), CLEANUP).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
            });
        }

        @Test
        void cleanupShouldReportRemovedEntries() {
            Context context = new Context();
            imapUidDAO.insert(MESSAGE_3).block();
            messageIdDAO.insert(MESSAGE_3).block();

            Task.Result result = testee.fixMessageInconsistencies(context, CLEANUP).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(Task.Result.COMPLETED);
                softly.assertThat(context.snapshot())
                    .isEqualTo(Context.Snapshot.builder()
                        .processedImapUidEntries(1)
                        .processedMessageIdEntries(1)
                        .removedImapUidEntries(1)
                        .addFixedInconsistencies(MESSAGE_3.getComposedMessageId().getComposedMessageId())
                        .build());
            });
        }

        @Test
        void cleanupShouldRemoveOrphanImapUidEntriesWithoutContent() {
            imapUidDAO.insert(MESSAGE_3).block();

            testee.fixMessageInconsistencies(new Context(), CLEANUP).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
            });
        }

        @Test
        void cleanupShouldNotRemoveEntriesWithContent() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1).block();

            Task.Result result = testee.fixMessageInconsistencies(new Context(), CLEANUP).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(Task.Result.COMPLETED);
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_1);
            });
        }

        @Test
        void cleanupShouldNotRemoveRecentEntries() {
            // One hour after MESSAGE_ID_3 creation: within the grace period
            Clock clock = Clock.fixed(Instant.parse("2020-04-14T16:24:15Z"), ZoneOffset.UTC);
            testee = new SolveMessageInconsistenciesService(imapUidDAO, messageIdDAO, messageDAOV3, CassandraConfiguration.DEFAULT_CONFIGURATION, clock);
            Context context = new Context();
            imapUidDAO.insert(MESSAGE_3).block();
            messageIdDAO.insert(MESSAGE_3).block();

            Task.Result result = testee.fixMessageInconsistencies(context, CLEANUP).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(Task.Result.PARTIAL);
                softly.assertThat(context.snapshot().getErrors())
                    .containsExactly(MESSAGE_3.getComposedMessageId().getComposedMessageId());
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
            });
        }

        @Test
        void cleanupShouldNotRemoveEntriesWhoseContentAppearsBeforeRemoval(CassandraCluster cassandra) throws Exception {
            imapUidDAO.insert(MESSAGE_3).block();
            messageIdDAO.insert(MESSAGE_3).block();

            // The detection read is executed normally, the re-check read right before the removal is blocked
            Scenario.Barrier barrier = new Scenario.Barrier(1);
            cassandra.getConf()
                .registerScenario(
                    executeNormally()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messagev3 WHERE messageid=:messageid"),
                    awaitOn(barrier)
                        .thenExecuteNormally()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messagev3 WHERE messageid=:messageid"));

            Context context = new Context();
            Mono<Task.Result> task = testee.fixMessageInconsistencies(context, CLEANUP).subscribeOn(Schedulers.boundedElastic()).cache();
            task.subscribe();

            barrier.awaitCaller();
            saveContent(MESSAGE_ID_3);
            barrier.releaseCaller();

            Task.Result result = task.block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(Task.Result.COMPLETED);
                softly.assertThat(context.snapshot().getRemovedImapUidEntries()).isZero();
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
            });
        }

        @Test
        void cleanupShouldNotRemoveEntriesWhenContentReCheckFails(CassandraCluster cassandra) {
            imapUidDAO.insert(MESSAGE_3).block();
            messageIdDAO.insert(MESSAGE_3).block();

            cassandra.getConf()
                .registerScenario(
                    executeNormally()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messagev3 WHERE messageid=:messageid"),
                    fail()
                        .times(1)
                        .whenQueryStartsWith("SELECT * FROM messagev3 WHERE messageid=:messageid"));

            Context context = new Context();
            Task.Result result = testee.fixMessageInconsistencies(context, CLEANUP).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(Task.Result.PARTIAL);
                softly.assertThat(context.snapshot().getErrors())
                    .containsExactly(MESSAGE_3.getComposedMessageId().getComposedMessageId());
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
            });
        }

        @Test
        void entriesWithoutContentShouldNotBeRemovedWhenNoCleanup() {
            imapUidDAO.insert(MESSAGE_3).block();
            messageIdDAO.insert(MESSAGE_3).block();

            Task.Result result = testee.fixMessageInconsistencies(new Context(), RunningOptions.DEFAULT).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(result).isEqualTo(Task.Result.COMPLETED);
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_3);
            });
        }
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenInconsistentModSeq() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1_WITH_MOD_SEQ_2).block();

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .processedMessageIdEntries(1)
                .updatedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                .build());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenInconsistentFlags() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedImapUidEntries(1)
                .processedMessageIdEntries(1)
                .updatedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                .build());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenOrphanMessageIdMessage() {
        Context context = new Context();

        messageIdDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedMessageIdEntries(1)
                .removedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                .build());
    }

    /*
    Error
    Cassandra timeout during SIMPLE write query at consistency QUORUM (1 replica were required but only 0 acknowledged the write)
    Stacktrace
    com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency QUORUM (1 replica were required but only 0 acknowledged the write)
    Caused by: com.datastax.driver.core.exceptions.WriteTimeoutException: Cassandra timeout during SIMPLE write query at consistency QUORUM (1 replica were required but only 0 acknowledged the write)
    https://builds.apache.org/blue/organizations/jenkins/james%2FApacheJames/detail/PR-268/39/tests
    */
    @Test
    @Tag(Unstable.TAG)
    void fixMailboxInconsistenciesShouldUpdateContextWhenDeleteError(CassandraCluster cassandra) {
        Context context = new Context();

        messageIdDAO.insert(MESSAGE_1).block();

        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM messageidtable WHERE mailboxid=:mailboxid AND uid=:uid"));

        testee.fixMessageInconsistencies(context, RunningOptions.DEFAULT).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.Snapshot.builder()
                .processedMessageIdEntries(1)
                .errors(MESSAGE_1.getComposedMessageId().getComposedMessageId())
                .build());
    }
}
