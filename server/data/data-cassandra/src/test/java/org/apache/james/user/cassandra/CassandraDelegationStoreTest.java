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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.DelegationStoreContract;
import org.apache.james.user.api.UsersRepositoryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class CassandraDelegationStoreTest implements DelegationStoreContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraUsersRepositoryModule.MODULE);

    private CassandraDelegationStore testee;
    private CassandraUsersDAO cassandraUsersDAO;

    @BeforeEach
    void setUp() {
        cassandraUsersDAO = new CassandraUsersDAO(cassandraCluster.getCassandraCluster().getConf());
        testee = new CassandraDelegationStore(cassandraUsersDAO, any -> Mono.just(true));
    }

    @Override
    public DelegationStore testee() {
        return testee;
    }

    @Override
    public void addUser(Username username) {
        try {
            cassandraUsersDAO.addUser(username, "password");
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void virtualUsersShouldNotBeListed() {
        testee = new CassandraDelegationStore(cassandraUsersDAO, any -> Mono.just(false));
        addUser(BOB);

        Mono.from(testee().addAuthorizedUser(ALICE).forUser(BOB)).block();

        assertThat(cassandraUsersDAO.listReactive().collectList().block())
            .containsOnly(BOB);
    }
}
