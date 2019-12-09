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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxCounterDAOTest {
    private static final int UID_VALIDITY = 15;
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraMailboxCounterModule.MODULE);

    private CassandraMailboxCounterDAO testee;
    private Mailbox mailbox;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailboxCounterDAO(cassandra.getConf());

        mailbox = new Mailbox(MailboxPath.forUser(Username.of("user"), "name"), UID_VALIDITY, MAILBOX_ID);
    }

    @Test
    void countMessagesInMailboxShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.countMessagesInMailbox(mailbox).hasElement().block()).isFalse();
    }

    @Test
    void countUnseenMessagesInMailboxShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.countUnseenMessagesInMailbox(mailbox).hasElement().block()).isFalse();
    }

    @Test
    void retrieveMailboxCounterShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).hasElement().block()).isFalse();
    }

    @Test
    void incrementCountShouldAddOneWhenAbsent() throws Exception {
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block()).isEqualTo(1L);
    }

    @Test
    void incrementUnseenShouldAddOneWhenAbsent() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block()).isEqualTo(1L);
    }

    @Test
    void incrementUnseenShouldAddOneWhenAbsentOnMailboxCounters() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(0L)
                .unseen(1L)
                .build());
    }

    @Test
    void incrementCountShouldAddOneWhenAbsentOnMailboxCounters() throws Exception {
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(1L)
                .unseen(0L)
                .build());
    }

    @Test
    void retrieveMailboxCounterShouldWorkWhenFullRow() throws Exception {
        testee.incrementCount(MAILBOX_ID).block();
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(1L)
                .unseen(1L)
                .build());
    }

    @Test
    void decrementCountShouldRemoveOne() throws Exception {
        testee.incrementCount(MAILBOX_ID).block();

        testee.decrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void decrementUnseenShouldRemoveOne() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.decrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void incrementUnseenShouldHaveNoImpactOnMessageCount() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void incrementCountShouldHaveNoEffectOnUnseenCount() throws Exception {
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void decrementUnseenShouldHaveNoEffectOnMessageCount() throws Exception {
        testee.incrementCount(MAILBOX_ID).block();

        testee.decrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(1L);
    }

    @Test
    void decrementCountShouldHaveNoEffectOnUnseenCount() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.decrementCount(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(1L);
    }

    @Test
    void decrementCountCanLeadToNegativeValue() throws Exception {
        testee.decrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(-1L);
    }

    @Test
    void decrementUnseenCanLeadToNegativeValue() throws Exception {
        testee.decrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(-1L);
    }
}
