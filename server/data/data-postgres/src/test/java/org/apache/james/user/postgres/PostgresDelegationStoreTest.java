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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.DelegationStoreContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class PostgresDelegationStoreTest implements DelegationStoreContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresUserModule.MODULE);

    private PostgresUsersDAO postgresUsersDAO;
    private PostgresDelegationStore postgresDelegationStore;

    @BeforeEach
    void beforeEach() {
        postgresUsersDAO = new PostgresUsersDAO(postgresExtension.getPostgresExecutor(), PostgresUsersRepositoryConfiguration.DEFAULT);
        postgresDelegationStore = new PostgresDelegationStore(postgresUsersDAO, any -> Mono.just(true));
    }

    @Override
    public DelegationStore testee() {
        return postgresDelegationStore;
    }

    @Override
    public void addUser(Username username) {
        postgresUsersDAO.addUser(username, "password");
    }

    @Test
    void virtualUsersShouldNotBeListed() {
        postgresDelegationStore = new PostgresDelegationStore(postgresUsersDAO, any -> Mono.just(false));
        addUser(BOB);

        Mono.from(testee().addAuthorizedUser(ALICE).forUser(BOB)).block();

        assertThat(postgresUsersDAO.listReactive().collectList().block())
            .containsOnly(BOB);
    }
}
