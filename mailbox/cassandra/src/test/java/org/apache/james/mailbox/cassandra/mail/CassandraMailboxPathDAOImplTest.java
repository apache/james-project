/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
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
import org.junit.jupiter.api.Test;

class CassandraMailboxPathDAOImplTest extends CassandraMailboxPathDAOTest<CassandraMailboxPathDAOImpl> {

    @Override
    CassandraMailboxPathDAOImpl testee(CassandraCluster cassandra) {
        return new CassandraMailboxPathDAOImpl(cassandra.getConf(), cassandra.getTypesProvider());
    }

    @Test
    void countAllShouldReturnEntryCount() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();
        testee.save(USER_OUTBOX_MAILBOXPATH, OUTBOX_ID).block();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).block();

        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.countAll().block())
            .isEqualTo(3);
    }

    @Test
    void countAllShouldReturnZeroByDefault() {
        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.countAll().block())
            .isEqualTo(0);
    }

    @Test
    void readAllShouldReturnAllStoredData() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();
        testee.save(USER_OUTBOX_MAILBOXPATH, OUTBOX_ID).block();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).block();

        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.readAll().toIterable())
            .containsOnly(
                new CassandraIdAndPath(INBOX_ID, USER_INBOX_MAILBOXPATH),
                new CassandraIdAndPath(OUTBOX_ID, USER_OUTBOX_MAILBOXPATH),
                new CassandraIdAndPath(otherMailboxId, OTHER_USER_MAILBOXPATH));
    }

    @Test
    void readAllShouldReturnEmptyByDefault() {
        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.readAll().toIterable())
            .isEmpty();
    }
}