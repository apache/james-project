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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
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
    private static final String UNKNOWN = "unknown";
    private static final String PASSWORD = "secret";
    private static final String BAD_PASSWORD = "badpassword";

    private LdapGenericContainer ldapContainer;
    private ReadOnlyUsersLDAPRepository ldapRepository;

    @Before
    public void setup() throws Exception {
        ldapContainer = LdapGenericContainer.builder()
                .domain(DOMAIN)
                .password(ADMIN_PASSWORD)
                .build();
        ldapContainer.start();
        ldapRepository = new ReadOnlyUsersLDAPRepository();
        ldapRepository.configure(ldapRepositoryConfiguration());
        ldapRepository.setLog(LOGGER);
        ldapRepository.init();
    }

    private HierarchicalConfiguration ldapRepositoryConfiguration() throws ConfigurationException {
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

    @After
    public void tearDown() {
        if (ldapContainer != null) {
            ldapContainer.stop();
        }
    }

    @Test
    public void knownUserShouldBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
        assertThat(ldapRepository.test(JAMES_USER, PASSWORD)).isTrue();
    }

    @Test
    public void knownUserShouldNotBeAbleToLogInWhenPasswordIsNotCorrect() throws Exception {
        assertThat(ldapRepository.test(JAMES_USER, BAD_PASSWORD)).isFalse();
    }

    @Test
    public void unknownUserShouldNotBeAbleToLogIn() throws Exception {
        assertThat(ldapRepository.test(UNKNOWN, BAD_PASSWORD)).isFalse();
    }

    @Test
    public void unknownUserShouldNotBeAbleToLogInWhenPasswordIsCorrect() throws Exception {
        assertThat(ldapRepository.test(UNKNOWN, PASSWORD)).isFalse();
    }
}
