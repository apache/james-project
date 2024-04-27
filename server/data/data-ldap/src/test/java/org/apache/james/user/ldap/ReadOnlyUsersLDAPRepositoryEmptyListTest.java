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

import static org.apache.james.user.ldap.ReadOnlyUsersLDAPRepositoryTest.ldapRepositoryConfiguration;
import static org.apache.james.user.ldap.ReadOnlyUsersLDAPRepositoryTest.ldapRepositoryConfigurationWithVirtualHosting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ReadOnlyUsersLDAPRepositoryEmptyListTest {
    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    DomainList domainList;
    private ReadOnlyUsersLDAPRepository ldapRepository;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @Nested
    class WhenDisableVirtualHosting {

        @BeforeEach
        void setUp() throws Exception {
            domainList = mock(DomainList.class);
            HierarchicalConfiguration<ImmutableNode> config = ldapRepositoryConfiguration(ldapContainer);
            config.setProperty("[@userBase]", "ou=empty,dc=james,dc=org");
            ldapRepository = startUsersRepository(config);
        }

        @Test
        void listShouldReturnEmptyWhenNoEntity() throws Exception {
            assertThat(ImmutableList.copyOf(ldapRepository.list()))
                .isEmpty();
        }

        @Test
        void countUsersShouldReturnZeroWhenEmptyRepository() throws UsersRepositoryException {
            //Given
            int expected = 0;
            //When
            int actual = ldapRepository.countUsers();
            //Then
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class SupportVirtualHosting {
        @BeforeEach
        void setUp() throws Exception {
            domainList = mock(DomainList.class);
            HierarchicalConfiguration<ImmutableNode> config = ldapRepositoryConfigurationWithVirtualHosting(ldapContainer);
            config.setProperty("[@userBase]", "ou=empty,dc=james,dc=org");
            ldapRepository = startUsersRepository(config);
        }

        @Test
        void listShouldReturnEmptyWhenNoEntity() throws Exception {
            assertThat(ImmutableList.copyOf(ldapRepository.list()))
                .isEmpty();
        }

        @Test
        void countUsersShouldReturnZeroWhenEmptyRepository() throws UsersRepositoryException {
            //Given
            int expected = 0;
            //When
            int actual = ldapRepository.countUsers();
            //Then
            assertThat(actual).isEqualTo(expected);
        }
    }

    private ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration) throws Exception {
        ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
        ldapRepository.configure(ldapRepositoryConfiguration);
        ldapRepository.init();
        return ldapRepository;
    }
}
