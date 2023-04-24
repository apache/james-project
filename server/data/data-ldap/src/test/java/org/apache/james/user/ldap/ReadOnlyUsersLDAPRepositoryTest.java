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
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_LOCAL_PART;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.apache.james.user.ldap.DockerLdapSingleton.DOMAIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.unboundid.ldap.sdk.LDAPException;

class ReadOnlyUsersLDAPRepositoryTest {

    static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyUsersLDAPRepositoryTest.class);
    static final Username JAMES_USER_MAIL = Username.of(JAMES_USER.getLocalPart() + "@" + DOMAIN);
    static final Username UNKNOWN = Username.of("unknown");
    static final String BAD_PASSWORD = "badpassword";
    public static final String SUPPORTS_VIRTUAL_HOSTING = "supportsVirtualHosting";

    static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .build();

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @Test
    void shouldNotStartWithInvalidFilter() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
        configuration.addProperty("[@filter]", "INVALID!!!");

        ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatThrownBy(usersLDAPRepository::init)
            .isInstanceOf(LDAPException.class);
    }

    @Nested
    class FilterTests {
        @Test
        void filterShouldKeepMatchingEntries() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty("[@filter]", "(sn=james-user)");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);
            usersLDAPRepository.init();

            assertThat(usersLDAPRepository.contains(JAMES_USER)).isTrue();
        }

        @Test
        void filterShouldFilterOutNonMatchingEntries() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty("[@filter]", "(sn=nomatch)");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);
            usersLDAPRepository.init();

            assertThat(usersLDAPRepository.contains(JAMES_USER)).isFalse();
        }

        @Test
        void countShouldTakeFilterIntoAccount() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty("[@filter]", "(sn=nomatch)");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);
            usersLDAPRepository.init();

            assertThat(usersLDAPRepository.countUsers()).isEqualTo(0);
        }

        @Test
        void listShouldTakeFilterIntoAccount() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty("[@filter]", "(sn=nomatch)");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);
            usersLDAPRepository.init();

            assertThat(ImmutableList.copyOf(usersLDAPRepository.list())).isEmpty();
        }
    }

    @Nested
    class WhenEnableVirtualHosting implements UsersRepositoryContract.WithVirtualHostingReadOnlyContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private ReadOnlyUsersLDAPRepository usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer), testSystem.getDomainList());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<ReadOnlyLDAPUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, administrator), testSystem.getDomainList());
        }

        @Test
        void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin(TestSystem testSystem) throws Exception {
            assertThat(testee().isAdministrator(testSystem.getAdmin())).isTrue();
        }

        @Test
        void knownUserShouldBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        void listShouldReturnExistingUsers() throws Exception {
            assertThat(ImmutableList.copyOf(usersRepository.list())).containsOnly(JAMES_USER_MAIL);
        }

        @Test
        void countUsersShouldReturnOne() throws Exception {
            assertThat(usersRepository.countUsers()).isEqualTo(1);
        }

        @Test
        void containsShouldReturnTrueWhenUserExists() throws Exception {
            assertThat(usersRepository.contains(JAMES_USER_MAIL)).isTrue();
        }

        @Test
        void containsShouldReturnFalseWhenUserDoesNotExists() throws Exception {
            assertThat(usersRepository.contains(Username.of("unknown@" + DOMAIN))).isFalse();
        }

        @Test
        void testShouldStillWorkAfterRestartingLDAP() throws Exception {
            usersRepository.test(JAMES_USER_MAIL, PASSWORD);

            DockerLdapSingleton.ldapContainer.pause();
            try {
                usersRepository.test(JAMES_USER_MAIL, PASSWORD);
            } catch (Exception e) {
                LOGGER.info("This exception is expected as we shut down the LDAP and forced its use", e);
            }
            DockerLdapSingleton.ldapContainer.unpause();

            assertThat(usersRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrectWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        void specialCharacterInUserInputShouldBeSanitized() throws Exception {
            Username patternMatchingMultipleUsers = Username.of("j*");

            assertThat(usersRepository.test(patternMatchingMultipleUsers, PASSWORD)).isFalse();
        }

        @Test
        void containsWithGetUserShouldBeTrueWithVirtualHosting() throws Exception {
            assertThat(usersRepository.contains(usersRepository.getUsername(JAMES_USER_MAIL.asMailAddress()))).isTrue();
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case," +
            "See @link{ReadOnlyUsersLDAPRepositoryEmptyListTest}")
        @Override
        @Test
        public void listShouldReturnEmptyIteratorWhenEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case," +
            "See @link{ReadOnlyUsersLDAPRepositoryEmptyListTest}")
        @Override
        @Test
        public void countUsersShouldReturnZeroWhenEmptyRepository() {
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements UsersRepositoryContract.WithOutVirtualHostingReadOnlyContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private ReadOnlyUsersLDAPRepository usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer), testSystem.getDomainList());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<ReadOnlyLDAPUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return startUsersRepository(ldapRepositoryConfiguration(ldapContainer, administrator), testSystem.getDomainList());
        }

        @Test
        void knownUserShouldBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            assertThat(usersRepository.test(JAMES_USER, PASSWORD)).isTrue();
        }

        @Test
        void listShouldReturnExistingUsers() throws Exception {
            assertThat(ImmutableList.copyOf(usersRepository.list())).containsOnly(JAMES_USER);
        }

        @Test
        void countUsersShouldReturnOne() throws Exception {
            assertThat(usersRepository.countUsers()).isEqualTo(1);
        }

        @Test
        void containsShouldReturnTrueWhenUserExists() throws Exception {
            assertThat(usersRepository.contains(JAMES_USER)).isTrue();
        }

        @Test
        void containsShouldReturnFalseWhenUserDoesNotExists() throws Exception {
            assertThat(usersRepository.contains(Username.of("unknown"))).isFalse();
        }

        @Test
        void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrect() throws Exception {
            assertThat(usersRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogIn() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            assertThat(usersRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        void containsWithGetUserShouldBeTrue() throws Exception {
            assertThat(usersRepository.contains(usersRepository.getUsername(JAMES_USER_MAIL.asMailAddress()))).isTrue();
        }

        @Test
        void isAdministratorShouldReturnTrueWhenConfiguredAndUserIsAdmin(TestSystem testSystem) throws Exception {
            assertThat(testee().isAdministrator(testSystem.getAdmin())).isTrue();
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case," +
            "See @link{ReadOnlyUsersLDAPRepositoryEmptyListTest}")
        @Override
        @Test
        public void listShouldReturnEmptyIteratorWhenEmptyRepository(TestSystem testSystem) {
        }

        @Disabled("JAMES-3088 Users are provisioned by default from Dockerfile, cannot setup this test case," +
            "See @link{ReadOnlyUsersLDAPRepositoryEmptyListTest}")
        @Override
        @Test
        public void countUsersShouldReturnZeroWhenEmptyRepository() {
        }
    }

    @Nested
    class SupportVirtualHosting {

        @Test
        void supportVirtualHostingShouldReturnFalseByDefault() throws Exception {
            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(ldapRepositoryConfiguration(ldapContainer));

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        void supportVirtualHostingShouldReturnTrueWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(SUPPORTS_VIRTUAL_HOSTING, "true");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isTrue();
        }

        @Test
        void supportVirtualHostingShouldReturnFalseWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(SUPPORTS_VIRTUAL_HOSTING, "false");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        void configureShouldThrowOnNonBooleanValueForSupportsVirtualHosting() {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(SUPPORTS_VIRTUAL_HOSTING, "bad");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());

            assertThatThrownBy(() -> usersLDAPRepository.configure(configuration))
                .isInstanceOf(ConversionException.class);
        }
    }

    @Test
    void shouldSupportLDAPS() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapsHost());

        ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatThrownBy(usersLDAPRepository::init)
            .isInstanceOf(LDAPException.class)
            .hasMessageContaining("SSLHandshakeException");
    }

    @Test
    void shouldSupportLDAPSWithoutCertificateValidation() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapsHost());
        configuration.addProperty("[@trustAllCerts]", "true");

        ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        usersLDAPRepository.configure(configuration);

        assertThatThrownBy(usersLDAPRepository::init)
            .isInstanceOf(LDAPException.class)
            .hasMessageContaining("SSLHandshakeException");
    }

    private static ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration,
                                                             DomainList domainList) throws Exception {
        ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
        ldapRepository.configure(ldapRepositoryConfiguration);
        ldapRepository.init();
        return ldapRepository;
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer) {
        return ldapRepositoryConfiguration(ldapContainer, Optional.of(Username.of(ADMIN_LOCAL_PART)));
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer,  Optional<Username> administrator) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "uid");
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN));
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    private static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@connectionTimeout]", "100");
        configuration.addProperty("[@readTimeout]", "100");
        return configuration;
    }
}
