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

import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.INBOX_ID;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.INBOX_ID_AND_PATH;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.OTHER_USER_MAILBOXPATH;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.OUTBOX_ID;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.USER_INBOX_MAILBOXPATH;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.USER_OUTBOX_MAILBOXPATH;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.otherMailboxId;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraMailboxPathV2DAOTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraMailboxModule.MODULE, CassandraSchemaVersionModule.MODULE));

    CassandraMailboxPathV2DAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailboxPathV2DAO(
            cassandra.getConf(),
            CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            cassandraCluster.getCassandraConsistenciesConfiguration());
    }

    @Test
    void cassandraIdAndPathShouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraIdAndPath.class).verify();
    }

    @Test
    void saveShouldInsertNewEntry() {
        assertThat(testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block()).isTrue();

        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).blockOptional())
            .contains(INBOX_ID_AND_PATH);
    }

    @Test
    void saveOnSecondShouldBeFalse() {
        assertThat(testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block()).isTrue();
        assertThat(testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block()).isFalse();
    }

    @Test
    void retrieveIdShouldReturnEmptyWhenEmptyData() {
        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).blockOptional())
            .isEmpty();
    }

    @Test
    void retrieveIdShouldReturnStoredData() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();

        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).blockOptional())
            .contains(INBOX_ID_AND_PATH);
    }

    @Test
    void getUserMailboxesShouldReturnAllMailboxesOfUser() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();
        testee.save(USER_OUTBOX_MAILBOXPATH, OUTBOX_ID).block();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).block();

        List<CassandraIdAndPath> cassandraIds = testee
            .listUserMailboxes(USER_INBOX_MAILBOXPATH.getNamespace(), USER_INBOX_MAILBOXPATH.getUser())
            .collectList()
            .block();

        assertThat(cassandraIds)
            .hasSize(2)
            .containsOnly(INBOX_ID_AND_PATH, new CassandraIdAndPath(OUTBOX_ID, USER_OUTBOX_MAILBOXPATH));
    }

    @Test
    void deleteShouldNotThrowWhenEmpty() {
        testee.delete(USER_INBOX_MAILBOXPATH).block();
    }

    @Test
    void deleteShouldDeleteTheExistingMailboxId() {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).block();

        testee.delete(USER_INBOX_MAILBOXPATH).block();

        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).blockOptional())
            .isEmpty();
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