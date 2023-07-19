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

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.apache.james.user.ldap.DockerLdapSingleton.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.unboundid.ldap.sdk.LDAPException;

class ReadOnlyUsersLDAPRepositoryWithLDAPFailoverTest {
    private final static LdapGenericContainer masterLdap = DockerLdapSingleton.ldapContainer;
    private final static LdapGenericContainer slaveLdap = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .build();

    private ReadOnlyUsersLDAPRepository usersLDAPRepository;

    @BeforeAll
    static void setUpAll() {
        masterLdap.start();
        slaveLdap.start();
    }

    @BeforeEach
    void setUp() throws Exception {
        masterLdap.unpause();
        slaveLdap.unpause();

        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfigurationWithVirtualHosting(List.of(masterLdap, slaveLdap));

        usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);
        usersLDAPRepository.init();
    }

    @AfterAll
    static void afterAll() {
        masterLdap.stop();
        slaveLdap.stop();
    }

    @Test
    void getUsersCountShouldSucceedByDefault() throws Exception {
        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void getUsersCountShouldSucceedWhenOnlySlaveLdapIsDown() throws Exception {
        slaveLdap.pause();

        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void getUsersCountShouldSucceedWhenOnlyMasterLdapIsDown() throws Exception {
        masterLdap.pause();

        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void getUsersCountShouldFailWhenBothMasterLdapAndSlaveLdapAreDown() {
        masterLdap.pause();
        slaveLdap.pause();

        assertThatThrownBy(() -> usersLDAPRepository.countUsers())
            .isInstanceOf(UsersRepositoryException.class)
            .hasMessage("Unable to retrieve user count from ldap");
    }

    @Test
    void getUsersCountShouldBackToNormalWhenMasterIsRecoveredAfterLdapClusterOutage() throws Exception {
        masterLdap.pause();
        slaveLdap.pause();

        assertThatThrownBy(() -> usersLDAPRepository.countUsers())
            .isInstanceOf(UsersRepositoryException.class)
            .hasMessage("Unable to retrieve user count from ldap");

        masterLdap.unpause();
        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void getUsersCountShouldBackToNormalWhenSlaveIsRecoveredAfterLdapClusterOutage() throws Exception {
        masterLdap.pause();
        slaveLdap.pause();

        assertThatThrownBy(() -> usersLDAPRepository.countUsers())
            .isInstanceOf(UsersRepositoryException.class)
            .hasMessage("Unable to retrieve user count from ldap");

        slaveLdap.unpause();
        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void allBadLdapURIsShouldThrowExceptionUponLdapThreadPoolInitialization() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfigurationWithVirtualHosting(List.of(masterLdap, slaveLdap));
        masterLdap.pause();
        slaveLdap.pause();

        usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatThrownBy(() -> usersLDAPRepository.init())
            .isInstanceOf(LDAPException.class)
            .hasMessageContaining("A client-side timeout was encountered");
    }

    @Test
    void onlyBadMasterLdapURIShouldNotThrowExceptionUponLdapThreadPoolInitialization() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfigurationWithVirtualHosting(List.of(masterLdap, slaveLdap));
        masterLdap.pause();

        usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatCode(() -> usersLDAPRepository.init())
            .doesNotThrowAnyException();
        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void onlyBadSlaveLdapURIShouldNotThrowExceptionUponLdapThreadPoolInitialization() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfigurationWithVirtualHosting(List.of(masterLdap, slaveLdap));
        slaveLdap.pause();

        usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatCode(() -> usersLDAPRepository.init())
            .doesNotThrowAnyException();
        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    @Test
    void shouldSupportBackwardCompatibilityForTheLegacyLdapHostProperty() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfigurationWithVirtualHosting(List.of(masterLdap, slaveLdap), "ldapHost");

        usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatCode(() -> usersLDAPRepository.init())
            .doesNotThrowAnyException();
        assertThat(usersLDAPRepository.countUsers()).isEqualTo(1);
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(List<LdapGenericContainer> ldapContainers) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainers, Optional.of(ADMIN), "ldapHosts");
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(List<LdapGenericContainer> ldapContainers, String ldapHostProperty) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainers, Optional.of(ADMIN), ldapHostProperty);
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(List<LdapGenericContainer> ldapContainers, Optional<Username> administrator, String ldapHostsProperty) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainers, ldapHostsProperty);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    static PropertyListConfiguration baseConfiguration(List<LdapGenericContainer> ldapContainers, String ldapHostsProperty) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty(String.format("[@%s]", ldapHostsProperty), ldapContainers.stream()
            .map(LdapGenericContainer::getLdapHost)
            .collect(Collectors.joining(",")));
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        return configuration;
    }
}
