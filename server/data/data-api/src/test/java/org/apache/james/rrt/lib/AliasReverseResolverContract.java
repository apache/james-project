/** *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;

public interface AliasReverseResolverContract {

    Domain DOMAIN = Domain.of("example.com");
    Domain OTHER_DOMAIN = Domain.of("other.org");
    Username USER = Username.of("user@example.com");
    Username USER_ALIAS = Username.of("alias@example.com");
    Username OTHER_USER = Username.of("other@example.com");

    AliasReverseResolver aliasReverseResolver();

    void addAliasMapping(Username alias, Username user) throws Exception;

    void addDomainAlias(Domain alias, Domain domain) throws Exception;

    void addGroupMapping(String group, Username user) throws Exception;

    @FunctionalInterface
    interface RequireUserName {
        void to(Username user) throws Exception;
    }

    @FunctionalInterface
    interface RequireDomain {
        void to(Domain domain) throws Exception;
    }

    default RequireUserName redirectUser(Username alias) {
        return user -> addAliasMapping(alias, user);
    }

    default RequireDomain redirectDomain(Domain alias) {
        return domain -> addDomainAlias(alias, domain);
    }

    @Test
    default void listAddressesShouldContainOnlyUserAddressWhenUserHasNoAlias() throws Exception {
        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .containsExactly(USER.asMailAddress());
    }

    @Test
    default void listAddressesShouldContainOnlyUserAddressWhenUserHasNoAliasAndAnotherUserHasOne() throws Exception {
        redirectUser(USER_ALIAS).to(OTHER_USER);
        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .containsExactly(USER.asMailAddress());
    }

    @Test
    default void listAddressesShouldContainUserAddressAndAnAliasOfTheUser() throws Exception {
        redirectUser(USER_ALIAS).to(USER);

        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .containsExactlyInAnyOrder(USER.asMailAddress(), USER_ALIAS.asMailAddress());
    }

    @Test
    default void listAddressesShouldBeTrueWhenSenderIsAnAliasOfAnAliasOfTheUser() throws Exception {
        Username userAliasBis = Username.of("aliasbis@" + DOMAIN.asString());
        redirectUser(userAliasBis).to(USER_ALIAS);
        redirectUser(USER_ALIAS).to(USER);

        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .containsExactlyInAnyOrder(USER.asMailAddress(), USER_ALIAS.asMailAddress(), userAliasBis.asMailAddress());
    }

    @Test
    default void listAddressesShouldContainUserAddressAndAnAliasOfTheDomainUser() throws Exception {
        Username fromUser = USER.withOtherDomain(OTHER_DOMAIN);

        redirectDomain(OTHER_DOMAIN).to(DOMAIN);

        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .containsExactlyInAnyOrder(USER.asMailAddress(), fromUser.asMailAddress());
    }

    @Test
    default void listAddressesShouldContainUserAddressAndAnAliasOfTheDomainUserFromAnotherDomain() throws Exception {
        Username userAliasOtherDomain = USER_ALIAS.withOtherDomain(OTHER_DOMAIN);

        redirectDomain(OTHER_DOMAIN).to(DOMAIN);
        redirectUser(userAliasOtherDomain).to(USER);

        Username userAliasMainDomain = USER_ALIAS.withOtherDomain(DOMAIN);
        Username userOtherDomain = USER.withOtherDomain(OTHER_DOMAIN);
        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .containsExactlyInAnyOrder(USER.asMailAddress(), userAliasOtherDomain.asMailAddress(), userAliasMainDomain.asMailAddress(), userOtherDomain.asMailAddress());
    }

    @Test
    default void listAddressesShouldNotContainAliasesRequiringMoreThanTenRecursionSteps() throws Exception {
        int recursionLevel = 10;
        IntStream.range(0, recursionLevel)
            .forEach(Throwing.intConsumer(aliasNumber -> {
                Username userAliasFrom = Username.of("alias" + aliasNumber + "@" + DOMAIN.asString());
                Username userAliasTo;
                if (aliasNumber == 0) {
                    userAliasTo = USER_ALIAS;
                } else {
                    userAliasTo = Username.of("alias" + (aliasNumber - 1) + "@" + DOMAIN.asString());
                }
                redirectUser(userAliasFrom).to(userAliasTo);
            }).sneakyThrow());

        Username userAliasExcluded = Username.of("alias" + (recursionLevel - 1) + "@" + DOMAIN.asString());
        redirectUser(USER_ALIAS).to(USER);

        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .doesNotContain(userAliasExcluded.asMailAddress());
    }

    @Test
    default void listAddressesShouldContainASenderAliasOfAnAliasInAnotherDomainOfTheUser() throws Exception {
        Username userAlias = Username.of("aliasbis@" + OTHER_DOMAIN.asString());
        Username userAliasBis = Username.of("aliaster@" + OTHER_DOMAIN.asString());
        redirectUser(userAliasBis).to(userAlias);
        redirectUser(userAlias).to(USER);

        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .contains(userAliasBis.asMailAddress());
    }

    @Test
    default void listAddressesShouldContainAUserAliasFollowingADomainAliasResolution() throws Exception {
        Username userAliasBis = Username.of("aliasbis@" + OTHER_DOMAIN.asString());
        redirectUser(userAliasBis).to(USER_ALIAS);
        redirectUser(USER_ALIAS).to(USER);

        assertThat(Flux.from(aliasReverseResolver().listAddresses(USER)).toStream())
            .contains(userAliasBis.asMailAddress());
    }
}
