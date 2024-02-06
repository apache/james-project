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

package org.apache.james.jmap.postgres.change;

import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.INDEX;
import static org.apache.james.jmap.postgres.change.PostgresMailboxChangeModule.PostgresMailboxChangeTable.TABLE;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresMailboxChangeModule {
    interface PostgresMailboxChangeTable {
        Table<Record> TABLE_NAME = DSL.table("mailbox_change");

        Field<String> ACCOUNT_ID = DSL.field("account_id", SQLDataType.VARCHAR.notNull());
        Field<UUID> STATE = DSL.field("state", SQLDataType.UUID.notNull());
        Field<OffsetDateTime> DATE = DSL.field("date", SQLDataType.TIMESTAMPWITHTIMEZONE.notNull());
        Field<Boolean> IS_SHARED = DSL.field("is_shared", SQLDataType.BOOLEAN.notNull());
        Field<Boolean> IS_COUNT_CHANGE = DSL.field("is_count_change", SQLDataType.BOOLEAN.notNull());
        Field<UUID[]> CREATED = DSL.field("created", SQLDataType.UUID.getArrayDataType().notNull());
        Field<UUID[]> UPDATED = DSL.field("updated", SQLDataType.UUID.getArrayDataType().notNull());
        Field<UUID[]> DESTROYED = DSL.field("destroyed", SQLDataType.UUID.getArrayDataType().notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(ACCOUNT_ID)
                .column(STATE)
                .column(DATE)
                .column(IS_SHARED)
                .column(IS_COUNT_CHANGE)
                .column(CREATED)
                .column(UPDATED)
                .column(DESTROYED)
                .constraint(DSL.primaryKey(ACCOUNT_ID, STATE))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex INDEX = PostgresIndex.name("index_mailbox_change_date")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, DATE));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(INDEX)
        .build();
}
