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
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadOnlyUsersLDAPRepositoryTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReadOnlyUsersLDAPRepositoryTest.class);
    private static final String DOMAIN = "james.org";
    private static final String ADMIN_PASSWORD = "mysecretpassword";
    private static final String JAMES_USER = "james-user";
    private static final String JAMES_USER_MAIL = "james-user" + "@" + DOMAIN;
    private static final String UNKNOWN = "unknown";
    private static final String PASSWORD = "secret";
    private static final String BAD_PASSWORD = "badpassword";

    private LdapGenericContainer ldapContainer;
    private ReadOnlyUsersLDAPRepository ldapRepository;
    private DomainList domainList;

    @Before
    public void setup() {
        startLdapContainer();
        domainList = mock(DomainList.class);
    }

    private void startLdapContainer() {
        ldapContainer = LdapGenericContainer.builder()
                .domain(DOMAIN)
                .password(ADMIN_PASSWORD)
                .build();
        ldapContainer.start();
    }

    private void startUsersRepository(HierarchicalConfiguration ldapRepositoryConfiguration) throws Exception {
        ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
        ldapRepository.configure(ldapRepositoryConfiguration);
        ldapRepository.init();
    }

    private HierarchicalConfiguration ldapRepositoryConfiguration() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
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

    private HierarchicalConfiguration ldapRepositoryConfigurationWithVirtualHosting() {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
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

    @After
    public void tearDown() {
        if (ldapContainer != null) {
            ldapContainer.stop();
        }
    }

    @Test
    public void knownUserShouldBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
        startUsersRepository(ldapRepositoryConfiguration());
        assertThat(ldapRepository.test(JAMES_USER, PASSWORD)).isTrue();
    }

    @Test
    public void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrect() throws Exception {
        startUsersRepository(ldapRepositoryConfiguration());
        assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
    }

    @Test
    public void unknownUserShouldNotBeAbleToLogIn() throws Exception {
        startUsersRepository(ldapRepositoryConfiguration());
        assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
    }

    @Test
    public void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
        startUsersRepository(ldapRepositoryConfiguration());
        assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
    }

    @Test
    public void knownUserShouldBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        assertThat(ldapRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
    }

    @Test
    public void testShouldStillWorksAfterRestartingLDAP() throws Exception {
        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        ldapRepository.test(JAMES_USER_MAIL, PASSWORD);

        ldapContainer.pause();
        try {
            ldapRepository.test(JAMES_USER_MAIL, PASSWORD);
        } catch (Exception e) {
            LOGGER.info("This exception is expected as we shut down the LDAP and forced its use", e);
        }
        ldapContainer.unpause();

        assertThat(ldapRepository.test(JAMES_USER_MAIL, PASSWORD)).isTrue();
    }


    @Test
    public void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrectWithVirtualHosting() throws Exception {
        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
    }

    @Test
    public void unknownUserShouldNotBeAbleToLogInWithVirtualHosting() throws Exception {
        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
    }

    @Test
    public void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrectWithVirtualHosting() throws Exception {
        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
    }

    @Test
    public void containsWithGetUserShouldBeTrue() throws Exception {
        startUsersRepository(ldapRepositoryConfiguration());
        assertThat(ldapRepository.contains(ldapRepository.getUser(new MailAddress(JAMES_USER_MAIL)))).isTrue();
    }

    @Test
    public void containsWithGetUserShouldBeTrueWithVirtualHosting() throws Exception {
        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        assertThat(ldapRepository.contains(ldapRepository.getUser(new MailAddress(JAMES_USER_MAIL)))).isTrue();
    }

    @Test
    public void specialCharacterInUserInputShouldBeSanitized() throws Exception {
        String patternMatchingMultipleUsers = "j*";

        startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting());
        assertThat(ldapRepository.test(patternMatchingMultipleUsers, PASSWORD)).isFalse();
    }
}
