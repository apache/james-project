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

import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.MAILBOX_1;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.MAILBOX_2;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.MAILBOX_3;
import static org.apache.james.mailbox.cassandra.mail.MailboxFixture.USER_INBOX_MAILBOXPATH;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.Mailbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import nl.jqno.equalsverifier.EqualsVerifier;

class CassandraMailboxPathV3DAOTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraMailboxModule.MODULE, CassandraSchemaVersionModule.MODULE));

    CassandraMailboxPathV3DAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailboxPathV3DAO(
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
        assertThat(testee.save(MAILBOX_1).block()).isTrue();

        assertThat(testee.retrieve(USER_INBOX_MAILBOXPATH).blockOptional())
            .contains(MAILBOX_1);
    }

    @Test
    void saveOnSecondShouldBeFalse() {
        assertThat(testee.save(MAILBOX_1).block()).isTrue();
        assertThat(testee.save(MAILBOX_1).block()).isFalse();
    }

    @Test
    void retrieveIdShouldReturnEmptyWhenEmptyData() {
        assertThat(testee.retrieve(USER_INBOX_MAILBOXPATH).blockOptional())
            .isEmpty();
    }

    @Test
    void retrieveIdShouldReturnStoredData() {
        testee.save(MAILBOX_1).block();

        assertThat(testee.retrieve(USER_INBOX_MAILBOXPATH).blockOptional())
            .contains(MAILBOX_1);
    }

    @Test
    void getUserMailboxesShouldReturnAllMailboxesOfUser() {
        testee.save(MAILBOX_1).block();
        testee.save(MAILBOX_2).block();
        testee.save(MAILBOX_3).block();

        List<Mailbox> cassandraIds = testee
            .listUserMailboxes(USER_INBOX_MAILBOXPATH.getNamespace(), USER_INBOX_MAILBOXPATH.getUser())
            .collectList()
            .block();

        assertThat(cassandraIds)
            .hasSize(2)
            .containsOnly(MAILBOX_1, MAILBOX_2);
    }

    @Test
    void deleteShouldNotThrowWhenEmpty() {
        testee.delete(USER_INBOX_MAILBOXPATH).block();
    }

    @Test
    void deleteShouldDeleteTheExistingMailboxId() {
        testee.save(MAILBOX_1).block();

        testee.delete(USER_INBOX_MAILBOXPATH).block();

        assertThat(testee.retrieve(USER_INBOX_MAILBOXPATH).blockOptional())
            .isEmpty();
    }

    @Test
    void listAllShouldBeEmptyByDefault() {
        assertThat(testee.listAll().collectList().block()).isEmpty();
    }

    @Test
    void listAllShouldContainAddedEntry() {
        testee.save(MAILBOX_1).block();

        assertThat(testee.listAll().collectList().block())
            .containsExactlyInAnyOrder(MAILBOX_1);
    }

    @Test
    void listAllShouldNotContainDeletedEntry() {
        testee.save(MAILBOX_1).block();

        testee.delete(USER_INBOX_MAILBOXPATH).block();

        assertThat(testee.listAll().collectList().block())
            .isEmpty();
    }

    @Test
    void listAllShouldContainAddedEntries() {
        testee.save(MAILBOX_1).block();
        testee.save(MAILBOX_3).block();

        assertThat(testee.listAll().collectList().block())
            .containsExactlyInAnyOrder(MAILBOX_1, MAILBOX_3);
    }
}