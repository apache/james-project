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

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresUserModule {
    interface PostgresUserTable {
        Table<Record> TABLE_NAME = DSL.table("users");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR(255).notNull());
        Field<String> HASHED_PASSWORD = DSL.field("hashed_password", SQLDataType.VARCHAR);
        Field<String> ALGORITHM = DSL.field("algorithm", SQLDataType.VARCHAR(100));
        Field<String[]> AUTHORIZED_USERS = DSL.field("authorized_users", SQLDataType.VARCHAR.getArrayDataType());
        Field<String[]> DELEGATED_USERS = DSL.field("delegated_users", SQLDataType.VARCHAR.getArrayDataType());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(HASHED_PASSWORD)
                .column(ALGORITHM)
                .column(AUTHORIZED_USERS)
                .column(DELEGATED_USERS)
                .constraint(DSL.primaryKey(USERNAME))))
            .disableRowLevelSecurity();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresUserTable.TABLE)
        .build();
}
