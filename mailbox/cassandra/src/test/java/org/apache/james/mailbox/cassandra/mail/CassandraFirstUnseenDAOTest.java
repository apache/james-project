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
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraFirstUnseenDAOTest {
    public static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    public static final MessageUid UID_1 = MessageUid.of(1);
    public static final MessageUid UID_2 = MessageUid.of(2);

    private CassandraCluster cassandra;
    private CassandraFirstUnseenDAO testee;

    @Before
    public void setUp() {
        cassandra = CassandraCluster.create(
            new CassandraFirstUnseenModule());
        cassandra.ensureAllTables();

        testee = new CassandraFirstUnseenDAO(cassandra.getConf());
    }

    @After
    public void tearDown() {
        cassandra.clearAllTables();
        cassandra.close();
    }

    @Test
    public void retrieveFirstUnreadShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join().isPresent())
            .isFalse();
    }

    @Test
    public void addUnreadShouldThenBeReportedAsFirstUnseen() {
        testee.addUnread(MAILBOX_ID, UID_1).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .contains(UID_1);
    }

    @Test
    public void retrieveFirstUnreadShouldReturnLowestUnreadUid() {
        testee.addUnread(MAILBOX_ID, UID_1).join();

        testee.addUnread(MAILBOX_ID, UID_2).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .contains(UID_1);
    }

    @Test
    public void retrieveFirstUnreadShouldBeOrderIndependent() {
        testee.addUnread(MAILBOX_ID, UID_2).join();

        testee.addUnread(MAILBOX_ID, UID_1).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .contains(UID_1);
    }

    @Test
    public void addUnreadShouldBeIdempotent() {
        testee.addUnread(MAILBOX_ID, UID_1).join();

        testee.addUnread(MAILBOX_ID, UID_1).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .contains(UID_1);
    }


    @Test
    public void removeUnreadShouldReturnWhenNoData() {
        testee.removeUnread(MAILBOX_ID, UID_1).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .isEmpty();
    }

    @Test
    public void removeUnreadShouldRemoveOnlyUnread() {
        testee.addUnread(MAILBOX_ID, UID_1).join();

        testee.removeUnread(MAILBOX_ID, UID_1).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .isEmpty();
    }

    @Test
    public void removeUnreadShouldRemoveLastUnread() {
        testee.addUnread(MAILBOX_ID, UID_1).join();
        testee.addUnread(MAILBOX_ID, UID_2).join();

        testee.removeUnread(MAILBOX_ID, UID_2).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .contains(UID_1);
    }

    @Test
    public void removeUnreadShouldHaveNoEffectWhenNotLast() {
        testee.addUnread(MAILBOX_ID, UID_1).join();
        testee.addUnread(MAILBOX_ID, UID_2).join();

        testee.removeUnread(MAILBOX_ID, UID_1).join();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).join())
            .contains(UID_2);
    }
}
