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

import static org.jooq.impl.SQLDataType.BIGINT;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresMailboxModule {
    interface PostgresMailboxTable {
        Table<Record> TABLE_NAME = DSL.table("mailbox");

        Field<UUID> MAILBOX_ID = DSL.field("mailbox_id", SQLDataType.UUID.notNull());
        Field<String> MAILBOX_NAME = DSL.field("mailbox_name", SQLDataType.VARCHAR(255).notNull());
        Field<Long> MAILBOX_UID_VALIDITY = DSL.field("mailbox_uid_validity", BIGINT.notNull());
        Field<String> USER_NAME = DSL.field("user_name", SQLDataType.VARCHAR(255));
        Field<String> MAILBOX_NAMESPACE = DSL.field("mailbox_namespace", SQLDataType.VARCHAR(255).notNull());
        Field<Long> MAILBOX_LAST_UID = DSL.field("mailbox_last_uid", BIGINT);
        Field<Long> MAILBOX_HIGHEST_MODSEQ = DSL.field("mailbox_highest_modseq", BIGINT);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(MAILBOX_ID, SQLDataType.UUID)
                .column(MAILBOX_NAME)
                .column(MAILBOX_UID_VALIDITY)
                .column(USER_NAME)
                .column(MAILBOX_NAMESPACE)
                .column(MAILBOX_LAST_UID)
                .column(MAILBOX_HIGHEST_MODSEQ)
                .constraint(DSL.primaryKey(MAILBOX_ID))
                .constraint(DSL.unique(MAILBOX_NAME, USER_NAME, MAILBOX_NAMESPACE))))
            .supportsRowLevelSecurity();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresMailboxTable.TABLE)
        .build();
}