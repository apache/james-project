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

import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.apache.james.mailbox.postgres.mail.PostgresCommons.DataTypes;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.types.Hstore;

public interface PostgresMessageModule {

    Field<UUID> MESSAGE_ID = DSL.field("message_id", SQLDataType.UUID.notNull());

    interface MessageTable {
        Table<Record> TABLE_NAME = DSL.table("message");
        Field<UUID> MESSAGE_ID = PostgresMessageModule.MESSAGE_ID;
        Field<String> BLOB_ID = DSL.field("blob_id", SQLDataType.VARCHAR(200).notNull());
        Field<String> MIME_TYPE = DSL.field("mime_type", SQLDataType.VARCHAR(200));
        Field<String> MIME_SUBTYPE = DSL.field("mime_subtype", SQLDataType.VARCHAR(200));
        Field<LocalDateTime> INTERNAL_DATE = DSL.field("internal_date", DataTypes.TIMESTAMP);
        Field<Long> SIZE = DSL.field("size", SQLDataType.BIGINT.notNull());
        Field<Integer> BODY_START_OCTET = DSL.field("body_start_octet", SQLDataType.INTEGER.notNull());
        Field<byte[]> HEADER_CONTENT = DSL.field("header_content", SQLDataType.BLOB.notNull());
        Field<Integer> TEXTUAL_LINE_COUNT = DSL.field("textual_line_count", SQLDataType.INTEGER);
        Field<Hstore> CONTENT_TYPE_PARAMETERS = DSL.field("content_type_parameters", DataTypes.HSTORE);
        Field<Hstore> CONTENT_DISPOSITION_PARAMETERS = DSL.field("content_disposition_parameters", DataTypes.HSTORE);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(MESSAGE_ID)
                .column(BLOB_ID)
                .column(MIME_TYPE)
                .column(MIME_SUBTYPE)
                .column(INTERNAL_DATE)
                .column(SIZE)
                .column(BODY_START_OCTET)
                .column(HEADER_CONTENT)
                .column(TEXTUAL_LINE_COUNT)
                .column(CONTENT_TYPE_PARAMETERS)
                .column(CONTENT_DISPOSITION_PARAMETERS)
                .constraint(DSL.primaryKey(MESSAGE_ID))
                .comment("Holds the metadata of a mail")))
            .supportsRowLevelSecurity();
    }

    interface MessageToMailboxTable {
        Table<Record> TABLE_NAME = DSL.table("message_mailbox");
        Field<UUID> MAILBOX_ID = DSL.field("mailbox_id", SQLDataType.UUID.notNull());
        Field<Long> MESSAGE_UID = DSL.field("message_uid", SQLDataType.BIGINT.notNull());
        Field<UUID> MESSAGE_ID = PostgresMessageModule.MESSAGE_ID;
        Field<String> THREAD_ID = DSL.field("thread_id", SQLDataType.NVARCHAR(255));
        Field<Boolean> IS_DELETED = DSL.field("is_deleted", SQLDataType.BOOLEAN.nullable(false)
            .defaultValue(DSL.field("false", SQLDataType.BOOLEAN)));
        Field<Boolean> IS_ANSWERED = DSL.field("is_answered", SQLDataType.BOOLEAN.nullable(false));
        Field<Boolean> IS_DRAFT = DSL.field("is_draft", SQLDataType.BOOLEAN.nullable(false));
        Field<Boolean> IS_FLAGGED = DSL.field("is_flagged", SQLDataType.BOOLEAN.nullable(false));
        Field<Boolean> IS_RECENT = DSL.field("is_recent", SQLDataType.BOOLEAN.nullable(false));
        Field<Boolean> IS_SEEN = DSL.field("is_seen", SQLDataType.BOOLEAN.nullable(false));
        Field<Boolean> IS_USER = DSL.field("is_user", SQLDataType.BOOLEAN.nullable(false));
        Field<String[]> USER_FLAGS = DSL.field("user_flags", DataTypes.STRING_ARRAY);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(MAILBOX_ID)
                .column(MESSAGE_UID)
                .column(MESSAGE_ID)
                .column(THREAD_ID)
                .column(IS_DELETED)
                .column(IS_ANSWERED)
                .column(IS_DRAFT)
                .column(IS_FLAGGED)
                .column(IS_RECENT)
                .column(IS_SEEN)
                .column(IS_USER)
                .column(USER_FLAGS)
                .constraint(DSL.primaryKey(MAILBOX_ID, MESSAGE_UID))
                .comment("Holds mailbox and flags for each message")))
            .supportsRowLevelSecurity();

        PostgresIndex MESSAGE_ID_INDEX = PostgresIndex.name("message_mailbox_message_id_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, MESSAGE_ID));

        PostgresIndex MAILBOX_ID_MESSAGE_UID_INDEX = PostgresIndex.name("mailbox_id_mail_uid_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, MAILBOX_ID, MESSAGE_UID.asc()));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(MessageTable.TABLE)
        .addTable(MessageToMailboxTable.TABLE)
        .addIndex(MessageToMailboxTable.MESSAGE_ID_INDEX)
        .addIndex(MessageToMailboxTable.MAILBOX_ID_MESSAGE_UID_INDEX)
        .build();

}
