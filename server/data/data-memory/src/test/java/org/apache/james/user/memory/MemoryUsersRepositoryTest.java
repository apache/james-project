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

package org.apache.james.user.memory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryUsersRepositoryTest extends AbstractUsersRepositoryTest {

    @RegisterExtension
    static UserRepositoryExtension extension = new UserRepositoryExtension(true);

    private MemoryUsersRepository memoryUsersRepository;

    @BeforeEach
    void setUp(TestSystem testSystem) {
        memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(testSystem.getDomainList());
    }

    @Override
    protected AbstractUsersRepository testee() {
        return memoryUsersRepository;
    }

    @Test
    void assertValidShouldThrowWhenDomainPartAndNoVirtualHosting(TestSystem testSystem) {
        MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting(testSystem.getDomainList());

        assertThatThrownBy(() -> memoryUsersRepository.assertValid(Username.of("user@domain.tld")))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void assertValidShouldThrowWhenNoDomainPartAndVirtualHosting(TestSystem testSystem) {
        MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(testSystem.getDomainList());

        assertThatThrownBy(() -> memoryUsersRepository.assertValid(Username.of("user")))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void assertValidShouldNotThrowWhenDomainPartAndVirtualHosting() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(new InMemoryDNSService()
            .registerMxRecord("localhost", "127.0.0.1")
            .registerMxRecord("127.0.0.1", "127.0.0.1"));
        domainList.setAutoDetect(false);
        domainList.setAutoDetectIP(false);
        domainList.addDomain(Domain.of("domain.tld"));

        MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        assertThatCode(() -> memoryUsersRepository.assertValid(Username.of("user@domain.tld")))
            .doesNotThrowAnyException();
    }

    @Test
    void assertValidShouldNotThrowWhenDomainPartAndDomainNotFound() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(new InMemoryDNSService()
            .registerMxRecord("localhost", "127.0.0.1")
            .registerMxRecord("127.0.0.1", "127.0.0.1"));
        domainList.setAutoDetect(false);
        domainList.setAutoDetectIP(false);

        MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        assertThatThrownBy(() -> memoryUsersRepository.assertValid(Username.of("user@domain.tld")))
            .isInstanceOf(UsersRepositoryException.class);
    }

    @Test
    void assertValidShouldNotThrowWhenNoDomainPartAndNoVirtualHosting(TestSystem testSystem) {
        MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting(testSystem.getDomainList());

        assertThatCode(() -> memoryUsersRepository.assertValid(Username.of("user")))
            .doesNotThrowAnyException();
    }
}
