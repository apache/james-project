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

package org.apache.james.backends.postgres;

import static org.apache.james.backends.postgres.PostgresFixture.Database.DEFAULT_DATABASE;
import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

import java.util.UUID;
import java.util.function.Supplier;

import org.testcontainers.containers.PostgreSQLContainer;

public interface PostgresFixture {

    interface Database {

        Database DEFAULT_DATABASE = new DefaultDatabase();
        Database ROW_LEVEL_SECURITY_DATABASE = new RowLevelSecurityDatabase();

        String dbUser();

        String dbPassword();

        String dbName();

        String schema();


        class DefaultDatabase implements Database {
            @Override
            public String dbUser() {
                return "james";
            }

            @Override
            public String dbPassword() {
                return "secret1";
            }

            @Override
            public String dbName() {
                return "james";
            }

            @Override
            public String schema() {
                return "public";
            }
        }

        class RowLevelSecurityDatabase implements Database {
            @Override
            public String dbUser() {
                return "rlsuser";
            }

            @Override
            public String dbPassword() {
                return "secret1";
            }

            @Override
            public String dbName() {
                return "rlsdb";
            }

            @Override
            public String schema() {
                return "rlsschema";
            }
        }
    }

    String IMAGE = "postgres:16";
    Integer PORT = POSTGRESQL_PORT;
    Supplier<PostgreSQLContainer<?>> PG_CONTAINER = () -> new PostgreSQLContainer<>(IMAGE)
        .withDatabaseName(DEFAULT_DATABASE.dbName())
        .withUsername(DEFAULT_DATABASE.dbUser())
        .withPassword(DEFAULT_DATABASE.dbPassword())
        .withCreateContainerCmdModifier(cmd -> cmd.withName("james-postgres-test-" + UUID.randomUUID()));
}
