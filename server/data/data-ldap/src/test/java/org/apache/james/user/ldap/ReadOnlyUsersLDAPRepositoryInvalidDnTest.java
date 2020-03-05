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
import static org.apache.james.user.ldap.ReadOnlyUsersLDAPRepositoryTest.ldapRepositoryConfigurationWithVirtualHosting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.domainlist.api.DomainList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ReadOnlyUsersLDAPRepositoryInvalidDnTest {
    static LdapGenericContainer ldapContainer = LdapGenericContainer.builder()
        .dockerFilePrefix("invalid/")
        .domain(DOMAIN)
        .password(ADMIN_PASSWORD)
        .build();

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

    @BeforeEach
    void setUp() throws Exception {
        domainList = mock(DomainList.class);
        ldapRepository = startUsersRepository(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
    }

    @Test
    void listShouldFilterOutUsersWithoutIdField() throws Exception {
        assertThat(ImmutableList.copyOf(ldapRepository.list()))
            .isEmpty();
    }

    @Test
    void getUserByNameShouldReturnNullWhenNoIdField() throws Exception {
        assertThat(ldapRepository.getUserByName(JAMES_USER)).isNull();
    }

    @Test
    void containsShouldReturnFalseWhenNoIdField() throws Exception {
        assertThat(ldapRepository.contains(JAMES_USER)).isFalse();
    }

    @Test
    void contShouldReturnZeroWhenInvalidUser() throws Exception {
        assertThat(ldapRepository.countUsers()).isEqualTo(0);
    }

    private ReadOnlyUsersLDAPRepository startUsersRepository(HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration) throws Exception {
        ReadOnlyUsersLDAPRepository ldapRepository = new ReadOnlyUsersLDAPRepository(domainList);
        ldapRepository.configure(ldapRepositoryConfiguration);
        ldapRepository.init();
        return ldapRepository;
    }
}
