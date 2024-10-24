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

package org.apache.james.events;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresEventDeadLettersModule {
    interface PostgresEventDeadLettersTable {
        Table<Record> TABLE_NAME = DSL.table("event_dead_letters");

        Field<UUID> INSERTION_ID = DSL.field("insertion_id", SQLDataType.UUID.notNull());
        Field<String> GROUP = DSL.field("\"group\"", SQLDataType.VARCHAR.notNull());
        Field<String> EVENT = DSL.field("event", SQLDataType.VARCHAR.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(INSERTION_ID)
                .column(GROUP)
                .column(EVENT)
                .primaryKey(INSERTION_ID)))
            .disableRowLevelSecurity()
            .build();

        PostgresIndex GROUP_INDEX = PostgresIndex.name("event_dead_letters_group_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, GROUP));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresEventDeadLettersTable.TABLE)
        .addIndex(PostgresEventDeadLettersTable.GROUP_INDEX)
        .build();
}
