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

package org.apache.james.user.postgres;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.SinglePostgresConnectionFactory;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.lib.UsersRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

class PostgresUsersRepositoryTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresUserModule.MODULE);

    @Nested
    class WhenEnableVirtualHosting implements UsersRepositoryContract.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private UsersRepositoryImpl<PostgresUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<PostgresUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements UsersRepositoryContract.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private UsersRepositoryImpl<PostgresUsersDAO> usersRepository;
        private TestSystem testSystem;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), Optional.empty());
            this.testSystem = testSystem;
        }

        @Override
        public UsersRepositoryImpl<PostgresUsersDAO> testee() {
            return usersRepository;
        }

        @Override
        public UsersRepository testee(Optional<Username> administrator) throws Exception {
            return getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting(), administrator);
        }
    }

    private static UsersRepositoryImpl<PostgresUsersDAO> getUsersRepository(DomainList domainList, boolean enableVirtualHosting, Optional<Username> administrator) throws Exception {
        PostgresUsersDAO usersDAO = new PostgresUsersDAO(postgresExtension.getPostgresExecutor(),
            PostgresUsersRepositoryConfiguration.DEFAULT);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        administrator.ifPresent(username -> configuration.addProperty("administratorId", username.asString()));

        UsersRepositoryImpl<PostgresUsersDAO> usersRepository = new PostgresUsersRepository(domainList, usersDAO);
        usersRepository.configure(configuration);
        return usersRepository;
    }
}
