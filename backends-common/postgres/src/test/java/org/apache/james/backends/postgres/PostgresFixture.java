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

import static org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT;

import java.util.UUID;
import java.util.function.Supplier;

import org.testcontainers.containers.PostgreSQLContainer;

public interface PostgresFixture {

    interface Database {
        String DB_USER = "james";
        String DB_PASSWORD = "secret1";
        String DB_NAME = "james";
        String SCHEMA = "public";
    }

    String IMAGE = "postgres:16.0";
    Integer PORT = POSTGRESQL_PORT;

    Supplier<PostgreSQLContainer<?>> PG_CONTAINER = () -> new PostgreSQLContainer<>(IMAGE)
        .withDatabaseName(Database.DB_NAME)
        .withUsername(Database.DB_USER)
        .withPassword(Database.DB_PASSWORD)
        .withCreateContainerCmdModifier(cmd -> cmd.withName("james-postgres-test-" + UUID.randomUUID()));
}
