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
package org.apache.james.user.jpa;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.jpa.model.JPAUser;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.AbstractUsersRepositoryTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

class JpaUsersRepositoryTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAUser.class);

    @Nested
    class WhenEnableVirtualHosting implements AbstractUsersRepositoryTest.WithVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withVirtualHost();

        private JPAUsersRepository usersRepository;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting());
        }

        @Override
        public AbstractUsersRepository testee() {
            return usersRepository;
        }
    }

    @Nested
    class WhenDisableVirtualHosting implements AbstractUsersRepositoryTest.WithOutVirtualHostingContract {
        @RegisterExtension
        UserRepositoryExtension extension = UserRepositoryExtension.withoutVirtualHosting();

        private JPAUsersRepository usersRepository;

        @BeforeEach
        void setUp(TestSystem testSystem) throws Exception {
            usersRepository = getUsersRepository(testSystem.getDomainList(), extension.isSupportVirtualHosting());
        }

        @Override
        public AbstractUsersRepository testee() {
            return usersRepository;
        }
    }

    @AfterEach
    void tearDown() {
        JPA_TEST_CLUSTER.clear("JAMES_USER");
    }

    private static JPAUsersRepository getUsersRepository(DomainList domainList, boolean enableVirtualHosting) throws Exception {
        JPAUsersRepository repos = new JPAUsersRepository(domainList);
        repos.setEntityManagerFactory(JPA_TEST_CLUSTER.getEntityManagerFactory());
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("enableVirtualHosting", String.valueOf(enableVirtualHosting));
        repos.configure(configuration);
        return repos;
    }
}
