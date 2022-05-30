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

package org.apache.james.adapter.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class DelegationStoreAuthorizatorTest {
    private static final Username OTHER_USER = Username.of("other_user");
    private static final Username GIVEN_USER = Username.of("given_user");

    private UsersRepository usersRepository;
    private DelegationStore delegationStore;
    private DelegationStoreAuthorizator testee;

    @BeforeEach
    public void setUp() throws Exception {
        usersRepository = mock(UsersRepository.class);
        delegationStore = mock(DelegationStore.class);
        testee = new DelegationStoreAuthorizator(delegationStore, usersRepository);
    }

    @Test
    void canLoginAsOtherUserShouldReturnAllowedWhenGivenUserIsAdmin() throws Exception {
        when(usersRepository.isAdministrator(GIVEN_USER))
            .thenReturn(true);
        when(usersRepository.contains(OTHER_USER))
            .thenReturn(true);
        when(delegationStore.authorizedUsers(OTHER_USER))
            .thenReturn(Flux.empty());

        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.ALLOWED);
    }

    @Test
    void canLoginAsOtherUserShouldReturnAllowedWhenGivenUserIsDelegatedByOtherUser() throws Exception {
        when(usersRepository.isAdministrator(GIVEN_USER))
            .thenReturn(false);
        when(usersRepository.contains(OTHER_USER))
            .thenReturn(true);
        when(delegationStore.authorizedUsers(OTHER_USER))
            .thenReturn(Flux.just(GIVEN_USER));

        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.ALLOWED);
    }

    @Test
    void canLoginAsOtherUserShouldReturnNotDelegatedWhenGivenUserIsNotAdminAndNotDelegated() throws Exception {
        when(usersRepository.isAdministrator(GIVEN_USER))
            .thenReturn(false);
        when(usersRepository.contains(OTHER_USER))
            .thenReturn(true);
        when(delegationStore.authorizedUsers(OTHER_USER))
            .thenReturn(Flux.empty());

        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.NOT_DELEGATED);
    }

    @Test
    void canLoginAsOtherUserShouldReturnUnknownUserWhenOtherUserDoesNotExist() throws Exception {
        when(usersRepository.isAdministrator(GIVEN_USER))
            .thenReturn(false);
        when(usersRepository.contains(OTHER_USER))
            .thenReturn(false);
        when(delegationStore.authorizedUsers(OTHER_USER))
            .thenReturn(Flux.empty());

        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.UNKNOWN_USER);
    }
}
