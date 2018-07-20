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

import java.util.stream.IntStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxRecentsModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;

public class CassandraMailboxRecentDAOTest {
    public static final MessageUid UID1 = MessageUid.of(36L);
    public static final MessageUid UID2 = MessageUid.of(37L);
    public static final CassandraId CASSANDRA_ID = CassandraId.timeBased();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();

    private static CassandraCluster cassandra;

    private CassandraMailboxRecentsDAO testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(new CassandraMailboxRecentsModule(), cassandraServer.getHost());
    }

    @Before
    public void setUp() {
        testee = new CassandraMailboxRecentsDAO(cassandra.getConf());
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
    public void getRecentMessageUidsInMailboxShouldBeEmptyByDefault() throws Exception {
        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).isEmpty();
    }

    @Test
    public void addToRecentShouldAddUidWhenEmpty() throws Exception {
        testee.addToRecent(CASSANDRA_ID, UID1).join();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).containsOnly(UID1);
    }

    @Test
    public void removeFromRecentShouldRemoveUidWhenOnlyOneUid() throws Exception {
        testee.addToRecent(CASSANDRA_ID, UID1).join();

        testee.removeFromRecent(CASSANDRA_ID, UID1).join();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).isEmpty();
    }

    @Test
    public void removeFromRecentShouldNotFailIfNotExisting() throws Exception {
        testee.removeFromRecent(CASSANDRA_ID, UID1).join();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).isEmpty();
    }

    @Test
    public void addToRecentShouldAddUidWhenNotEmpty() throws Exception {
        testee.addToRecent(CASSANDRA_ID, UID1).join();

        testee.addToRecent(CASSANDRA_ID, UID2).join();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).containsOnly(UID1, UID2);
    }

    @Test
    public void removeFromRecentShouldOnlyRemoveUidWhenNotEmpty() throws Exception {
        testee.addToRecent(CASSANDRA_ID, UID1).join();
        testee.addToRecent(CASSANDRA_ID, UID2).join();

        testee.removeFromRecent(CASSANDRA_ID, UID2).join();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).containsOnly(UID1);
    }

    @Test
    public void addToRecentShouldBeIdempotent() throws Exception {
        testee.addToRecent(CASSANDRA_ID, UID1).join();
        testee.addToRecent(CASSANDRA_ID, UID1).join();

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).containsOnly(UID1);
    }

    @Test
    public void getRecentMessageUidsInMailboxShouldNotTimeoutWhenOverPagingLimit() throws Exception {
        int pageSize = 5000;
        int size = pageSize + 1000;
        IntStream.range(0, size)
            .parallel()
            .forEach(i -> testee.addToRecent(CASSANDRA_ID, MessageUid.of(i + 1)).join());

        assertThat(testee.getRecentMessageUidsInMailbox(CASSANDRA_ID).join()
            .collect(Guavate.toImmutableList())).hasSize(size);
    }
}
