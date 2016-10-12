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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.MessageRange;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraMessageIdDAOTest {

    private CassandraCluster cassandra;

    private CassandraMessageId.Factory messageIdFactory;
    private CassandraMessageIdDAO testee;


    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraMessageModule());
        cassandra.ensureAllTables();

        messageIdFactory = new CassandraMessageId.Factory();
        testee = new CassandraMessageIdDAO(cassandra.getConf(), messageIdFactory);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
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
        testee.insert(mailboxId, messageUid, messageId).join();

        testee.delete(mailboxId, messageUid).join();

        Optional<CassandraMessageId> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.isPresent()).isFalse();
    }

    @Test
    public void deleteShouldDeleteOnlyConcernedRowWhenMultipleRowExists() {
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CompletableFuture.allOf(testee.insert(mailboxId, messageUid, messageId),
                testee.insert(mailboxId, messageUid2, messageId2))
        .join();

        testee.delete(mailboxId, messageUid).join();

        Optional<CassandraMessageId> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.isPresent()).isFalse();
        Optional<CassandraMessageId> messageNotDeleted = testee.retrieve(mailboxId, messageUid2).join();
        assertThat(messageNotDeleted.isPresent()).isTrue();
    }

    @Test
    public void insertShouldWork() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        testee.insert(mailboxId, messageUid, messageId).join();

        Optional<CassandraMessageId> message = testee.retrieve(mailboxId, messageUid).join();
        assertThat(message.get()).isEqualTo(messageId);
    }

    @Test
    public void retrieveShouldRetrieveWhenKeyMatches() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        testee.insert(mailboxId, messageUid, messageId).join();

        Optional<CassandraMessageId> message = testee.retrieve(mailboxId, messageUid).join();

        assertThat(message.get()).isEqualTo(messageId);
    }

    @Test
    public void retrieveMessageIdsShouldRetrieveAllWhenRangeAll() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        CompletableFuture.allOf(testee.insert(mailboxId, messageUid, messageId),
                testee.insert(mailboxId, messageUid2, messageId2))
        .join();

        List<CassandraMessageId> messages = testee.retrieveMessageIds(mailboxId, MessageRange.all()).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(messageId, messageId2);
    }

    @Test
    public void retrieveMessageIdsShouldRetrieveSomeWhenRangeFrom() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);
        CompletableFuture.allOf(testee.insert(mailboxId, messageUid, messageId),
                testee.insert(mailboxId, messageUid2, messageId2),
                testee.insert(mailboxId, messageUid3, messageId3))
        .join();

        List<CassandraMessageId> messages = testee.retrieveMessageIds(mailboxId, MessageRange.from(messageUid2)).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(messageId2, messageId3);
    }

    @Test
    public void retrieveMessageIdsShouldRetrieveSomeWhenRange() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraMessageId messageId4 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);
        MessageUid messageUid4 = MessageUid.of(4);
        CompletableFuture.allOf(testee.insert(mailboxId, messageUid, messageId),
                testee.insert(mailboxId, messageUid2, messageId2),
                testee.insert(mailboxId, messageUid3, messageId3),
                testee.insert(mailboxId, messageUid4, messageId4))
        .join();

        List<CassandraMessageId> messages = testee.retrieveMessageIds(mailboxId, MessageRange.range(messageUid2, messageUid3)).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(messageId2, messageId3);
    }

    @Test
    public void retrieveMessageIdsShouldRetrieveOneWhenRangeOne() {
        CassandraMessageId messageId = messageIdFactory.generate();
        CassandraMessageId messageId2 = messageIdFactory.generate();
        CassandraMessageId messageId3 = messageIdFactory.generate();
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        MessageUid messageUid3 = MessageUid.of(3);
        CompletableFuture.allOf(testee.insert(mailboxId, messageUid, messageId),
                testee.insert(mailboxId, messageUid2, messageId2),
                testee.insert(mailboxId, messageUid3, messageId3))
        .join();

        List<CassandraMessageId> messages = testee.retrieveMessageIds(mailboxId, MessageRange.one(messageUid2)).join()
                .collect(Collectors.toList());

        assertThat(messages).containsOnly(messageId2);
    }
}
