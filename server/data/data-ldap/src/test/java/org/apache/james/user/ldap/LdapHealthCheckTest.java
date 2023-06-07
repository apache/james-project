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

import org.apache.james.core.healthcheck.Result;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LdapHealthCheckTest {

    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;
    private LdapHealthCheck ldapHealthCheck;
    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @BeforeEach
    public void setUp() throws Exception {
        ReadOnlyUsersLDAPRepository ldapUserRepository = new ReadOnlyUsersLDAPRepository(new SimpleDomainList());
        ldapUserRepository.configure(ReadOnlyUsersLDAPRepositoryTest.ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
        ldapUserRepository.init();
        ldapHealthCheck = new LdapHealthCheck(ldapUserRepository);
    }

    @Test
    void checkShouldReturnUnhealthyIfLdapIsDown() {
        ldapContainer.pause();

        try {
            Result checkResult = ldapHealthCheck.check().block();
            assertThat(checkResult.isUnHealthy()).isTrue();
        } finally {
            ldapContainer.unpause();
        }
    }

    @Test
    void checkShouldReturnHealthyIfLdapIsRunning() {
        Result checkResult = ldapHealthCheck.check().block();
        assertThat(checkResult.isHealthy()).isTrue();
    }
}
