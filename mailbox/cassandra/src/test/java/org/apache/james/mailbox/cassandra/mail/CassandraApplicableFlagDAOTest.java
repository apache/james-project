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

import java.util.Optional;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CassandraApplicableFlagDAOTest {

    public static final String USER_FLAG = "User Flag";
    public static final String USER_FLAG2 = "User Flag 2";
    public static final CassandraId CASSANDRA_ID = CassandraId.timeBased();

    private CassandraCluster cassandra;

    private CassandraApplicableFlagDAO testee;

    @Before
    public void setUp() throws Exception {
        cassandra = CassandraCluster.create(new CassandraApplicableFlagsModule());
        cassandra.ensureAllTables();

        testee = new CassandraApplicableFlagDAO(cassandra.getConf());
    }

    @After
    public void tearDown() throws Exception {
        cassandra.clearAllTables();
    }

    @Test
    public void updateApplicableFlagsShouldReturnEmptyByDefault() throws Exception {
        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateApplicableFlagsShouldSupportEmptyFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, new Flags()).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateApplicableFlagsShouldIgnoreRecentFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, new Flags(Flag.RECENT)).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isFalse();
    }

    @Test
    public void updateApplicableFlagsShouldUpdateMultiFlags() throws Exception {
        Flags flags = new FlagsBuilder().add(Flag.ANSWERED, Flag.DELETED).build();
        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldAddAnsweredFlag() throws Exception {
        Flags flags = new Flags(Flag.ANSWERED);
        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldAddDeletedFlag() throws Exception {
        Flags flags = new Flags(Flag.DELETED);
        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldAddDraftFlag() throws Exception {
        Flags flags = new Flags(Flag.DRAFT);
        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldAddFlaggedFlag() throws Exception {
        Flags flags = new Flags(Flag.FLAGGED);
        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldAddSeenFlag() throws Exception {
        Flags flags = new Flags(Flag.SEEN);
        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldUnionSystemFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, new Flags(Flag.ANSWERED)).join();
        testee.updateApplicableFlags(CASSANDRA_ID, new Flags(Flag.SEEN)).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(new FlagsBuilder().add(Flag.ANSWERED, Flag.SEEN).build());
    }

    @Test
    public void updateApplicableFlagsShouldUpdateUserFlag() throws Exception {
        Flags flags = new FlagsBuilder().add(Flag.ANSWERED).add(USER_FLAG).build();

        testee.updateApplicableFlags(CASSANDRA_ID, flags).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(flags);
    }

    @Test
    public void updateApplicableFlagsShouldUnionUserFlags() throws Exception {
        testee.updateApplicableFlags(CASSANDRA_ID, new Flags(USER_FLAG)).join();

        testee.updateApplicableFlags(CASSANDRA_ID, new Flags(USER_FLAG2)).join();

        Optional<Flags> actual = testee.retrieveApplicableFlag(CASSANDRA_ID).join();
        assertThat(actual.isPresent()).isTrue();
        assertThat(actual.get()).isEqualTo(new FlagsBuilder().add(USER_FLAG, USER_FLAG2).build());
    }

}