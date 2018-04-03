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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.james.domainlist.api.DomainList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ReadOnlyUsersLDAPRepositoryTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private DomainList domainList;

    @Before
    public void setUp() {
        domainList = mock(DomainList.class);
    }

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

        expectedException.expect(ConversionException.class);

        usersLDAPRepository.configure(configuration);
    }

    private HierarchicalConfiguration ldapRepositoryConfiguration() throws ConfigurationException {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", "ldap://127.0.0.1");
        configuration.addProperty("[@principal]", "cn=admin\\,dc=james\\,dc=org");
        configuration.addProperty("[@credentials]", "secret");
        configuration.addProperty("[@userBase]", "ou=People\\,dc=james\\,dc=org");
        configuration.addProperty("[@userIdAttribute]", "uid");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "4");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "8");
        configuration.addProperty("[@retryIntervalScale]", "1000");
        return configuration;
    }

}
