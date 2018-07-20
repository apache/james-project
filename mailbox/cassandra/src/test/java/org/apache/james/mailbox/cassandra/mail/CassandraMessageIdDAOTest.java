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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class CassandraMessageIdDAOTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    private CassandraMessageId.Factory messageIdFactory;
    private CassandraMessageIdDAO testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(new CassandraMessageModule(), cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        messageIdFactory = new CassandraMessageId.Factory();
        testee = new CassandraMessageIdDAO(cassandra.getConf(), messageIdFactory);
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void deleteShouldNotThrowWhenRowDoesntExist() {
        testee.delete(CassandraId.timeBased(), MessageUid.of(1))
            .join();
    }

    @Test
    public void deleteShouldDeleteWhenRowExists() {
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        CassandraMessageId messageId = messageIdFactory.generate();
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        testee.delete(mailboxId, messageUid).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.isPresent()).isFalse();
    }

    @Test
    public void deleteShouldDeleteOnlyConcernedRowWhenMultipleRowExists() {
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CompletableFuture.allOf(testee.insert(
                ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(1)
                    .build()),
                testee.insert(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                    .flags(new Flags())
                    .modSeq(1)
                    .build()))
        .join();

        testee.delete(mailboxId, messageUid).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.isPresent()).isFalse();
        Optional<ComposedMessageIdWithMetaData> messageNotDeleted = testee.retrieve(mailboxId, messageUid2).join();
        assertThat(messageNotDeleted.isPresent()).isTrue();
    }

    @Test
    public void insertShouldWork() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(1)
                .build();
        testee.insert(composedMessageIdWithMetaData).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(composedMessageIdWithMetaData);
    }

    @Test
    public void updateShouldUpdateModSeq() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateAnsweredFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.ANSWERED))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateDeletedFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.DELETED))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateDraftFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.DRAFT))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateFlaggedFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.FLAGGED))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateRecentFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.RECENT))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateSeenFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.SEEN))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateUserFlag() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags(Flag.USER))
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void updateShouldUpdateUserFlags() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        ComposedMessageId composedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        testee.insert(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(new Flags())
                .modSeq(1)
                .build())
            .join();

        Flags flags = new Flags();
        flags.add("myCustomFlag");
        ComposedMessageIdWithMetaData expectedComposedMessageId = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(composedMessageId)
                .flags(flags)
                .modSeq(2)
                .build();
        testee.updateMetadata(expectedComposedMessageId).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(expectedComposedMessageId);
    }

    @Test
    public void retrieveShouldRetrieveWhenKeyMatches() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(1)
                .build();
        testee.insert(composedMessageIdWithMetaData).join();

        Optional<ComposedMessageIdWithMetaData> message = testee.retrieve(mailboxId, messageUid).join();

        assertThat(message.get()).isEqualTo(composedMessageIdWithMetaData);
    }

    @Test
    public void retrieveMessagesShouldRetrieveAllWhenRangeAll() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                .flags(new Flags())
                .modSeq(1)
                .build();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(1)
                .build();
        CompletableFuture.allOf(testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2))
        .join();

        List<ComposedMessageIdWithMetaData> messages = testee.retrieveMessages(mailboxId, MessageRange.all()).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(composedMessageIdWithMetaData, composedMessageIdWithMetaData2);
    }

    @Test
    public void retrieveMessagesShouldRetrieveSomeWhenRangeFrom() {
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
                .modSeq(1)
                .build();
        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
                .flags(new Flags())
                .modSeq(1)
                .build();
        CompletableFuture.allOf(testee.insert(
                ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(1)
                    .build()),
                testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2))
        .join();

        List<ComposedMessageIdWithMetaData> messages = testee.retrieveMessages(mailboxId, MessageRange.from(messageUid2)).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(composedMessageIdWithMetaData, composedMessageIdWithMetaData2);
    }

    @Test
    public void retrieveMessagesShouldRetrieveSomeWhenRange() {
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
                .modSeq(1)
                .build())
            .join();

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId2, messageUid2))
                .flags(new Flags())
                .modSeq(1)
                .build();

        ComposedMessageIdWithMetaData composedMessageIdWithMetaData2 = ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
                .flags(new Flags())
                .modSeq(1)
                .build();
        CompletableFuture.allOf(testee.insert(composedMessageIdWithMetaData),
                testee.insert(composedMessageIdWithMetaData2),
                testee.insert(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId4, messageUid4))
                    .flags(new Flags())
                    .modSeq(1)
                    .build()))
        .join();

        List<ComposedMessageIdWithMetaData> messages = testee.retrieveMessages(mailboxId, MessageRange.range(messageUid2, messageUid3)).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(composedMessageIdWithMetaData, composedMessageIdWithMetaData2);
    }

    @Test
    public void retrieveMessagesShouldRetrieveOneWhenRangeOne() {
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
                .modSeq(1)
                .build();
        CompletableFuture.allOf(testee.insert(
                ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId, messageUid))
                    .flags(new Flags())
                    .modSeq(1)
                    .build()),
                testee.insert(composedMessageIdWithMetaData),
                testee.insert(ComposedMessageIdWithMetaData.builder()
                    .composedMessageId(new ComposedMessageId(mailboxId, messageId3, messageUid3))
                    .flags(new Flags())
                    .modSeq(1)
                    .build()))
        .join();

        List<ComposedMessageIdWithMetaData> messages = testee.retrieveMessages(mailboxId, MessageRange.one(messageUid2)).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(composedMessageIdWithMetaData);
    }
}
