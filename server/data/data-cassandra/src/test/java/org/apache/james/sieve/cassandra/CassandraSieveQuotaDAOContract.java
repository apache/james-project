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

package org.apache.james.sieve.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.junit.jupiter.api.Test;

interface CassandraSieveQuotaDAOContract {
    Username USERNAME = Username.of("user");
    QuotaSizeLimit QUOTA_SIZE = QuotaSizeLimit.size(15L);


    CassandraSieveQuotaDAO testee();

    @Test
    default void getQuotaShouldReturnEmptyByDefault() {
        assertThat(testee().getQuota().block())
            .isEmpty();
    }

    @Test
    default void getQuotaUserShouldReturnEmptyByDefault() {
        assertThat(testee().getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    default void getQuotaShouldReturnStoredValue() {
        testee().setQuota(QUOTA_SIZE).block();

        assertThat(testee().getQuota().block())
            .contains(QUOTA_SIZE);
    }

    @Test
    default void getQuotaUserShouldReturnStoredValue() {
        testee().setQuota(USERNAME, QUOTA_SIZE).block();

        assertThat(testee().getQuota(USERNAME).block())
            .contains(QUOTA_SIZE);
    }

    @Test
    default void removeQuotaShouldDeleteQuota() {
        testee().setQuota(QUOTA_SIZE).block();

        testee().removeQuota().block();

        assertThat(testee().getQuota().block())
            .isEmpty();
    }

    @Test
    default void removeQuotaUserShouldDeleteQuotaUser() {
        testee().setQuota(USERNAME, QUOTA_SIZE).block();

        testee().removeQuota(USERNAME).block();

        assertThat(testee().getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    default void removeQuotaShouldWorkWhenNoneStore() {
        testee().removeQuota().block();

        assertThat(testee().getQuota().block())
            .isEmpty();
    }

    @Test
    default void removeQuotaUserShouldWorkWhenNoneStore() {
        testee().removeQuota(USERNAME).block();

        assertThat(testee().getQuota(USERNAME).block())
            .isEmpty();
    }

    @Test
    default void spaceUsedByShouldReturnZeroByDefault() {
        assertThat(testee().spaceUsedBy(USERNAME).block()).isEqualTo(0);
    }

    @Test
    default void spaceUsedByShouldReturnStoredValue() {
        long spaceUsed = 18L;

        testee().updateSpaceUsed(USERNAME, spaceUsed).block();

        assertThat(testee().spaceUsedBy(USERNAME).block()).isEqualTo(spaceUsed);
    }

    @Test
    default void updateSpaceUsedShouldBeAdditive() {
        long spaceUsed = 18L;

        testee().updateSpaceUsed(USERNAME, spaceUsed).block();
        testee().updateSpaceUsed(USERNAME, spaceUsed).block();

        assertThat(testee().spaceUsedBy(USERNAME).block()).isEqualTo(2 * spaceUsed);
    }

    @Test
    default void updateSpaceUsedShouldWorkWithNegativeValues() {
        long spaceUsed = 18L;

        testee().updateSpaceUsed(USERNAME, spaceUsed).block();
        testee().updateSpaceUsed(USERNAME, -1 * spaceUsed).block();

        assertThat(testee().spaceUsedBy(USERNAME).block()).isEqualTo(0L);
    }

    @Test
    default void resetSpaceUsedShouldResetSpaceWhenNewSpaceIsGreaterThanCurrentSpace() {
        testee().updateSpaceUsed(USERNAME, 10L).block();
        testee().resetSpaceUsed(USERNAME, 15L).block();

        assertThat(testee().spaceUsedBy(USERNAME).block()).isEqualTo(15L);
    }

    @Test
    default void resetSpaceUsedShouldResetSpaceWhenNewSpaceIsSmallerThanCurrentSpace() {
        testee().updateSpaceUsed(USERNAME, 10L).block();
        testee().resetSpaceUsed(USERNAME, 9L).block();

        assertThat(testee().spaceUsedBy(USERNAME).block()).isEqualTo(9L);
    }
}
