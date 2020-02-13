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

import java.util.List;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import nl.jqno.equalsverifier.EqualsVerifier;

public abstract class CassandraMailboxPathDAOTest<T extends CassandraMailboxPathDAO> {
    private static final Username USER = Username.of("user");
    private static final Username OTHER_USER = Username.of("other");

    static final CassandraId INBOX_ID = CassandraId.timeBased();
    static final CassandraId OUTBOX_ID = CassandraId.timeBased();
    static final CassandraId otherMailboxId = CassandraId.timeBased();
    static final MailboxPath USER_INBOX_MAILBOXPATH = MailboxPath.forUser(USER, "INBOX");
    static final MailboxPath USER_OUTBOX_MAILBOXPATH = MailboxPath.forUser(USER, "OUTBOX");
    static final MailboxPath OTHER_USER_MAILBOXPATH = MailboxPath.forUser(OTHER_USER, "INBOX");
    static final CassandraIdAndPath INBOX_ID_AND_PATH = new CassandraIdAndPath(INBOX_ID, USER_INBOX_MAILBOXPATH);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        CassandraMailboxModule.MODULE, CassandraSchemaVersionModule.MODULE));

    protected T testee;

    abstract T testee(CassandraCluster cassandra);

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = testee(cassandra);
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
}