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

package org.apache.james.mailbox.postgres.mail.dao;

import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.MESSAGE_ID_INDEX;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresThreadModule.PostgresThreadTable.TABLE;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresThreadModule {
    interface PostgresThreadTable {
        Table<Record> TABLE_NAME = DSL.table("thread");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR(255).notNull());
        Field<Integer> HASH_MIME_MESSAGE_ID = DSL.field("hash_mime_message_id", SQLDataType.INTEGER.notNull());
        Field<UUID> MESSAGE_ID = DSL.field("message_id", SQLDataType.UUID.notNull());
        Field<UUID> THREAD_ID = DSL.field("thread_id", SQLDataType.UUID.notNull());
        Field<Integer> HASH_BASE_SUBJECT = DSL.field("hash_base_subject", SQLDataType.INTEGER);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(HASH_MIME_MESSAGE_ID)
                .column(MESSAGE_ID)
                .column(THREAD_ID)
                .column(HASH_BASE_SUBJECT)
                .constraint(DSL.primaryKey(USERNAME, HASH_MIME_MESSAGE_ID, MESSAGE_ID))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex MESSAGE_ID_INDEX = PostgresIndex.name("thread_message_id_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, MESSAGE_ID));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(MESSAGE_ID_INDEX)
        .build();
}
