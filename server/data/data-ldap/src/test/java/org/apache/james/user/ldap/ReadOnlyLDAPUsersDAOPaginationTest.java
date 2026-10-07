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

package org.apache.james.user.ldap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.user.api.model.UsernamePredicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.LDAPConnectionPool;

import reactor.core.publisher.Flux;

class ReadOnlyLDAPUsersDAOPaginationTest {
    static final String BASE_DN = "dc=james,dc=org";
    static final String PEOPLE_DN = "ou=people," + BASE_DN;
    // More users than the server size limit, so that listing them can not rely on a single search
    static final List<Username> JAMES_USERS = IntStream.range(0, 560)
        .mapToObj(i -> Username.of(String.format("user%03d@james.org", i)))
        .collect(ImmutableList.toImmutableList());
    static final List<Username> OTHER_USERS = IntStream.range(0, 10)
        .mapToObj(i -> Username.of(String.format("other%03d@other.org", i)))
        .collect(ImmutableList.toImmutableList());
    static final List<Username> ALL_USERS = Stream.concat(OTHER_USERS.stream(), JAMES_USERS.stream())
        .collect(ImmutableList.toImmutableList());
    // Matches the OpenLDAP default size limit
    static final int SERVER_SIZE_LIMIT = 500;

    InMemoryDirectoryServer ldapServer;
    LDAPConnectionPool connectionPool;
    ReadOnlyLDAPUsersDAO testee;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryDirectoryServerConfig serverConfig = new InMemoryDirectoryServerConfig(BASE_DN);
        serverConfig.setMaxSizeLimit(SERVER_SIZE_LIMIT);
        ldapServer = new InMemoryDirectoryServer(serverConfig);
        ldapServer.startListening();
        ldapServer.add("dn: " + BASE_DN, "objectClass: domain", "dc: james");
        ldapServer.add("dn: " + PEOPLE_DN, "objectClass: organizationalUnit", "ou: people");
        for (Username username : ALL_USERS) {
            String localPart = username.getLocalPart();
            ldapServer.add("dn: uid=" + localPart + "-" + username.getDomainPart().get().asString() + "," + PEOPLE_DN,
                "objectClass: inetOrgPerson",
                "uid: " + localPart,
                "cn: " + localPart,
                "sn: " + localPart,
                "mail: " + username.asString());
        }

        LdapRepositoryConfiguration configuration = LdapRepositoryConfiguration.builder()
            .ldapHosts(ImmutableList.of(URI.create("ldap://127.0.0.1:" + ldapServer.getListenPort())))
            .principal("")
            .credentials("")
            .userBase(PEOPLE_DN)
            .userIdAttribute("mail")
            .userObjectClass("inetOrgPerson")
            .build();

        connectionPool = ldapServer.getConnectionPool(2);
        testee = new ReadOnlyLDAPUsersDAO(new NoopGaugeRegistry(), connectionPool, configuration);
        testee.init();
    }

    @AfterEach
    void tearDown() {
        connectionPool.close();
        ldapServer.shutDown(true);
    }

    List<Username> search(List<UsernamePredicate> predicates, Optional<Username> anchor, Optional<Integer> limit) {
        return Flux.from(testee.searchPaginated(predicates, anchor, limit))
            .collectList()
            .block();
    }

    @Test
    void shouldReturnFirstUsersSortedAlphabetically() {
        assertThat(search(ImmutableList.of(), Optional.empty(), Optional.of(5)))
            .containsExactlyElementsOf(ALL_USERS.subList(0, 5));
    }

    @Test
    void shouldReturnAllUsersSortedWhenNoLimit() {
        assertThat(search(ImmutableList.of(), Optional.empty(), Optional.empty()))
            .containsExactlyElementsOf(ALL_USERS);
    }

    @Test
    void shouldReturnUsersStrictlyAfterTheAnchor() {
        assertThat(search(ImmutableList.of(), Optional.of(ALL_USERS.get(10)), Optional.of(3)))
            .containsExactlyElementsOf(ALL_USERS.subList(11, 14));
    }

    @Test
    void shouldSupportAnAnchorNotInTheDirectory() {
        // "user0505@" sorts before "user050@" as '5' < '@'
        assertThat(search(ImmutableList.of(), Optional.of(Username.of("user0505@james.org")), Optional.of(2)))
            .containsExactly(Username.of("user050@james.org"), Username.of("user051@james.org"));
    }

    @Test
    void shouldReturnLessUsersThanTheLimitOnTheLastPage() {
        assertThat(search(ImmutableList.of(), Optional.of(ALL_USERS.get(ALL_USERS.size() - 3)), Optional.of(10)))
            .containsExactlyElementsOf(ALL_USERS.subList(ALL_USERS.size() - 2, ALL_USERS.size()));
    }

    @Test
    void shouldReturnNoUsersAfterTheLastOne() {
        assertThat(search(ImmutableList.of(), Optional.of(ALL_USERS.getLast()), Optional.of(10)))
            .isEmpty();
    }

    @Test
    void browsingAllPagesShouldReturnAllUsersOnce() {
        List<Username> browsed = new ArrayList<>();
        Optional<Username> anchor = Optional.empty();
        while (true) {
            List<Username> page = search(ImmutableList.of(), anchor, Optional.of(50));
            browsed.addAll(page);
            if (page.size() < 50) {
                break;
            }
            anchor = Optional.of(page.getLast());
        }

        assertThat(browsed).containsExactlyElementsOf(ALL_USERS);
    }

    @Test
    void shouldFilterByPrefix() {
        assertThat(search(ImmutableList.of(UsernamePredicate.prefix("user11")), Optional.empty(), Optional.of(50)))
            .containsExactlyElementsOf(JAMES_USERS.subList(110, 120));
    }

    @Test
    void prefixShouldBeCaseInsensitive() {
        assertThat(search(ImmutableList.of(UsernamePredicate.prefix("USER11")), Optional.empty(), Optional.of(50)))
            .containsExactlyElementsOf(JAMES_USERS.subList(110, 120));
    }

    @Test
    void shouldFilterByDomain() {
        assertThat(search(ImmutableList.of(UsernamePredicate.domain(Domain.of("other.org"))), Optional.empty(), Optional.of(50)))
            .containsExactlyElementsOf(OTHER_USERS);
    }

    @Test
    void shouldCombinePredicatesAnchorAndLimit() {
        assertThat(search(ImmutableList.of(UsernamePredicate.domain(Domain.of("james.org")), UsernamePredicate.prefix("user1")),
                Optional.of(Username.of("user104@james.org")), Optional.of(3)))
            .containsExactly(Username.of("user105@james.org"), Username.of("user106@james.org"), Username.of("user107@james.org"));
    }

    @Test
    void shouldReturnNothingWhenNoMatch() {
        assertThat(search(ImmutableList.of(UsernamePredicate.prefix("nobody")), Optional.empty(), Optional.of(10)))
            .isEmpty();
    }
}
