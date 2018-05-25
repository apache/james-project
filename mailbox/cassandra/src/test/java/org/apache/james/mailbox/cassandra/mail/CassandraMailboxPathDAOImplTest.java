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

import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class CassandraMailboxPathDAOImplTest extends CassandraMailboxPathDAOTest {

    @Override
    CassandraMailboxPathDAO testee() {
        return new CassandraMailboxPathDAOImpl(cassandra.getConf(), cassandra.getTypesProvider());
    }

    @Test
    public void countAllShouldReturnEntryCount() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join();
        testee.save(USER_OUTBOX_MAILBOXPATH, OUTBOX_ID).join();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).join();

        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.countAll().join())
            .isEqualTo(3);
    }

    @Test
    public void countAllShouldReturnZeroByDefault() {
        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.countAll().join())
            .isEqualTo(0);
    }

    @Test
    public void readAllShouldReturnAllStoredData() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join();
        testee.save(USER_OUTBOX_MAILBOXPATH, OUTBOX_ID).join();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).join();

        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.readAll().join().collect(Guavate.toImmutableList()))
            .containsOnly(
                new CassandraIdAndPath(INBOX_ID, USER_INBOX_MAILBOXPATH),
                new CassandraIdAndPath(OUTBOX_ID, USER_OUTBOX_MAILBOXPATH),
                new CassandraIdAndPath(otherMailboxId, OTHER_USER_MAILBOXPATH));
    }

    @Test
    public void readAllShouldReturnEmptyByDefault() {
        CassandraMailboxPathDAOImpl daoV1 = (CassandraMailboxPathDAOImpl) testee;

        assertThat(daoV1.readAll().join().collect(Guavate.toImmutableList()))
            .isEmpty();
    }
}