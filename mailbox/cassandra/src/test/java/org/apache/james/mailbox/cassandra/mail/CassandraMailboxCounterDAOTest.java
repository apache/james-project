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
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxCounterModule;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraMailboxCounterDAOTest {
    public static final int UID_VALIDITY = 15;
    public static final CassandraId MAILBOX_ID = CassandraId.timeBased();

    private CassandraCluster cassandra;
    private CassandraMailboxCounterDAO testee;
    private SimpleMailbox mailbox;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(new CassandraMailboxCounterModule());
        cassandra.ensureAllTables();

        testee = new CassandraMailboxCounterDAO(cassandra.getConf());

        mailbox = new SimpleMailbox(new MailboxPath("#private", "user", "name"), UID_VALIDITY, MAILBOX_ID);
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void countMessagesInMailboxShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.countMessagesInMailbox(mailbox).join()).isEmpty();
    }

    @Test
    public void countUnseenMessagesInMailboxShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.countUnseenMessagesInMailbox(mailbox).join()).isEmpty();
    }

    @Test
    public void retrieveMailboxCounterShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.retrieveMailboxCounters(mailbox).join()).isEmpty();
    }

    @Test
    public void incrementCountShouldAddOneWhenAbsent() throws Exception {
        testee.incrementCount(MAILBOX_ID).join();

        assertThat(testee.countMessagesInMailbox(mailbox).join()).contains(1L);
    }

    @Test
    public void incrementUnseenShouldAddOneWhenAbsent() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).join();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).join()).contains(1L);
    }

    @Test
    public void incrementUnseenShouldAddOneWhenAbsentOnMailboxCounters() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).join();

        assertThat(testee.retrieveMailboxCounters(mailbox).join())
            .contains(MailboxCounters.builder()
                .count(0L)
                .unseen(1L)
                .build());
    }

    @Test
    public void incrementCountShouldAddOneWhenAbsentOnMailboxCounters() throws Exception {
        testee.incrementCount(MAILBOX_ID).join();

        assertThat(testee.retrieveMailboxCounters(mailbox).join())
            .contains(MailboxCounters.builder()
                .count(1L)
                .unseen(0L)
                .build());
    }

    @Test
    public void retrieveMailboxCounterShouldWorkWhenFullRow() throws Exception {
        testee.incrementCount(MAILBOX_ID).join();
        testee.incrementUnseen(MAILBOX_ID).join();

        assertThat(testee.retrieveMailboxCounters(mailbox).join())
            .contains(MailboxCounters.builder()
                .count(1L)
                .unseen(1L)
                .build());
    }

    @Test
    public void decrementCountShouldRemoveOne() throws Exception {
        testee.incrementCount(MAILBOX_ID).join();

        testee.decrementCount(MAILBOX_ID).join();

        assertThat(testee.countMessagesInMailbox(mailbox).join())
            .contains(0L);
    }

    @Test
    public void decrementUnseenShouldRemoveOne() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).join();

        testee.decrementUnseen(MAILBOX_ID).join();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).join())
            .contains(0L);
    }

    @Test
    public void incrementUnseenShouldHaveNoImpactOnMessageCount() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).join();

        assertThat(testee.countMessagesInMailbox(mailbox).join())
            .contains(0L);
    }

    @Test
    public void incrementCountShouldHaveNoEffectOnUnseenCount() throws Exception {
        testee.incrementCount(MAILBOX_ID).join();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).join())
            .contains(0L);
    }

    @Test
    public void decrementUnseenShouldHaveNoEffectOnMessageCount() throws Exception {
        testee.incrementCount(MAILBOX_ID).join();

        testee.decrementUnseen(MAILBOX_ID).join();

        assertThat(testee.countMessagesInMailbox(mailbox).join())
            .contains(1L);
    }

    @Test
    public void decrementCountShouldHaveNoEffectOnUnseenCount() throws Exception {
        testee.incrementUnseen(MAILBOX_ID).join();

        testee.decrementCount(MAILBOX_ID).join();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).join())
            .contains(1L);
    }

    @Test
    public void decrementCountCanLeadToNegativeValue() throws Exception {
        testee.decrementCount(MAILBOX_ID).join();

        assertThat(testee.countMessagesInMailbox(mailbox).join())
            .contains(-1L);
    }

    @Test
    public void decrementUnseenCanLeadToNegativeValue() throws Exception {
        testee.decrementUnseen(MAILBOX_ID).join();

        assertThat(testee.countUnseenMessagesInMailbox(mailbox).join())
            .contains(-1L);
    }
}
