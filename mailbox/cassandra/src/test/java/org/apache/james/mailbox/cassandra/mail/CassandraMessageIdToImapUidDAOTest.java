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

import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;

import reactor.core.publisher.Flux;

class CassandraMessageIdToImapUidDAOTest {
    private static final HashBlobId HEADER_BLOB_ID_1 = new HashBlobId.Factory().forPayload("abc".getBytes());
    private static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraSchemaVersionModule.MODULE,
        CassandraMessageModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private CassandraMessageIdToImapUidDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        testee = new CassandraMessageIdToImapUidDAO(
            cassandra.getConf(),
            new HashBlobId.Factory(),
            CassandraConfiguration.DEFAULT_CONFIGURATION);
    }

    @Test
    void deleteShouldNotThrowWhenRowDoesntExist() {
        testee.delete(CassandraMessageId.Factory.of(Uuids.timeBased()), CassandraId.timeBased())
            .block();
    }

    @Test
    void deleteShouldDeleteWhenRowExists() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build())
                .block();

        testee.delete(messageId, mailboxId).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.of(mailboxId)).collectList().block();
        assertThat(messages).isEmpty();
    }

    @Test
    void deleteShouldDeleteOnlyConcernedRowWhenMultipleRowExists() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        CassandraId mailboxId2 = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        Flux.merge(
            testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build()),
            testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId2, messageId, messageUid2))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build()))
        .blockLast();

        testee.delete(messageId, mailboxId).block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId2, messageId, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void insertShouldWork() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void shouldHandleNullSaveDateWell() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .saveDate(Optional.empty())
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build())
            .block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages.get(0).getSaveDate()).isEmpty();
    }

    @Test
    void shouldHandleSaveDateWell() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        Optional<Date> saveDate = Optional.of(new Date());

        testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .saveDate(saveDate)
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build())
            .block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages.get(0).getSaveDate()).isEqualTo(saveDate);
    }

    @Test
    void updateShouldReturnTrueWhenOldModSeqMatches() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .build();
        testee.insert(CassandraMessageMetadata.builder()
            .ids(composedMessageIdWithMetaData)
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(new Flags(Flags.Flag.ANSWERED))
            .build();

        Boolean result = testee.updateMetadata(composedMessageIdWithMetaData.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        assertThat(result).isTrue();
    }

    @Test
    void updateShouldReturnFalseWhenOldModSeqDoesntMatch() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageIdWithMetaData composedMessageIdWithFlags = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        testee.insert(CassandraMessageMetadata.builder()
            .ids(composedMessageIdWithFlags)
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(new Flags(Flags.Flag.ANSWERED))
            .build();

        Boolean result = testee.updateMetadata(composedMessageIdWithFlags.getComposedMessageId(), updatedFlags, ModSeq.of(3)).block();

        assertThat(result).isFalse();
    }

    @Test
    void updateShouldUpdateModSeq() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.ANSWERED))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();


        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

       testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateAnsweredFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.ANSWERED))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(new Flags(Flags.Flag.ANSWERED))
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateDeletedFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.DELETED))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateDraftFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.DRAFT))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateFlaggedFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.FLAGGED))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateRecentFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.RECENT))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateSeenFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.SEEN))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateUserFlag() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flags.Flag.USER))
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateUserFlags() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        Flags flags = new Flags();
        flags.add("myCustomFlag");
        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(flags)
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags())
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void updateShouldRemoveUserFlags() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        Flags flags = new Flags();
        flags.add("myCustomFlag");
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(flags)
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(2))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(messageUid)
            .messageId(messageId)
            .modSeq(ModSeq.of(2))
            .oldFlags(new Flags("myCustomFlag"))
            .newFlags(expectedComposedMessageId.getFlags())
            .build();

        testee.updateMetadata(expectedComposedMessageId.getComposedMessageId(), updatedFlags, ModSeq.of(1)).block();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void retrieveShouldReturnOneMessageWhenKeyMatches() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        testee.insert(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build())
            .internalDate(new Date())
            .bodyStartOctet(18L)
            .size(36L)
            .headerContent(Optional.of(HEADER_BLOB_ID_1))
            .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId);
    }

    @Test
    void retrieveShouldReturnMultipleMessagesWhenMessageIdMatches() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        CassandraId mailboxId2 = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        Flux.merge(
            testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build()),
            testee.insert(CassandraMessageMetadata.builder()
                .ids(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId2, messageId, messageUid2))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .threadId(ThreadId.fromBaseMessageId(messageId))
                    .build())
                .internalDate(new Date())
                .bodyStartOctet(18L)
                .size(36L)
                .headerContent(Optional.of(HEADER_BLOB_ID_1))
                .build()))
        .blockLast();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();
        ComposedMessageIdWithMetaData expectedComposedMessageId2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId2, messageId, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .threadId(ThreadId.fromBaseMessageId(messageId))
                .build();

        List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
        assertThat(messages)
            .extracting(CassandraMessageMetadata::getComposedMessageId)
            .containsOnly(expectedComposedMessageId, expectedComposedMessageId2);
    }

    @Test
    void retrieveMessageShouldHandlePossibleNullInternalDate() {
        CassandraMessageId messageId = CassandraMessageId.Factory.of(Uuids.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .build();

        testee.insertNullInternalDateAndHeaderContent(CassandraMessageMetadata.builder()
                .ids(expectedComposedMessageId)
                .build())
            .block();

        SoftAssertions.assertSoftly(softAssertions -> {
            softAssertions.assertThatCode(() -> testee.retrieveAllMessages().collectList().block())
                .doesNotThrowAnyException();

            List<CassandraMessageMetadata> messages = testee.retrieve(messageId, Optional.empty()).collectList().block();
            softAssertions.assertThat(messages)
                .extracting(CassandraMessageMetadata::getComposedMessageId)
                .containsOnly(expectedComposedMessageId);

            softAssertions.assertThat(messages)
                .extracting(CassandraMessageMetadata::getInternalDate)
                .containsOnly(Optional.empty());
        });
    }

}