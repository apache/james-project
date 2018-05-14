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
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

import nl.jqno.equalsverifier.EqualsVerifier;

public abstract class CassandraMailboxPathDAOTest {
    private static final String USER = "user";
    private static final String OTHER_USER = "other";
    private static final CassandraId INBOX_ID = CassandraId.timeBased();
    private static final CassandraId OUTBOX_ID = CassandraId.timeBased();
    private static final CassandraId otherMailboxId = CassandraId.timeBased();

    public static final MailboxPath USER_INBOX_MAILBOXPATH = MailboxPath.forUser(USER, "INBOX");
    public static final CassandraIdAndPath INBOX_ID_AND_PATH = new CassandraIdAndPath(INBOX_ID, USER_INBOX_MAILBOXPATH);
    public static final MailboxPath USER_OUTBOX_MAILBOXPATH = MailboxPath.forUser(USER, "OUTBOX");
    public static final MailboxPath OTHER_USER_MAILBOXPATH = MailboxPath.forUser(OTHER_USER, "INBOX");

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    protected CassandraCluster cassandra;

    private CassandraMailboxPathDAO testee;
    abstract CassandraMailboxPathDAO testee();

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraMailboxModule(), cassandraServer.getIp(), cassandraServer.getBindingPort());
        testee = testee();

    }

    @After
    public void tearDown() throws Exception {
        cassandra.close();
    }

    @Test
    public void cassandraIdAndPathShouldRespectBeanContract() {
        EqualsVerifier.forClass(CassandraIdAndPath.class).verify();
    }

    @Test
    public void saveShouldInsertNewEntry() throws Exception {
        assertThat(testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join()).isTrue();

        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).join())
            .contains(INBOX_ID_AND_PATH);
    }

    @Test
    public void saveOnSecondShouldBeFalse() throws Exception {
        assertThat(testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join()).isTrue();
        assertThat(testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join()).isFalse();
    }

    @Test
    public void retrieveIdShouldReturnEmptyWhenEmptyData() throws Exception {
        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).join()
            .isPresent())
            .isFalse();
    }

    @Test
    public void retrieveIdShouldReturnStoredData() throws Exception {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join();

        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).join())
            .contains(INBOX_ID_AND_PATH);
    }

    @Test
    public void getUserMailboxesShouldReturnAllMailboxesOfUser() throws Exception {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join();
        testee.save(USER_OUTBOX_MAILBOXPATH, OUTBOX_ID).join();
        testee.save(OTHER_USER_MAILBOXPATH, otherMailboxId).join();

        List<CassandraIdAndPath> cassandraIds = testee
            .listUserMailboxes(USER_INBOX_MAILBOXPATH.getNamespace(), USER_INBOX_MAILBOXPATH.getUser())
            .join()
            .collect(Guavate.toImmutableList());

        assertThat(cassandraIds)
            .hasSize(2)
            .containsOnly(INBOX_ID_AND_PATH, new CassandraIdAndPath(OUTBOX_ID, USER_OUTBOX_MAILBOXPATH));
    }

    @Test
    public void deleteShouldNotThrowWhenEmpty() throws Exception {
        testee.delete(USER_INBOX_MAILBOXPATH).join();
    }

    @Test
    public void deleteShouldDeleteTheExistingMailboxId() throws Exception {
        testee.save(USER_INBOX_MAILBOXPATH, INBOX_ID).join();

        testee.delete(USER_INBOX_MAILBOXPATH).join();

        assertThat(testee.retrieveId(USER_INBOX_MAILBOXPATH).join())
            .isEmpty();
    }
}