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

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.apache.james.user.ldap.DockerLdapSingleton.DOMAIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConversionException;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

class ReadOnlyUsersLDAPRepositoryTest {

    static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyUsersLDAPRepositoryTest.class);
    static final Username JAMES_USER_MAIL = Username.of(JAMES_USER.getLocalPart()+ "@" + DOMAIN);
    static final Username UNKNOWN = Username.of("unknown");
    static final String BAD_PASSWORD = "badpassword";

    static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .build();

    DomainList domainList;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.start();
    }

    @BeforeEach
    void setUp() {
        domainList = mock(DomainList.class);
    }

    @Nested
    class SupportVirtualHosting {

        @Test
        void supportVirtualHostingShouldReturnFalseByDefault() throws Exception {
            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);
            usersLDAPRepository.configure(ldapRepositoryConfiguration(ldapContainer));

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        void supportVirtualHostingShouldReturnTrueWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "true");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isTrue();
        }

        @Test
        void supportVirtualHostingShouldReturnFalseWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "false");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        void configureShouldThrowOnNonBooleanValueForSupportsVirtualHosting() {
            HierarchicalConfiguration<ImmutableNode> configuration = ldapRepositoryConfiguration(ldapContainer);
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "bad");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);

            assertThatThrownBy(() -> usersLDAPRepository.configure(configuration))
                .isInstanceOf(ConversionException.class);
        }
    }

    @Nested
    class TestUser {

        @Test
        void knownUserShouldBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer));
            assertThat(ldapRepository.test(JAMES_USER, PASSWORD)).isTrue();
        }

        @Test
        void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrect() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer));
            assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogIn() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer));
            assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer));
            assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        void knownUserShouldBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ldapRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        void testShouldListUsers() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ImmutableList.copyOf(ldapRepository.list()))
                .containsOnly(JAMES_USER_MAIL);
        }

        @Test
        void testShouldStillWorkAfterRestartingLDAP() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            ldapRepository.test(JAMES_USER_MAIL, PASSWORD);

            DockerLdapSingleton.ldapContainer.pause();
            try {
                ldapRepository.test(JAMES_USER_MAIL, PASSWORD);
            } catch (Exception e) {
                LOGGER.info("This exception is expected as we shut down the LDAP and forced its use", e);
            }
            DockerLdapSingleton.ldapContainer.unpause();

            assertThat(ldapRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrectWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        void specialCharacterInUserInputShouldBeSanitized() throws Exception {
            Username patternMatchingMultipleUsers = Username.of("j*");

            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ldapRepository.test(patternMatchingMultipleUsers, PASSWORD)).isFalse();
        }

        @Test
        void containsWithGetUserShouldBeTrue() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration(ldapContainer));
            assertThat(ldapRepository.contains(ldapRepository.getUsername(JAMES_USER_MAIL.asMailAddress()))).isTrue();
        }

        @Test
        void containsWithGetUserShouldBeTrueWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
            assertThat(ldapRepository.contains(ldapRepository.getUsername(JAMES_USER_MAIL.asMailAddress()))).isTrue();
        }

        private ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration) throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
            ldapRepository.configure(ldapRepositoryConfiguration);
            ldapRepository.init();
            return ldapRepository;
        }
    }

    private HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People,dc=james,dc=org");
        configuration.addProperty("[@userIdAttribute]", "uid");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "1");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "2");
        configuration.addProperty("[@retryIntervalScale]", "100");
        configuration.addProperty("[@connectionTimeout]", "100");
        configuration.addProperty("[@readTimeout]", "100");
        return configuration;
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People,dc=james,dc=org");
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "1");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "2");
        configuration.addProperty("[@retryIntervalScale]", "100");
        configuration.addProperty("supportsVirtualHosting", true);
        configuration.addProperty("[@connectionTimeout]", "100");
        configuration.addProperty("[@readTimeout]", "100");
        return configuration;
    }
}
