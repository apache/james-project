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

import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.util.streams.Limit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;

class CassandraMessageIdDAOTest {
    public static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraMessageModule.MODULE,
        CassandraSchemaVersionModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    private CassandraMessageId.Factory messageIdFactory;
    private CassandraMessageIdDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        messageIdFactory = new CassandraMessageId.Factory();
        testee = new CassandraMessageIdDAO(cassandra.getConf(), messageIdFactory);
    }

    @Test
    void deleteShouldNotThrowWhenRowDoesntExist() {
        testee.delete(CassandraId.timeBased(), MessageUid.of(1))
            .block();
    }

    @Test
    void deleteShouldDeleteWhenRowExists() {
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        CassandraMessageId messageId = messageIdFactory.generate();
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        testee.delete(mailboxId, messageUid).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.isPresent()).isFalse();
    }

    @Test
    void outOfOrderUpdatesShouldBeIgnored() {
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        CassandraMessageId messageId = messageIdFactory.generate();
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        testee.delete(mailboxId, messageUid).block();

        testee.updateMetadata(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags(org.apache.james.mailbox.cassandra.table.Flag.ANSWERED))
                .modSeq(ModSeq.of(2))
                .build())
            .block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.isPresent()).isFalse();
    }

    @Test
    void deleteShouldDeleteOnlyConcernedRowWhenMultipleRowExists() {
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        Flux.merge(testee.insert(
            ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build()),
            testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build()))
        .blockLast();

        testee.delete(mailboxId, messageUid).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.isPresent()).isFalse();
        Optional<ComposedMessageIdWithMetaData> messageNotDeleted = testee.retrieve(mailboxId, messageUid2).block();
        assertThat(messageNotDeleted.isPresent()).isTrue();
    }

    @Test
    void insertShouldWork() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        testee.insert(composedMessageIdWithMetaData).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(composedMessageIdWithMetaData);
    }

    @Test
    void updateShouldUpdateModSeq() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateAnsweredFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.ANSWERED))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateDeletedFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.DELETED))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateDraftFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.DRAFT))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateFlaggedFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.FLAGGED))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateRecentFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.RECENT))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateSeenFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.SEEN))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateUserFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.USER))
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void updateShouldUpdateUserFlags() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        Flags flags = new Flags();
        flags.add("myCustomFlag");
        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(flags)
                .modSeq(ModSeq.of(2))
                .build();
        testee.updateMetadata(expectedComposedMessageId).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    void retrieveShouldRetrieveWhenKeyMatches() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        testee.insert(composedMessageIdWithMetaData).block();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).block();

        assertThat(message.get()).isEqualTo(composedMessageIdWithMetaData);
    }

    @Test
    void retrieveMessagesShouldRetrieveAllWhenRangeAll() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        Flux.merge(testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2))
        .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited()).toIterable())
            .containsOnly(composedMessageIdWithMetaData, composedMessageIdWithMetaData2);
    }

    @Test
    void retrieveMessagesShouldApplyLimitWhenRangeAll() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        Flux.merge(testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2))
            .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.all(), Limit.limit(1)).toIterable())
            .containsOnly(composedMessageIdWithMetaData);
    }

    @Test
    void retrieveMessagesShouldRetrieveSomeWhenRangeFrom() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        Flux.merge(testee.insert(
                ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .build()),
                testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2))
        .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.from(messageUid2), Limit.unlimited()).toIterable())
            .containsOnly(composedMessageIdWithMetaData, composedMessageIdWithMetaData2);
    }

    @Test
    void retrieveMessagesShouldAppluLimitWhenRangeFrom() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .build();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .build();
        Flux.merge(testee.insert(
            ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build()),
            testee.insert(composedMessageIdWithMetaData),
            testee.insert(composedMessageIdWithMetaData2))
            .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.from(messageUid2), Limit.limit(1)).toIterable())
            .containsOnly(composedMessageIdWithMetaData);
    }

    @Test
    void retrieveMessagesShouldRetrieveSomeWhenRange() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraMessageId messageId4 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);
        MessageUid messageUid4 = MessageUid.of(4);

        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build())
            .block();

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        Flux.merge(testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2),
                testee.insert(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId4, messageUid4))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .build()))
        .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.range(messageUid2, messageUid3), Limit.unlimited()).toIterable())
            .containsOnly(composedMessageIdWithMetaData, composedMessageIdWithMetaData2);
    }

    @Test
    void retrieveMessagesShouldApplyLimitWhenRange() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraMessageId messageId4 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);
        MessageUid messageUid4 = MessageUid.of(4);

        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
            .build())
            .block();

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .build();

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
            .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
            .flags(new Flags())
            .modSeq(ModSeq.of(1))
            .build();
        Flux.merge(testee.insert(composedMessageIdWithMetaData),
            testee.insert(composedMessageIdWithMetaData2),
            testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId4, messageUid4))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build()))
            .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.range(messageUid2, messageUid3), Limit.limit(1)).toIterable())
            .containsOnly(composedMessageIdWithMetaData);
    }

    @Test
    void retrieveMessagesShouldRetrieveOneWhenRangeOne() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(ModSeq.of(1))
                .build();
        Flux.merge(testee.insert(
                ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .build()),
                testee.insert(composedMessageIdWithMetaData),
                testee.insert(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
                    .flags(new Flags())
                    .modSeq(ModSeq.of(1))
                    .build()))
        .blockLast();

        assertThat(testee.retrieveMessages(mailboxId, MessageRange.one(messageUid2), Limit.unlimited()).toIterable())
            .containsOnly(composedMessageIdWithMetaData);
    }
}
