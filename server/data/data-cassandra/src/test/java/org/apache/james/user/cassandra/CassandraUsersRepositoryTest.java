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

package org.apache.james.user.cassandra;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraUsersRepositoryTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraUsersRepositoryDataDefinition.MODULE);

    @Nested
    class WhenEnableVirtualHosting implements UsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private UsersRepositoryImpl<CassandraUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<CassandraUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }

        @Override
        public UsersRepository testee(Set<Username> administrators) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrators);
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements UsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private UsersRepositoryImpl<CassandraUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<CassandraUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }

        @Override
        public UsersRepository testee(Set<Username> administrators) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrators);
        }
    }

    private static UsersRepositoryImpl<CassandraUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Optional<Username> administrator) throws Exception {
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrator.ifPresent(username -> configuration.addProperty("administratorId", username.asString()));

        UsersRepositoryImpl<CassandraUsersDAO> usersRepository = new UsersRepositoryImpl<>(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }

    private static UsersRepositoryImpl<CassandraUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Set<Username> administrators) throws Exception {
        CassandraUsersDAO usersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrators.forEach(admin -> configuration.addProperty("administratorIds.administratorId", admin.asString()));
        UsersRepositoryImpl<CassandraUsersDAO> usersRepository = new UsersRepositoryImpl<>(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }
}
