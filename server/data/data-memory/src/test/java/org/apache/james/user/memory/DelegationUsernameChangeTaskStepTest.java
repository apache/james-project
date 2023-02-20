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

package org.apache.james.user.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationUsernameChangeTaskStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DelegationUsernameChangeTaskStepTest {
    private static final Username ALICE_OLD = Username.of("alice-old@domain.tld");
    private static final Username ALICE_NEW = Username.of("alice-new@domain.tld");
    private static final Username BOB = Username.of("bob@domain.tld");

    private MemoryDelegationStore delegationStore;
    private DelegationUsernameChangeTaskStep testee;

    @BeforeEach
    void setUp() {
        delegationStore = new MemoryDelegationStore();
        testee = new DelegationUsernameChangeTaskStep(delegationStore);
    }

    @Test
    void shouldMigrateDelegationToNewUser() {
        Mono.from(delegationStore.addAuthorizedUser(BOB)
            .forUser(ALICE_OLD))
            .block();

        Mono.from(testee.changeUsername(ALICE_OLD, ALICE_NEW)).block();

        assertThat(Flux.from(delegationStore.authorizedUsers(ALICE_NEW)).collectList().block())
            .containsOnly(BOB);
    }

    @Test
    void shouldRemoveDelegationForOldUser() {
        Mono.from(delegationStore.addAuthorizedUser(BOB)
            .forUser(ALICE_OLD))
            .block();

        Mono.from(testee.changeUsername(ALICE_OLD, ALICE_NEW)).block();

        assertThat(Flux.from(delegationStore.authorizedUsers(ALICE_OLD)).collectList().block())
            .isEmpty();
    }

    @Test
    void shouldMigrateChangesForDelegators() {
        Mono.from(delegationStore.addAuthorizedUser(ALICE_OLD)
            .forUser(BOB))
            .block();

        Mono.from(testee.changeUsername(ALICE_OLD, ALICE_NEW)).block();

        assertThat(Flux.from(delegationStore.authorizedUsers(BOB)).collectList().block())
            .containsOnly(ALICE_NEW);
    }
}
