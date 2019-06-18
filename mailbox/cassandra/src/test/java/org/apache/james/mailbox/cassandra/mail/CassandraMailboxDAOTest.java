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

import java.util.List;
import java.util.Optional;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(CassandraRestartExtension.class)
class CassandraMailboxDAOTest {
    private static final int UID_VALIDITY_1 = 145;
    private static final int UID_VALIDITY_2 = 147;
    private static final MailboxPath NEW_MAILBOX_PATH = MailboxPath.forUser("user", "xyz");
    private static CassandraId CASSANDRA_ID_1 = CassandraId.timeBased();
    private static CassandraId CASSANDRA_ID_2 = CassandraId.timeBased();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailboxModule.MODULE,
            CassandraAclModule.MODULE));


    private CassandraMailboxDAO testee;
    private Mailbox mailbox1;
    private Mailbox mailbox2;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());

        mailbox1 = new Mailbox(MailboxPath.forUser("user", "abcd"),
            UID_VALIDITY_1,
            CASSANDRA_ID_1);
        mailbox2 = new Mailbox(MailboxPath.forUser("user", "defg"),
            UID_VALIDITY_2,
            CASSANDRA_ID_2);
    }

    @Test
    void retrieveMailboxShouldReturnEmptyWhenNone() {
        assertThat(testee.retrieveMailbox(CASSANDRA_ID_1).blockOptional())
            .isEmpty();
    }

    @Test
    void saveShouldAddAMailbox() {
        testee.save(mailbox1).block();

        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .blockOptional();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox1);
    }

    @Test
    void saveShouldOverride() {
        testee.save(mailbox1).block();

        mailbox2.setMailboxId(CASSANDRA_ID_1);
        testee.save(mailbox2).block();


        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .blockOptional();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox2);
    }

    @Test
    void retrieveAllMailboxesShouldBeEmptyByDefault() {
        List<Mailbox> mailboxes = testee.retrieveAllMailboxes()
            .collectList()
            .block();

        assertThat(mailboxes).isEmpty();
    }

    @Test
    void retrieveAllMailboxesShouldReturnSingleMailbox() {
        testee.save(mailbox1).block();

        List<Mailbox> mailboxes = testee.retrieveAllMailboxes()
            .collectList()
            .block();

        assertThat(mailboxes).containsOnly(mailbox1);
    }

    @Test
    void retrieveAllMailboxesShouldReturnMultiMailboxes() {
        testee.save(mailbox1).block();
        testee.save(mailbox2).block();

        List<Mailbox> mailboxes = testee.retrieveAllMailboxes()
            .collectList()
            .block();

        assertThat(mailboxes).containsOnly(mailbox1, mailbox2);
    }

    @Test
    void deleteShouldNotFailWhenMailboxIsAbsent() {
        testee.delete(CASSANDRA_ID_1).block();
    }

    @Test
    void deleteShouldRemoveExistingMailbox() {
        testee.save(mailbox1).block();

        testee.delete(CASSANDRA_ID_1).block();

        assertThat(testee.retrieveMailbox(CASSANDRA_ID_1).blockOptional())
            .isEmpty();
    }

    @Test
    void updateShouldNotFailWhenMailboxIsAbsent() {
        testee.updatePath(CASSANDRA_ID_1, NEW_MAILBOX_PATH).block();
    }

    @Test
    void updateShouldChangeMailboxPath() {
        testee.save(mailbox1).block();

        testee.updatePath(CASSANDRA_ID_1, NEW_MAILBOX_PATH).block();

        mailbox1.setNamespace(NEW_MAILBOX_PATH.getNamespace());
        mailbox1.setUser(NEW_MAILBOX_PATH.getUser());
        mailbox1.setName(NEW_MAILBOX_PATH.getName());
        Optional<Mailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1).blockOptional();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox1);
    }
}
