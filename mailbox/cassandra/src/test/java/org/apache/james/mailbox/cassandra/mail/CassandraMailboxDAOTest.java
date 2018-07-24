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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class CassandraMailboxDAOTest {

    public static final int UID_VALIDITY_1 = 145;
    public static final int UID_VALIDITY_2 = 147;
    public static final MailboxPath NEW_MAILBOX_PATH = MailboxPath.forUser("user", "xyz");
    public static CassandraId CASSANDRA_ID_1 = CassandraId.timeBased();
    public static CassandraId CASSANDRA_ID_2 = CassandraId.timeBased();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    private static CassandraCluster cassandra;

    private CassandraMailboxDAO testee;
    private SimpleMailbox mailbox1;
    private SimpleMailbox mailbox2;

    @BeforeClass
    public static void setUpClass() {
        CassandraModule modules = CassandraModule.aggregateModules(CassandraMailboxModule.MODULE, CassandraAclModule.MODULE);
        cassandra = CassandraCluster.create(modules, cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        testee = new CassandraMailboxDAO(cassandra.getConf(), cassandra.getTypesProvider());

        mailbox1 = new SimpleMailbox(MailboxPath.forUser("user", "abcd"),
            UID_VALIDITY_1,
            CASSANDRA_ID_1);
        mailbox2 = new SimpleMailbox(MailboxPath.forUser("user", "defg"),
            UID_VALIDITY_2,
            CASSANDRA_ID_2);
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }

    @Test
    public void retrieveMailboxShouldReturnEmptyWhenNone() {
        assertThat(testee.retrieveMailbox(CASSANDRA_ID_1).join())
            .isEmpty();
    }

    @Test
    public void saveShouldAddAMailbox() {
        testee.save(mailbox1).join();

        Optional<SimpleMailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .join();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox1);
    }

    @Test
    public void saveShouldOverride() {
        testee.save(mailbox1).join();

        mailbox2.setMailboxId(CASSANDRA_ID_1);
        testee.save(mailbox2).join();


        Optional<SimpleMailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1)
            .join();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox2);
    }

    @Test
    public void retrieveAllMailboxesShouldBeEmptyByDefault() {
        List<SimpleMailbox> mailboxes = testee.retrieveAllMailboxes()
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(mailboxes).isEmpty();
    }

    @Test
    public void retrieveAllMailboxesShouldReturnSingleMailbox() {
        testee.save(mailbox1).join();

        List<SimpleMailbox> mailboxes = testee.retrieveAllMailboxes()
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(mailboxes).containsOnly(mailbox1);
    }

    @Test
    public void retrieveAllMailboxesShouldReturnMultiMailboxes() {
        testee.save(mailbox1).join();
        testee.save(mailbox2).join();

        List<SimpleMailbox> mailboxes = testee.retrieveAllMailboxes()
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(mailboxes).containsOnly(mailbox1, mailbox2);
    }

    @Test
    public void deleteShouldNotFailWhenMailboxIsAbsent() {
        testee.delete(CASSANDRA_ID_1).join();
    }

    @Test
    public void deleteShouldRemoveExistingMailbox() {
        testee.save(mailbox1).join();

        testee.delete(CASSANDRA_ID_1).join();

        assertThat(testee.retrieveMailbox(CASSANDRA_ID_1).join())
            .isEmpty();
    }

    @Test
    public void updateShouldNotFailWhenMailboxIsAbsent() {
        testee.updatePath(CASSANDRA_ID_1, NEW_MAILBOX_PATH).join();
    }

    @Test
    public void updateShouldChangeMailboxPath() {
        testee.save(mailbox1).join();

        testee.updatePath(CASSANDRA_ID_1, NEW_MAILBOX_PATH).join();

        mailbox1.setNamespace(NEW_MAILBOX_PATH.getNamespace());
        mailbox1.setUser(NEW_MAILBOX_PATH.getUser());
        mailbox1.setName(NEW_MAILBOX_PATH.getName());
        Optional<SimpleMailbox> readMailbox = testee.retrieveMailbox(CASSANDRA_ID_1).join();
        assertThat(readMailbox.isPresent()).isTrue();
        assertThat(readMailbox.get()).isEqualToComparingFieldByField(mailbox1);
    }
}
