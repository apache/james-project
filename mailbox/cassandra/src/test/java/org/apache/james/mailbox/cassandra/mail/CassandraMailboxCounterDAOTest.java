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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxCounterDAOTest {
    private static final UidValidity UID_VALIDITY = UidValidity.of(15);
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
    void countMessagesInMailboxShouldReturnEmptyByDefault() {
        assertThat(testee.countMessagesInMailbox(mailbox).hasElement().block()).isFalse();
    }

    @Test
    void countUnseenMessagesInMailboxShouldReturnEmptyByDefault() {
        assertThat(testee.countUnseenMessagesInMailbox(mailbox).hasElement().block()).isFalse();
    }

    @Test
    void retrieveMailboxCounterShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).hasElement().block()).isFalse();
    }

    @Test
    void incrementCountShouldAddOneWhenAbsent() {
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block()).isEqualTo(1L);
    }

    @Disabled("Cassandra counter deletion is reversed once counter is incremented cf http://wiki.apache.org/cassandra/Counters")
    @Test
    void deleteShouldResetCounterValueForever() {
        testee.incrementCount(MAILBOX_ID).block();
        testee.delete(MAILBOX_ID).block();
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block()).isEqualTo(1L);
    }

    @Test
    void incrementUnseenAndCountShouldAddOneWhenAbsent() {
        testee.incrementUnseenAndCount(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(1)
                .unseen(1)
                .build());
    }

    @Test
    void incrementUnseenAndCountShouldBeApplicableSeveralTimes() {
        testee.incrementUnseenAndCount(MAILBOX_ID).block();
        testee.incrementUnseenAndCount(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(2)
                .unseen(2)
                .build());
    }

    @Test
    void incrementUnseenShouldAddOneWhenAbsent() {
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block()).isEqualTo(1L);
    }

    @Test
    void incrementUnseenShouldAddOneWhenAbsentOnMailboxCounters() {
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(0L)
                .unseen(1L)
                .build());
    }

    @Test
    void incrementCountShouldAddOneWhenAbsentOnMailboxCounters() {
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(1L)
                .unseen(0L)
                .build());
    }

    @Test
    void retrieveMailboxCounterShouldWorkWhenFullRow() {
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
    void retrieveMailboxCounterShouldNotReturnDeletedItems() {
        testee.incrementCount(MAILBOX_ID).block();
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.delete(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .isEmpty();
    }

    @Test
    void deleteShouldNotThrowWhenNoData() {
        assertThatCode(() -> testee.delete(MAILBOX_ID).block()).doesNotThrowAnyException();
    }

    @Test
    void decrementCountShouldRemoveOne() {
        testee.incrementCount(MAILBOX_ID).block();

        testee.decrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void decrementUnseenShouldRemoveOne() {
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.decrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void incrementUnseenShouldHaveNoImpactOnMessageCount() {
        testee.incrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void incrementCountShouldHaveNoEffectOnUnseenCount() {
        testee.incrementCount(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(0L);
    }

    @Test
    void decrementUnseenShouldHaveNoEffectOnMessageCount() {
        testee.incrementCount(MAILBOX_ID).block();

        testee.decrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(1L);
    }

    @Test
    void decrementCountShouldHaveNoEffectOnUnseenCount() {
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.decrementCount(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(1L);
    }

    @Test
    void decrementCountCanLeadToNegativeValue() {
        testee.decrementCount(MAILBOX_ID).block();

        assertThat(testee.countMessagesInMailbox(mailbox).block())
            .isEqualTo(-1L);
    }

    @Test
    void decrementUnseenAndCountCanLeadToNegativeValue() {
        testee.decrementUnseenAndCount(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.builder()
                .mailboxId(MAILBOX_ID)
                .count(-1)
                .unseen(-1)
                .build());
    }

    @Test
    void decrementUnseenAndCountShouldRevertIncrementUnseenAndCount() {
        testee.incrementUnseenAndCount(MAILBOX_ID).block();

        testee.decrementUnseenAndCount(MAILBOX_ID).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).block())
            .isEqualTo(MailboxCounters.empty(MAILBOX_ID));
    }

    @Test
    void decrementUnseenCanLeadToNegativeValue() {
        testee.decrementUnseen(MAILBOX_ID).block();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).block())
            .isEqualTo(-1L);
    }

    @Test
    void resetCountersShouldNoopWhenZeroAndNoData() {
        MailboxCounters counters = MailboxCounters.empty(MAILBOX_ID);

        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .isEmpty();
    }

    @Test
    void resetCountersShouldNoopWhenZeroAndZeroData() {
        MailboxCounters counters = MailboxCounters.empty(MAILBOX_ID);

        testee.incrementUnseen(MAILBOX_ID).block();
        testee.decrementUnseen(MAILBOX_ID).block();

        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .contains(counters);
    }

    @Test
    void resetCountersShouldReInitCountWhenNothing() {
        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(MAILBOX_ID)
            .count(78)
            .unseen(45)
            .build();

        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .contains(counters);
    }

    @Test
    void resetCountersShouldReInitCountWhenData() {
        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(MAILBOX_ID)
            .count(78)
            .unseen(45)
            .build();

        testee.incrementCount(MAILBOX_ID).block();
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .contains(counters);
    }

    @Test
    void resetCountersShouldBeIdempotent() {
        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(MAILBOX_ID)
            .count(78)
            .unseen(45)
            .build();

        testee.resetCounters(counters).block();
        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .contains(counters);
    }

    @Test
    void resetCountersShouldReInitCountWhenZeroUnseen() {
        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(MAILBOX_ID)
            .count(78)
            .unseen(0)
            .build();

        testee.incrementCount(MAILBOX_ID).block();
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .contains(counters);
    }

    @Test
    void resetCountersShouldReInitCountWhenZeroCount() {
        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(MAILBOX_ID)
            .count(0)
            .unseen(46)
            .build();

        testee.incrementCount(MAILBOX_ID).block();
        testee.incrementUnseen(MAILBOX_ID).block();

        testee.resetCounters(counters).block();

        assertThat(testee.retrieveMailboxCounters(MAILBOX_ID).blockOptional())
            .contains(counters);
    }
}
