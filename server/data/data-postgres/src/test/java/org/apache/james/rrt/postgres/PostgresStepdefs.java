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
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableFixture;
import org.apache.james.rrt.lib.RewriteTablesStepdefs;
import org.apache.james.user.postgres.PostgresUserModule;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepository;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;

import com.github.fge.lambdas.Throwing;

import io.cucumber.java.After;
import io.cucumber.java.Before;

public class PostgresStepdefs {
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresModule.aggregateModules(PostgresRecipientRewriteTableModule.MODULE, PostgresUserModule.MODULE));

    private final RewriteTablesStepdefs mainStepdefs;

    public PostgresStepdefs(RewriteTablesStepdefs mainStepdefs) {
        this.mainStepdefs = mainStepdefs;
    }

    @Before
    public void setup() throws Throwable {
        postgresExtension.beforeAll(null);
        postgresExtension.beforeEach(null);
        mainStepdefs.setUp(Throwing.supplier(this::getRecipientRewriteTable).sneakyThrow());
    }

    @After
    public void tearDown() {
        postgresExtension.afterEach(null);
        postgresExtension.afterAll(null);
    }

    private AbstractRecipientRewriteTable getRecipientRewriteTable() throws DomainListException {
        PostgresRecipientRewriteTable postgresRecipientRewriteTable = new PostgresRecipientRewriteTable(new PostgresRecipientRewriteTableDAO(postgresExtension.getPostgresExecutor()));
        postgresRecipientRewriteTable.setUsersRepository(new PostgresUsersRepository(RecipientRewriteTableFixture.domainListForCucumberTests(),
            new PostgresUsersDAO(postgresExtension.getPostgresExecutor(), PostgresUsersRepositoryConfiguration.DEFAULT)));
        postgresRecipientRewriteTable.setDomainList(RecipientRewriteTableFixture.domainListForCucumberTests());
        return postgresRecipientRewriteTable;
    }
}
