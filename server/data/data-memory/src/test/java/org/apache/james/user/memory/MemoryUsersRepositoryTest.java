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

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryUsersRepositoryTest {
    @Nested
    class WhenEnableVirtualHosting implements UsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private MemoryUsersRepository memoryUsersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) {
            memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(testSystem.getDomainList());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepository testee() {
            return memoryUsersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(testSystem.getDomainList());
            memoryUsersRepository.configure(configuration(administrator, true));
            return memoryUsersRepository;
        }

        @Test
        void assertValidShouldThrowWhenNoDomainPartAndVirtualHosting() {
            assertThatThrownBy(() -> memoryUsersRepository.assertValid(Username.of("user")))
                .isInstanceOf(UsersRepositoryException.class);
        }

        @Test
        void assertValidShouldNotThrowWhenDomainPartAndVirtualHosting() throws Exception {
            MemoryDomainList domainList = new MemoryDomainList();
            domainList.addDomain(Domain.of("domain.tld"));

            MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

            assertThatCode(() -> memoryUsersRepository.assertValid(Username.of("user@domain.tld")))
                .doesNotThrowAnyException();
        }

        @Test
        void assertValidShouldNotThrowWhenDomainPartAndDomainNotFound() throws Exception {
            MemoryDomainList domainList = new MemoryDomainList();

            MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

            assertThatThrownBy(() -> memoryUsersRepository.assertValid(Username.of("user@domain.tld")))
                .isInstanceOf(UsersRepositoryException.class);
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements UsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private MemoryUsersRepository memoryUsersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) {
            memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting(testSystem.getDomainList());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepository testee() {
            return memoryUsersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withVirtualHosting(testSystem.getDomainList());
            memoryUsersRepository.configure(configuration(administrator, false));
            return memoryUsersRepository;
        }

        @Test
        void assertValidShouldThrowWhenDomainPartAndNoVirtualHosting(TestSystem testSystem) {
            MemoryUsersRepository memoryUsersRepository = MemoryUsersRepository.withoutVirtualHosting(testSystem.getDomainList());

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

    private HierarchicalConfiguration<ImmutableNode> configuration(Optional<Username> administrator, boolean enableVirtualHosting) {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        administrator.ifPresent(username -> configuration.addProperty("administratorId", username.asString()));

        configuration.addProperty("enableVirtualHosting", enableVirtualHosting);
        configuration.addProperty("algorithm", "none");
        return configuration;
    }
}
