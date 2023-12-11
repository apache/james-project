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

package org.apache.james.sieve.postgres;

import java.time.OffsetDateTime;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresSieveModule {
    interface PostgresSieveScriptTable {
        Table<Record> TABLE_NAME = DSL.table("sieve_scripts");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR(255).notNull());
        Field<String> SCRIPT_NAME = DSL.field("script_name", SQLDataType.VARCHAR.notNull());
        Field<Long> SCRIPT_SIZE = DSL.field("script_size", SQLDataType.BIGINT.notNull());
        Field<String> SCRIPT_CONTENT = DSL.field("script_content", SQLDataType.VARCHAR.notNull());
        Field<Boolean> IS_ACTIVE = DSL.field("is_active", SQLDataType.BOOLEAN.notNull());
        Field<OffsetDateTime> ACTIVATION_DATE_TIME = DSL.field("activation_date_time", SQLDataType.OFFSETDATETIME);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(SCRIPT_NAME)
                .column(SCRIPT_SIZE)
                .column(SCRIPT_CONTENT)
                .column(IS_ACTIVE)
                .column(ACTIVATION_DATE_TIME)
                .primaryKey(USERNAME, SCRIPT_NAME)))
            .disableRowLevelSecurity();

        PostgresIndex MAXIMUM_ONE_ACTIVE_SCRIPT_PER_USER_UNIQUE_INDEX = PostgresIndex.name("maximum_one_active_script_per_user")
            .createIndexStep(((dsl, indexName) -> dsl.createUniqueIndexIfNotExists(indexName)
                .on(TABLE_NAME, USERNAME)
                .where(IS_ACTIVE)));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresSieveScriptTable.TABLE)
        .addIndex(PostgresSieveScriptTable.MAXIMUM_ONE_ACTIVE_SCRIPT_PER_USER_UNIQUE_INDEX)
        .build();
}
