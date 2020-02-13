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
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.junit.jupiter.api.Test;

class CassandraMailboxPathV2DAOTest extends CassandraMailboxPathDAOTest<CassandraMailboxPathV2DAO> {
    @Override
    CassandraMailboxPathV2DAO testee(CassandraCluster cassandra) {
        return new CassandraMailboxPathV2DAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    @Test
    void listAllShouldBeEmptyByDefault() {
        assertThat(testee.listAll().collectList().block()).isEmpty();
    }

    @Test
    void listAllShouldContainAddedEntry() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();

        assertThat(testee.listAll().collectList().block())
            .containsExactlyInAnyOrder(INBOX_ID_AND_PATH);
    }

    @Test
    void listAllShouldNotContainDeletedEntry() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();

        testee.delete(USER_INBOX_MAILBOXPATH).block();

        assertThat(testee.listAll().collectList().block())
            .isEmpty();
    }

    @Test
    void listAllShouldContainAddedEntries() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).block();

        assertThat(testee.listAll().collectList().block())
            .containsExactlyInAnyOrder(
                INBOX_ID_AND_PATH,
                new CassandraIdAndPath(otherMailboxId, OTHER_USER_MAILBOXPATH));
    }
}