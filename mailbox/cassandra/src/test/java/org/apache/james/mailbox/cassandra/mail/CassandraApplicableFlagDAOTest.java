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
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraApplicableFlagsModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

class CassandraApplicableFlagDAOTest {

    private static final String USER_FLAG = "User Flag";
    private static final String USER_FLAG2 = "User Flag 2";
    private static final CassandraId CASSANDRA_ID = CassandraId.timeBased();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraApplicableFlagsModule.MODULE);

    private CassandraApplicableFlagDAO testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        testee = new CassandraApplicableFlagDAO(cassandra.getConf());
    }

    @Test
    void updateApplicableFlagsShouldReturnEmptyByDefault() {
        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).hasElement().block())
            .isFalse();
    }

    @Test
    void updateApplicableFlagsShouldSupportEmptyUserFlags() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of()).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).hasElement().block())
            .isFalse();
    }

    @Test
    void updateApplicableFlagsShouldUpdateUserFlag() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).block())
            .isEqualTo(new Flags(USER_FLAG));
    }

    @Test
    void retrieveApplicableFlagsShouldReturnEmptyWhenDeleted() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).block();

        testee.delete(CASSANDRA_ID).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).blockOptional())
            .isEmpty();
    }

    @Test
    void deleteShouldNotThrowWhenEmpty() {
        assertThatCode(() -> testee.delete(CASSANDRA_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    void updateApplicableFlagsShouldUnionUserFlags() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).block();
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG2)).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).block())
            .isEqualTo(FlagsBuilder.builder().add(USER_FLAG, USER_FLAG2).build());
    }

    @Test
    void updateApplicableFlagsShouldBeIdempotent() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).block();
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).block())
            .isEqualTo(new Flags(USER_FLAG));
    }

    @Test
    void updateApplicableFlagsShouldSkipAlreadyStoredFlagsWhenAddingFlag() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG)).block();
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG, USER_FLAG2)).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).block())
            .isEqualTo(FlagsBuilder.builder().add(USER_FLAG, USER_FLAG2).build());
    }

    @Test
    void updateApplicableFlagsShouldUpdateMultiFlags() {
        testee.updateApplicableFlags(CASSANDRA_ID, ImmutableSet.of(USER_FLAG, USER_FLAG2)).block();

        assertThat(testee.retrieveApplicableFlag(CASSANDRA_ID).block())
            .isEqualTo(FlagsBuilder.builder().add(USER_FLAG, USER_FLAG2).build());
    }

}