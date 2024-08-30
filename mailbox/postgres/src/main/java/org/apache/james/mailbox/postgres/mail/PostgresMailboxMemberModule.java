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

package org.apache.james.mailbox.postgres.mail;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresMailboxMemberModule {
    interface PostgresMailboxMemberTable {
        Table<Record> TABLE_NAME = DSL.table("mailbox_member");

        Field<String> USER_NAME = DSL.field("user_name", SQLDataType.VARCHAR(255));
        Field<UUID> MAILBOX_ID = DSL.field("mailbox_id", SQLDataType.UUID.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USER_NAME)
                .column(MAILBOX_ID)
                .constraint(DSL.primaryKey(USER_NAME, MAILBOX_ID))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex MAILBOX_MEMBER_USERNAME_INDEX = PostgresIndex.name("mailbox_member_username_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USER_NAME));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresMailboxMemberTable.TABLE)
        .addIndex(PostgresMailboxMemberTable.MAILBOX_MEMBER_USERNAME_INDEX)
        .build();
}
