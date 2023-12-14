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

package org.apache.james.mailrepository.postgres;

import static org.apache.james.backends.postgres.PostgresCommons.DataTypes.HSTORE;

import java.time.LocalDateTime;

import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.types.Hstore;

public interface PostgresMailRepositoryModule {
    interface PostgresMailRepositoryUrlTable {
        Table<Record> TABLE_NAME = DSL.table("mail_repository_url");

        Field<String> URL = DSL.field("url", SQLDataType.VARCHAR(255).notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(URL)
                .primaryKey(URL)))
            .disableRowLevelSecurity();
    }

    interface PostgresMailRepositoryContentTable {
        Table<Record> TABLE_NAME = DSL.table("mail_repository_content");

        Field<String> URL = DSL.field("url", SQLDataType.VARCHAR(255).notNull());
        Field<String> KEY = DSL.field("key", SQLDataType.VARCHAR.notNull());
        Field<String> STATE = DSL.field("state", SQLDataType.VARCHAR.notNull());
        Field<String> ERROR = DSL.field("error", SQLDataType.VARCHAR);
        Field<String> HEADER_BLOB_ID = DSL.field("header_blob_id", SQLDataType.VARCHAR.notNull());
        Field<String> BODY_BLOB_ID = DSL.field("body_blob_id", SQLDataType.VARCHAR.notNull());
        Field<Hstore> ATTRIBUTES = DSL.field("attributes", HSTORE.notNull());
        Field<String> SENDER = DSL.field("sender", SQLDataType.VARCHAR);
        Field<String[]> RECIPIENTS = DSL.field("recipients", SQLDataType.VARCHAR.getArrayDataType().notNull());
        Field<String> REMOTE_HOST = DSL.field("remote_host", SQLDataType.VARCHAR.notNull());
        Field<String> REMOTE_ADDRESS = DSL.field("remote_address", SQLDataType.VARCHAR.notNull());
        Field<LocalDateTime> LAST_UPDATED = DSL.field("last_updated", PostgresCommons.DataTypes.TIMESTAMP.notNull());
        Field<Hstore> PER_RECIPIENT_SPECIFIC_HEADERS = DSL.field("per_recipient_specific_headers", HSTORE.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(URL)
                .column(KEY)
                .column(STATE)
                .column(ERROR)
                .column(HEADER_BLOB_ID)
                .column(BODY_BLOB_ID)
                .column(ATTRIBUTES)
                .column(SENDER)
                .column(RECIPIENTS)
                .column(REMOTE_HOST)
                .column(REMOTE_ADDRESS)
                .column(LAST_UPDATED)
                .column(PER_RECIPIENT_SPECIFIC_HEADERS)
                .primaryKey(URL, KEY)))
            .disableRowLevelSecurity();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresMailRepositoryUrlTable.TABLE)
        .addTable(PostgresMailRepositoryContentTable.TABLE)
        .build();
}
