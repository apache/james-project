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

package org.apache.james.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DelegationStoreContract {

    Username ALICE = Username.of("alice");
    Username BOB = Username.of("bob");
    Username CEDRIC = Username.of("cedic");
    Username DAMIEN = Username.of("damien");
    Username EDGARD = Username.of("edgard");

    DelegationStore testee();

    default void addUser(Username username) {
    }

    @Test
    default void authorizedUsersShouldReturnEmptyByDefault() {
        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .isEmpty();
    }

    @Test
    default void authorizedUsersShouldReturnAddedUsers() {
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, CEDRIC)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, DAMIEN)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .containsOnly(BOB, CEDRIC, DAMIEN);
    }

    @Test
    default void authorizedUsersShouldReturnEmptyAfterClear() {
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, CEDRIC)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, DAMIEN)).block();

        Mono.from(testee().clear(ALICE)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .isEmpty();
    }

    @Test
    default void clearShouldBeIdempotent() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(EDGARD, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, CEDRIC)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, DAMIEN)).block();

        Mono.from(testee().clear(ALICE)).block();
        Mono.from(testee().clear(ALICE)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsExactly(EDGARD);
    }

    @Test
    default void authorizedUsersShouldNotReturnDeletedUsers() {
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, CEDRIC)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, DAMIEN)).block();

        Mono.from(testee().removeAuthorizedUser(ALICE, CEDRIC)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .containsOnly(BOB, DAMIEN);
    }

    @Test
    default void removeAuthorizedUserShouldBeIdempotent() {
        addUser(CEDRIC);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, CEDRIC)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, DAMIEN)).block();
        Mono.from(testee().addAuthorizedUser(EDGARD, CEDRIC)).block();

        Mono.from(testee().removeAuthorizedUser(ALICE, CEDRIC)).block();
        Mono.from(testee().removeAuthorizedUser(ALICE, CEDRIC)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .containsOnly(BOB, DAMIEN);

        assertThat(Flux.from(testee().delegatedUsers(CEDRIC)).collectList().block())
            .containsOnly(EDGARD);
    }

    @Test
    default void authorizedUsersShouldNotReturnDuplicates() {
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .containsExactly(BOB);
    }

    @Test
    default void authorizedUsersShouldNotReturnUnrelatedUsers() {
        Mono.from(testee().addAuthorizedUser(BOB, ALICE)).block();
        Mono.from(testee().addAuthorizedUser(BOB, CEDRIC)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .isEmpty();
    }

    @Test
    default void delegatedUsersShouldReturnEmptyByDefault() {
        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .isEmpty();
    }

    @Test
    default void delegatedUsersShouldReturnCorrectUsers() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(CEDRIC, BOB)).block();

        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsOnly(CEDRIC, ALICE);
    }

    @Test
    default void delegateesSourceAndDelegatorsSourceShouldBeAlignedWhenBothUsersDoNotExist() {
        // LDAP case where there are no user entries in user table

        // ALICE delegates BOB
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();

        // Delegatees source of ALICE should be aligned with Delegators source of BOB
        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .containsOnly(BOB);
        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsOnly(ALICE);
    }

    @Test
    default void removeDelegateeLDAPCaseShouldSucceed() {
        // LDAP case where there are no user entries in user table

        // ALICE delegates BOB
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();

        // ALICE remove BOB's access
        Mono.from(testee().removeAuthorizedUser(ALICE, BOB)).block();

        // Delegatees source of ALICE and Delegators source of BOB should both return empty
        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .isEmpty();
    }

    @Test
    default void removeDelegatorLDAPCaseShouldSucceed() {
        // LDAP case where there are no user entries in user table

        // ALICE delegates BOB
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();

        // BOB withdraws access to ALICE account
        Mono.from(testee().removeDelegatedUser(BOB, ALICE)).block();

        // Delegatees source of ALICE and Delegators source of BOB should both return empty
        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .isEmpty();
        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .isEmpty();
    }

    @Test
    default void delegatedUsersShouldReturnUpdateEntryAfterClearDelegatedBaseUser() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(CEDRIC, BOB)).block();

        Mono.from(testee().clear(ALICE)).block();
        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsOnly(CEDRIC);
    }

    @Test
    default void delegatedUsersShouldNotReturnDeletedUsers() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(CEDRIC, BOB)).block();
        Mono.from(testee().addAuthorizedUser(DAMIEN, BOB)).block();

        Mono.from(testee().removeAuthorizedUser(ALICE, BOB)).block();

        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsOnly(CEDRIC, DAMIEN);
    }

    @Test
    default void delegatedUsersShouldNotReturnDuplicates() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();

        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsExactly(ALICE);
    }

    @Test
    default void delegatedUsersShouldNotReturnUnrelatedUsers() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(BOB, ALICE)).block();
        Mono.from(testee().addAuthorizedUser(BOB, CEDRIC)).block();

        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .isEmpty();
    }

    @Test
    default void addAuthorizedUserShouldNotThrowWhenUserWithAccessDoesNotExist() {
        assertThatCode(() -> Mono.from(testee().addAuthorizedUser(BOB, ALICE)).block())
            .doesNotThrowAnyException();
    }


    @Test
    default void delegatedUserShouldNotReturnDeletedUsers() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(CEDRIC, BOB)).block();

        Mono.from(testee().removeDelegatedUser(BOB, CEDRIC)).block();

        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsOnly(ALICE);
    }

    @Test
    default void removeDelegatedUserShouldBeIdempotent() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(CEDRIC, BOB)).block();

        Mono.from(testee().removeDelegatedUser(BOB, CEDRIC)).block();
        Mono.from(testee().removeDelegatedUser(BOB, CEDRIC)).block();

        assertThat(Flux.from(testee().delegatedUsers(BOB)).collectList().block())
            .containsOnly(ALICE);
    }

    @Test
    default void removeDelegatedUserShouldUpdateAuthorizedUserRelated() {
        addUser(BOB);
        Mono.from(testee().addAuthorizedUser(ALICE, BOB)).block();
        Mono.from(testee().addAuthorizedUser(ALICE, CEDRIC)).block();

        Mono.from(testee().removeDelegatedUser(BOB, ALICE)).block();

        assertThat(Flux.from(testee().authorizedUsers(ALICE)).collectList().block())
            .containsOnly(CEDRIC);
    }

}
