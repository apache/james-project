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

import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadOnlyUsersLDAPRepositoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyUsersLDAPRepositoryTest.class);
    private static final String JAMES_USER_MAIL = JAMES_USER + "@" + DOMAIN;
    private static final String UNKNOWN = "unknown";
    private static final String BAD_PASSWORD = "badpassword";

    private DomainList domainList;

    @BeforeEach
    void setUp() {
        domainList = mock(DomainList.class);
    }

    @Nested
    class SupportVirtualHosting {

        @Test
        public void supportVirtualHostingShouldReturnFalseByDefault() throws Exception {
            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);
            usersLDAPRepository.configure(ldapRepositoryConfiguration());

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        public void supportVirtualHostingShouldReturnTrueWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration configuration = ldapRepositoryConfiguration();
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "true");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isTrue();
        }

        @Test
        public void supportVirtualHostingShouldReturnFalseWhenReportedInConfig() throws Exception {
            HierarchicalConfiguration configuration = ldapRepositoryConfiguration();
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "false");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);
            usersLDAPRepository.configure(configuration);

            assertThat(usersLDAPRepository.supportVirtualHosting()).isFalse();
        }

        @Test
        public void configureShouldThrowOnNonBooleanValueForSupportsVirtualHosting() throws Exception {
            HierarchicalConfiguration configuration = ldapRepositoryConfiguration();
            configuration.addProperty(ReadOnlyUsersLDAPRepository.SUPPORTS_VIRTUAL_HOSTING, "bad");

            ReadOnlyUsersLDAPRepository usersLDAPRepository = new ReadOnlyUsersLDAPRepository(domainList);

            assertThatThrownBy(()-> usersLDAPRepository.configure(configuration))
                .isInstanceOf(ConversionException.class);
        }
    }

    @Nested
    class TestUser {

        @Test
        public void knownUserShouldBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration());
            assertThat(ldapRepository.test(JAMES_USER, PASSWORD)).isTrue();
        }

        @Test
        public void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrect() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration());
            assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        public void unknownUserShouldNotBeAbleToLogIn() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration());
            assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        public void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration());
            assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        public void knownUserShouldBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
            assertThat(ldapRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
        }

        @Test
        public void testShouldStillWorksAfterRestartingLDAP() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
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
        public void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrectWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
            assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
        }

        @Test
        public void unknownUserShouldNotBeAbleToLogInWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
            assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
        }

        @Test
        public void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
            assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
        }

        @Test
        public void specialCharacterInUserInputShouldBeSanitized() throws Exception {
            String patternMatchingMultipleUsers = "j*";

            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
            assertThat(ldapRepository.test(patternMatchingMultipleUsers, PASSWORD)).isFalse();
        }

        @Test
        public void containsWithGetUserShouldBeTrue() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfiguration());
            assertThat(ldapRepository.contains(ldapRepository.getUser(new MailAddress(JAMES_USER_MAIL)))).isTrue();
        }

        @Test
        public void containsWithGetUserShouldBeTrueWithVirtualHosting() throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
            assertThat(ldapRepository.contains(ldapRepository.getUser(new MailAddress(JAMES_USER_MAIL)))).isTrue();
        }

        private ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration ldapRepositoryConfiguration) throws Exception {
            ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
            ldapRepository.configure(ldapRepositoryConfiguration);
            ldapRepository.init();
            return ldapRepository;
        }
    }

    private static HierarchicalConfiguration ldapRepositoryConfiguration() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", DockerLdapSingleton.ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin\\,dc=james\\,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People\\,dc=james\\,dc=org");
        configuration.addProperty("[@userIdAttribute]", "uid");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "4");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "8");
        configuration.addProperty("[@retryIntervalScale]", "1000");
        return configuration;
    }

    private static HierarchicalConfiguration ldapRepositoryConfigurationWithVirtualHosting() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", DockerLdapSingleton.ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin\\,dc=james\\,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People\\,dc=james\\,dc=org");
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "4");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "8");
        configuration.addProperty("[@retryIntervalScale]", "1000");
        configuration.addProperty("supportsVirtualHosting", true);
        return configuration;
    }
}
