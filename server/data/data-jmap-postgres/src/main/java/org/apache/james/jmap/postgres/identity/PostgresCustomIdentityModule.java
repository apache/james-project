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

package org.apache.james.jmap.postgres.identity;

import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.TABLE;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.USERNAME_INDEX;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresCustomIdentityModule {
    interface PostgresCustomIdentityTable {
        Table<Record> TABLE_NAME = DSL.table("custom_identity");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR(255).notNull());
        Field<UUID> ID = DSL.field("id", SQLDataType.UUID.notNull());
        Field<String> NAME = DSL.field("name", SQLDataType.VARCHAR(255).notNull());
        Field<String> EMAIL = DSL.field("email", SQLDataType.VARCHAR(255).notNull());
        Field<JSON> REPLY_TO = DSL.field("reply_to", SQLDataType.JSON.notNull());
        Field<JSON> BCC = DSL.field("bcc", SQLDataType.JSON.notNull());
        Field<String> TEXT_SIGNATURE = DSL.field("text_signature", SQLDataType.VARCHAR(255).notNull());
        Field<String> HTML_SIGNATURE = DSL.field("html_signature", SQLDataType.VARCHAR(255).notNull());
        Field<Integer> SORT_ORDER = DSL.field("sort_order", SQLDataType.INTEGER.notNull());
        Field<Boolean> MAY_DELETE = DSL.field("may_delete", SQLDataType.BOOLEAN.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(ID)
                .column(NAME)
                .column(EMAIL)
                .column(REPLY_TO)
                .column(BCC)
                .column(TEXT_SIGNATURE)
                .column(HTML_SIGNATURE)
                .column(SORT_ORDER)
                .column(MAY_DELETE)
                .constraint(DSL.primaryKey(USERNAME, ID))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex USERNAME_INDEX = PostgresIndex.name("custom_identity_username_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USERNAME));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(USERNAME_INDEX)
        .build();
}
