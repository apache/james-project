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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.CassandraMessageId;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.utils.UUIDs;
import com.github.steveash.guavate.Guavate;

public class CassandraMessageIdToImapUidDAOTest {

    private CassandraCluster cassandra;
    private CassandraMessageId.Factory messageIdFactory;

    private CassandraMessageIdToImapUidDAO testee;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraMessageModule());
        cassandra.ensureAllTables();

        messageIdFactory = new CassandraMessageId.Factory();
        testee = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), messageIdFactory);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
    }

    @Test
    public void deleteShouldNotThrowWhenRowDoesntExist() {
        testee.delete(messageIdFactory.of(UUIDs.timeBased()), CassandraId.timeBased())
            .join();
    }

    @Test
    public void deleteShouldDeleteWhenRowExists() {
        CassandraMessageId messageId = messageIdFactory.of(UUIDs.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        testee.insert(messageId, mailboxId, messageUid).join();

        testee.delete(messageId, mailboxId).join();

        Stream<ComposedMessageId> messages = testee.retrieve(messageId, Optional.of(mailboxId)).join();
        assertThat(messages.collect(Guavate.toImmutableList())).isEmpty();
    }

    @Test
    public void deleteShouldDeleteOnlyConcernedRowWhenMultipleRowExists() {
        CassandraMessageId messageId = messageIdFactory.of(UUIDs.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        CassandraId mailboxId2 = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        CompletableFuture.allOf(testee.insert(messageId, mailboxId, messageUid),
                testee.insert(messageId, mailboxId2, messageUid2))
        .join();

        testee.delete(messageId, mailboxId).join();

        ComposedMessageId expectedComposedMessageId = new ComposedMessageId(mailboxId2, messageId, messageUid2);
        Stream<ComposedMessageId> messages = testee.retrieve(messageId, Optional.empty()).join();
        assertThat(messages.collect(Guavate.toImmutableList())).containsOnly(expectedComposedMessageId);
    }

    @Test
    public void insertShouldWork() {
        CassandraMessageId messageId = messageIdFactory.of(UUIDs.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);

        testee.insert(messageId, mailboxId, messageUid).join();

        ComposedMessageId expectedComposedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        Stream<ComposedMessageId> messages = testee.retrieve(messageId, Optional.of(mailboxId)).join();
        assertThat(messages.collect(Guavate.toImmutableList())).containsOnly(expectedComposedMessageId);
    }

    @Test
    public void retrieveShouldReturnOneMessageWhenKeyMatches() {
        CassandraMessageId messageId = messageIdFactory.of(UUIDs.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        testee.insert(messageId, mailboxId, messageUid).join();

        ComposedMessageId expectedComposedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        Stream<ComposedMessageId> messages = testee.retrieve(messageId, Optional.of(mailboxId)).join();

        assertThat(messages.collect(Guavate.toImmutableList())).containsOnly(expectedComposedMessageId);
    }

    @Test
    public void retrieveShouldReturnMultipleMessagesWhenMessageIdMatches() {
        CassandraMessageId messageId = messageIdFactory.of(UUIDs.timeBased());
        CassandraId mailboxId = CassandraId.timeBased();
        CassandraId mailboxId2 = CassandraId.timeBased();
        MessageUid messageUid = MessageUid.of(1);
        MessageUid messageUid2 = MessageUid.of(2);
        CompletableFuture.allOf(testee.insert(messageId, mailboxId, messageUid),
                testee.insert(messageId, mailboxId2, messageUid2))
        .join();

        ComposedMessageId expectedComposedMessageId = new ComposedMessageId(mailboxId, messageId, messageUid);
        ComposedMessageId expectedComposedMessageId2 = new ComposedMessageId(mailboxId2, messageId, messageUid2);
        Stream<ComposedMessageId> messages = testee.retrieve(messageId, Optional.empty()).join();

        assertThat(messages.collect(Guavate.toImmutableList())).containsOnly(expectedComposedMessageId, expectedComposedMessageId2);
    }
}
