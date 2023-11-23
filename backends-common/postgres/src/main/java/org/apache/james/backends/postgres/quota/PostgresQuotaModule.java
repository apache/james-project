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

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(IDENTIFIER)
                .column(COMPONENT)
                .column(TYPE)
                .column(CURRENT_VALUE)
                .constraint(DSL.primaryKey(IDENTIFIER, COMPONENT, TYPE))))
            .disableRowLevelSecurity();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresQuotaCurrentValueTable.TABLE)
        .build();
}
