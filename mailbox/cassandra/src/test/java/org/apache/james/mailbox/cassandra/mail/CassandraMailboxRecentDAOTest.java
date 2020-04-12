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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.IntStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxRecentDAOTest {
    private static final MessageUid UID1 = MessageUid.of(36L);
    private static final MessageUid UID2 = MessageUid.of(37L);
    private static final CassandraId CASSANDRA_ID = CassandraId.timeBased();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailboxRecentsModule.MODULE);

    private CassandraMailboxRecentsDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailboxRecentsDAO(cassandra.getConf());
    }

    @Test
    void getRecentMessageUidsInMailboxShouldBeEmptyByDefault() {
        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .isEmpty();
    }

    @Test
    void addToRecentShouldAddUidWhenEmpty() {
        testee.addToRecent(CASSANDRA_ID, UID1).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .containsOnly(UID1);
    }

    @Test
    void removeFromRecentShouldRemoveUidWhenOnlyOneUid() {
        testee.addToRecent(CASSANDRA_ID, UID1).block();

        testee.removeFromRecent(CASSANDRA_ID, UID1).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .isEmpty();
    }

    @Test
    void getRecentMessageUidsInMailboxShouldNotReturnDeletedItems() {
        testee.addToRecent(CASSANDRA_ID, UID1).block();
        testee.addToRecent(CASSANDRA_ID, UID2).block();

        testee.delete(CASSANDRA_ID).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .isEmpty();
    }

    @Test
    void deleteShouldNotThrowWhenNothing() {
        assertThatCode(() -> testee.delete(CASSANDRA_ID).block()).doesNotThrowAnyException();
    }

    @Test
    void removeFromRecentShouldNotFailIfNotExisting() {
        testee.removeFromRecent(CASSANDRA_ID, UID1).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .isEmpty();
    }

    @Test
    void addToRecentShouldAddUidWhenNotEmpty() {
        testee.addToRecent(CASSANDRA_ID, UID1).block();

        testee.addToRecent(CASSANDRA_ID, UID2).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .containsOnly(UID1, UID2);
    }

    @Test
    void removeFromRecentShouldOnlyRemoveUidWhenNotEmpty() {
        testee.addToRecent(CASSANDRA_ID, UID1).block();
        testee.addToRecent(CASSANDRA_ID, UID2).block();

        testee.removeFromRecent(CASSANDRA_ID, UID2).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .containsOnly(UID1);
    }

    @Test
    void addToRecentShouldBeIdempotent() {
        testee.addToRecent(CASSANDRA_ID, UID1).block();
        testee.addToRecent(CASSANDRA_ID, UID1).block();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .containsOnly(UID1);
    }

    @Test
    void getRecentMessageUidsInMailboxShouldNotTimeoutWhenOverPagingLimit() {
        int pageSize = 5000;
        int size = pageSize + 1000;
        IntStream.range(0, size)
            .parallel()
            .forEach(i -> testee.addToRecent(CASSANDRA_ID, MessageUid.of(i + 1)).block());

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID)
            .collectList()
            .block())
            .hasSize(size);
    }
}
