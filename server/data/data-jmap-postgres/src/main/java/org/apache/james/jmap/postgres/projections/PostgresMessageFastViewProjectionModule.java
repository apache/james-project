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

package org.apache.james.jmap.postgres.projections;

import static org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionModule.MessageFastViewProjectionTable.TABLE;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresMessageFastViewProjectionModule {
    interface MessageFastViewProjectionTable {
        Table<Record> TABLE_NAME = DSL.table("message_fast_view_projection");

        Field<UUID> MESSAGE_ID = DSL.field("messageId", SQLDataType.UUID.notNull());
        Field<String> PREVIEW = DSL.field("preview", SQLDataType.VARCHAR.notNull());
        Field<Boolean> HAS_ATTACHMENT = DSL.field("has_attachment", SQLDataType.BOOLEAN.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(MESSAGE_ID)
                .column(PREVIEW)
                .column(HAS_ATTACHMENT)
                .primaryKey(MESSAGE_ID)
                .comment("Storing the JMAP projections for MessageFastView, an aggregation of JMAP properties expected to be fast to fetch.")))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .build();
}
