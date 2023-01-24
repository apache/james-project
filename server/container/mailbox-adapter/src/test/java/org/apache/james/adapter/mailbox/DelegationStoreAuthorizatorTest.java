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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.memory.MemoryDelegationStore;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class DelegationStoreAuthorizatorTest {
    private static final Username OTHER_USER = Username.of("other_user");
    private static final Username GIVEN_USER = Username.of("given_user");
    private static final Username ADMIN_USER = Username.of("admin_user");
    private static final Username NOT_GIVEN_USER = Username.of("not_given_user");

    private MemoryUsersRepository usersRepository;
    private DelegationStore delegationStore;
    private DelegationStoreAuthorizator testee;

    @BeforeEach
    public void setUp() throws Exception {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(null);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("administratorId", ADMIN_USER.asString());
        usersRepository.configure(configuration);
        delegationStore = new MemoryDelegationStore();
        testee = new DelegationStoreAuthorizator(delegationStore, usersRepository);
    }

    @Test
    void canLoginAsOtherUserShouldReturnAllowedWhenGivenUserIsAdmin() throws Exception {
        usersRepository.addUser(OTHER_USER, "secret");

        assertThat(testee.canLoginAsOtherUser(ADMIN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.ALLOWED);
    }

    @Test
    void canLoginAsOtherUserShouldReturnForbiddenWhenWrongVirtualHosting() throws Exception {
        usersRepository.addUser(OTHER_USER, "secret");
        assertThat(testee.canLoginAsOtherUser(Username.of("other_user@domain.tld"), OTHER_USER))
            .isEqualTo(Authorizator.AuthorizationState.FORBIDDEN);
    }

    @Test
    void canLoginAsOtherUserShouldReturnAllowedWhenGivenUserIsDelegatedByOtherUser() throws Exception {
        usersRepository.addUser(OTHER_USER, "secret");
        Mono.from(delegationStore.addAuthorizedUser(OTHER_USER, GIVEN_USER)).block();

        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.ALLOWED);
    }

    @Test
    void canLoginAsOtherUserShouldReturnAllowedWhenGivenUserIsAdminWithWrongVirtualHosting() throws Exception {
        Username accessor = Username.of("other_user@domain.tld");
        usersRepository.addUser(OTHER_USER, "secret");
        Mono.from(delegationStore.addAuthorizedUser(OTHER_USER, accessor)).block();

        assertThat(testee.canLoginAsOtherUser(accessor, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.ALLOWED);
    }

    @Test
    void canLoginAsOtherUserShouldReturnForbiddenWhenGivenUserIsNotAdminAndNotDelegated() throws Exception {
        usersRepository.addUser(OTHER_USER, "secret");
        Mono.from(delegationStore.addAuthorizedUser(OTHER_USER, NOT_GIVEN_USER)).block();

        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.FORBIDDEN);
    }

    @Test
    void canLoginAsOtherUserShouldReturnUnknownUserWhenOtherUserDoesNotExist() throws Exception {
        assertThat(testee.canLoginAsOtherUser(GIVEN_USER, OTHER_USER)).isEqualTo(Authorizator.AuthorizationState.UNKNOWN_USER);
    }
}
