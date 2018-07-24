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

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class CassandraApplicableFlagDAOTest {

    public static final String USER_FLAG = "User Flag";
    public static final String USER_FLAG2 = "User Flag 2";
    public static final CassandraId CASSANDRA_ID = CassandraId.timeBased();

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private static CassandraCluster cassandra;

    private CassandraApplicableFlagDAO testee;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(new CassandraApplicableFlagsModule(), cassandraServer.getHost());
    }

    @Before
    public void setUp() throws Exception {
        testee = new CassandraApplicableFlagDAO(cassandra.getConf());
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
    public void updateApplicableFlagsShouldReturnEmptyByDefault() throws Exception {
        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .isEmpty();
    }

    @Test
    public void updateApplicableFlagsShouldSupportEmptyUserFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of()).join();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .isEmpty();
    }

    @Test
    public void updateApplicableFlagsShouldUpdateUserFlag() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).join();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .contains(new Flags(USER_FLAG));
    }

    @Test
    public void updateApplicableFlagsShouldUnionUserFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).join();
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG2)).join();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .contains(FlagsBuilder.builder().add(USER_FLAG, USER_FLAG2).build());
    }

    @Test
    public void updateApplicableFlagsShouldBeIdempotent() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).join();
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).join();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .contains(new Flags(USER_FLAG));
    }

    @Test
    public void updateApplicableFlagsShouldSkipAlreadyStoredFlagsWhenAddingFlag() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).join();
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG, USER_FLAG2)).join();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .contains(FlagsBuilder.builder().add(USER_FLAG, USER_FLAG2).build());
    }

    @Test
    public void updateApplicableFlagsShouldUpdateMultiFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG, USER_FLAG2)).join();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).join())
            .contains(FlagsBuilder.builder().add(USER_FLAG, USER_FLAG2).build());
    }

}