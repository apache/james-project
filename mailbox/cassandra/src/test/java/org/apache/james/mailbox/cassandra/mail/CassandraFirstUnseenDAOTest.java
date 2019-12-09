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
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraFirstUnseenModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraFirstUnseenDAOTest {
    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final MessageUid UID_1 = MessageUid.of(1);
    private static final MessageUid UID_2 = MessageUid.of(2);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraFirstUnseenModule.MODULE);

    private CassandraFirstUnseenDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraFirstUnseenDAO(cassandra.getConf());
    }

    @Test
    void retrieveFirstUnreadShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).hasElement().block())
            .isFalse();
    }

    @Test
    void addUnreadShouldThenBeReportedAsFirstUnseen() {
        testee.addUnread(MAILBOX_ID, UID_1).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).block())
            .isEqualByComparingTo(UID_1);
    }

    @Test
    void retrieveFirstUnreadShouldReturnLowestUnreadUid() {
        testee.addUnread(MAILBOX_ID, UID_1).block();

        testee.addUnread(MAILBOX_ID, UID_2).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).block())
            .isEqualByComparingTo(UID_1);
    }

    @Test
    void retrieveFirstUnreadShouldBeOrderIndependent() {
        testee.addUnread(MAILBOX_ID, UID_2).block();

        testee.addUnread(MAILBOX_ID, UID_1).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).block())
            .isEqualByComparingTo(UID_1);
    }

    @Test
    void addUnreadShouldBeIdempotent() {
        testee.addUnread(MAILBOX_ID, UID_1).block();

        testee.addUnread(MAILBOX_ID, UID_1).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).block())
            .isEqualByComparingTo(UID_1);
    }

    @Test
    void removeUnreadShouldReturnWhenNoData() {
        testee.removeUnread(MAILBOX_ID, UID_1).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).hasElement().block())
            .isFalse();
    }

    @Test
    void removeUnreadShouldRemoveOnlyUnread() {
        testee.addUnread(MAILBOX_ID, UID_1).block();

        testee.removeUnread(MAILBOX_ID, UID_1).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).hasElement().block())
            .isFalse();
    }

    @Test
    void removeUnreadShouldRemoveLastUnread() {
        testee.addUnread(MAILBOX_ID, UID_1).block();
        testee.addUnread(MAILBOX_ID, UID_2).block();

        testee.removeUnread(MAILBOX_ID, UID_2).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).block())
            .isEqualByComparingTo(UID_1);
    }

    @Test
    void removeUnreadShouldHaveNoEffectWhenNotLast() {
        testee.addUnread(MAILBOX_ID, UID_1).block();
        testee.addUnread(MAILBOX_ID, UID_2).block();

        testee.removeUnread(MAILBOX_ID, UID_1).block();

        assertThat(testee.retrieveFirstUnread(MAILBOX_ID).block())
            .isEqualByComparingTo(UID_2);
    }
}
