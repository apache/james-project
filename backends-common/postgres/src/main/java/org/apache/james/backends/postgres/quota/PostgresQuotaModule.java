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

package org.apache.james.backends.postgres.quota;

import static org.jooq.impl.DSL.name;
import static org.jooq.impl.SQLDataType.BIGINT;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresQuotaModule {
    interface PostgresQuotaCurrentValueTable {
        Table<Record> TABLE_NAME = DSL.table("quota_current_value");

        Field<String> IDENTIFIER = DSL.field("identifier", SQLDataType.VARCHAR.notNull());
        Field<String> COMPONENT = DSL.field("component", SQLDataType.VARCHAR.notNull());
        Field<String> TYPE = DSL.field("type", SQLDataType.VARCHAR.notNull());
        Field<Long> CURRENT_VALUE = DSL.field(name(TABLE_NAME.getName(), "current_value"), BIGINT.notNull());

        Name PRIMARY_KEY_CONSTRAINT_NAME = DSL.name("quota_current_value_primary_key");

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(IDENTIFIER)
                .column(COMPONENT)
                .column(TYPE)
                .column(CURRENT_VALUE)
                .constraint(DSL.constraint(PRIMARY_KEY_CONSTRAINT_NAME)
                    .primaryKey(IDENTIFIER, COMPONENT, TYPE))))
            .disableRowLevelSecurity()
            .build();
    }

    interface PostgresQuotaLimitTable {
        Table<Record> TABLE_NAME = DSL.table("quota_limit");

        Field<String> QUOTA_SCOPE = DSL.field("quota_scope", SQLDataType.VARCHAR.notNull());
        Field<String> IDENTIFIER = DSL.field("identifier", SQLDataType.VARCHAR.notNull());
        Field<String> QUOTA_COMPONENT = DSL.field("quota_component", SQLDataType.VARCHAR.notNull());
        Field<String> QUOTA_TYPE = DSL.field("quota_type", SQLDataType.VARCHAR.notNull());
        Field<Long> QUOTA_LIMIT = DSL.field("quota_limit", SQLDataType.BIGINT);

        Name PK_CONSTRAINT_NAME = DSL.name("quota_limit_pkey");

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(QUOTA_SCOPE)
                .column(IDENTIFIER)
                .column(QUOTA_COMPONENT)
                .column(QUOTA_TYPE)
                .column(QUOTA_LIMIT)
                .constraint(DSL.constraint(PK_CONSTRAINT_NAME).primaryKey(QUOTA_SCOPE, IDENTIFIER, QUOTA_COMPONENT, QUOTA_TYPE))))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresQuotaCurrentValueTable.TABLE)
        .addTable(PostgresQuotaLimitTable.TABLE)
        .build();
}