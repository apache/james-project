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

package org.apache.james.vacation.postgres;

import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.TABLE;

import java.time.LocalDateTime;

import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresVacationModule {
    interface PostgresVacationResponseTable {
        Table<Record> TABLE_NAME = DSL.table("vacation_response");

        Field<String> ACCOUNT_ID = DSL.field("account_id", SQLDataType.VARCHAR.notNull());
        Field<Boolean> IS_ENABLED = DSL.field("is_enabled", SQLDataType.BOOLEAN);
        Field<LocalDateTime> FROM_DATE = DSL.field("from_date", PostgresCommons.DataTypes.TIMESTAMP);
        Field<LocalDateTime> TO_DATE = DSL.field("to_date", PostgresCommons.DataTypes.TIMESTAMP);
        Field<String> TEXT = DSL.field("text", SQLDataType.VARCHAR);
        Field<String> SUBJECT = DSL.field("subject", SQLDataType.VARCHAR);
        Field<String> HTML = DSL.field("html", SQLDataType.VARCHAR);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(ACCOUNT_ID)
                .column(IS_ENABLED)
                .column(FROM_DATE)
                .column(TO_DATE)
                .column(TEXT)
                .column(SUBJECT)
                .column(HTML)
                .constraint(DSL.primaryKey(ACCOUNT_ID))))
            .supportsRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .build();
}
