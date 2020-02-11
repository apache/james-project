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
package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.CanSendFrom;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public interface CanSendFromContract {

    Domain DOMAIN = Domain.of("example.com");
    Domain OTHER_DOMAIN = Domain.of("other.org");
    Username USER = Username.of("user@example.com");
    Username USER_ALIAS = Username.of("alias@example.com");
    Username OTHER_USER = Username.of("other@example.com");

    CanSendFrom canSendFrom();

    void addAliasMapping(Username alias, Username user) throws Exception;

    void addDomainMapping(Domain alias, Domain domain) throws Exception;

    void addGroupMapping(String group, Username user) throws Exception;

    @Test
    default void userCanSendFromShouldBeFalseWhenSenderIsNotTheUser() {
        assertThat(canSendFrom().userCanSendFrom(USER, OTHER_USER)).isFalse();
    }

    @Test
    default void userCanSendFromShouldBeTrueWhenSenderIsTheUser() {
        assertThat(canSendFrom().userCanSendFrom(USER, USER)).isTrue();
    }

    @Test
    default void userCanSendFromShouldBeFalseWhenSenderIsAnAliasOfAnotherUser() throws Exception {
        addAliasMapping(USER_ALIAS, OTHER_USER);

        assertThat(canSendFrom().userCanSendFrom(USER, USER_ALIAS)).isFalse();
    }

    @Test
    default void userCanSendFromShouldBeTrueWhenSenderIsAnAliasOfTheUser() throws Exception {
        addAliasMapping(USER_ALIAS, USER);

        assertThat(canSendFrom().userCanSendFrom(USER, USER_ALIAS)).isTrue();
    }

    @Test
    default void userCanSendFromShouldBeTrueWhenSenderIsAnAliasOfAnAliasOfTheUser() throws Exception {
        Username userAliasBis = Username.of("aliasbis@" + DOMAIN.asString());
        addAliasMapping(userAliasBis, USER_ALIAS);
        addAliasMapping(USER_ALIAS, USER);

        assertThat(canSendFrom().userCanSendFrom(USER, userAliasBis)).isTrue();
    }

    @Test
    default void userCanSendFromShouldBeTrueWhenSenderIsAnAliasOfTheDomainUser() throws Exception {
        Username fromUser = Username.of(USER.getLocalPart() + "@" + OTHER_DOMAIN.asString());

        addDomainMapping(OTHER_DOMAIN, DOMAIN);

        assertThat(canSendFrom().userCanSendFrom(USER, fromUser)).isTrue();
    }

    @Test
    default void userCanSendFromShouldBeFalseWhenWhenSenderIsAnAliasOfTheUserFromAGroupAlias() throws Exception {
        Username fromGroup = Username.of("group@example.com");

        addGroupMapping("group@example.com", USER);

        assertThat(canSendFrom().userCanSendFrom(USER, fromGroup)).isFalse();

    }

    @Test
    default void allValidFromAddressesShouldContainOnlyUserAddressWhenUserHasNoAlias() throws Exception {
        assertThat(canSendFrom().allValidFromAddressesForUser(USER))
            .containsExactly(USER.asMailAddress());
    }

    @Test
    default void allValidFromAddressesShouldContainOnlyUserAddressWhenUserHasNoAliasAndAnotherUserHasOne() throws Exception {
        addAliasMapping(USER_ALIAS, OTHER_USER);
        assertThat(canSendFrom().allValidFromAddressesForUser(USER))
            .containsExactly(USER.asMailAddress());
    }

    @Test
    default void allValidFromAddressesShouldContainUserAddressAndAnAliasOfTheUser() throws Exception {
        addAliasMapping(USER_ALIAS, USER);

        assertThat(canSendFrom().allValidFromAddressesForUser(USER))
            .containsExactlyInAnyOrder(USER.asMailAddress(), USER_ALIAS.asMailAddress());
    }

    @Test
    @Disabled("Recursive aliases are not supported yet")
    default void allValidFromAddressesFromShouldBeTrueWhenSenderIsAnAliasOfAnAliasOfTheUser() throws Exception {
        Username userAliasBis = Username.of("aliasbis@" + DOMAIN.asString());
        addAliasMapping(userAliasBis, USER_ALIAS);
        addAliasMapping(USER_ALIAS, USER);

        assertThat(canSendFrom().allValidFromAddressesForUser(USER))
            .containsExactlyInAnyOrder(USER.asMailAddress(), USER_ALIAS.asMailAddress(), userAliasBis.asMailAddress());
    }

    @Test
    default void allValidFromAddressesShouldContainUserAddressAndAnAliasOfTheDomainUser() throws Exception {
        Username fromUser = Username.of(USER.getLocalPart() + "@" + OTHER_DOMAIN.asString());

        addDomainMapping(OTHER_DOMAIN, DOMAIN);

        assertThat(canSendFrom().allValidFromAddressesForUser(USER))
            .containsExactlyInAnyOrder(USER.asMailAddress(), fromUser.asMailAddress());
    }

    @Test
    default void allValidFromAddressesShouldContainUserAddressAndAnAliasOfTheDomainUserFromAnotherDomain() throws Exception {
        Username userAliasOtherDomain = Username.of(USER_ALIAS.getLocalPart() + "@" + OTHER_DOMAIN.asString());

        addDomainMapping(OTHER_DOMAIN, DOMAIN);
        addAliasMapping(userAliasOtherDomain, USER);

        Username userAliasMainDomain = Username.of(USER_ALIAS.getLocalPart() + "@" + DOMAIN.asString());
        Username userOtherDomain = Username.of(USER.getLocalPart() + "@" + OTHER_DOMAIN.asString());
        assertThat(canSendFrom().allValidFromAddressesForUser(USER))
            .containsExactlyInAnyOrder(USER.asMailAddress(), userAliasOtherDomain.asMailAddress(), userAliasMainDomain.asMailAddress(), userOtherDomain.asMailAddress());
    }
}
