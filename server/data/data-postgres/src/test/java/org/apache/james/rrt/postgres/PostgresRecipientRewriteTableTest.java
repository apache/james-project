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

package org.apache.james.rrt.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableContract;
import org.apache.james.user.postgres.PostgresUserModule;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresRecipientRewriteTableTest implements RecipientRewriteTableContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresModule.aggregateModules(PostgresRecipientRewriteTableModule.MODULE, PostgresUserModule.MODULE));

    private PostgresRecipientRewriteTable postgresRecipientRewriteTable;

    @BeforeEach
    void setup() throws Exception {
        setUp();
    }

    @AfterEach
    void teardown() throws Exception {
        tearDown();
    }

    @Override
    public void createRecipientRewriteTable() {
        postgresRecipientRewriteTable = new PostgresRecipientRewriteTable(new PostgresRecipientRewriteTableDAO(postgresExtension.getDefaultPostgresExecutor()));
        postgresRecipientRewriteTable.setUsersRepository(new PostgresUsersRepository(new SimpleDomainList(),
            new PostgresUsersDAO(postgresExtension.getDefaultPostgresExecutor(), PostgresUsersRepositoryConfiguration.DEFAULT)));
    }

    @Override
    public AbstractRecipientRewriteTable virtualUserTable() {
        return postgresRecipientRewriteTable;
    }
}
