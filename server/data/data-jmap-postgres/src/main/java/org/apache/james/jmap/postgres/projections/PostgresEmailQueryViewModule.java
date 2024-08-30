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

import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MAILBOX_ID_INDEX;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MAILBOX_ID_RECEIVED_AT_INDEX;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.MAILBOX_ID_SENT_AT_INDEX;
import static org.apache.james.jmap.postgres.projections.PostgresEmailQueryViewModule.PostgresEmailQueryViewTable.TABLE;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresEmailQueryViewModule {
    interface PostgresEmailQueryViewTable {
        Table<Record> TABLE_NAME = DSL.table("email_query_view");

        Field<UUID> MAILBOX_ID = DSL.field("mailbox_id", SQLDataType.UUID.notNull());
        Field<UUID> MESSAGE_ID = PostgresMessageModule.MESSAGE_ID;
        Field<OffsetDateTime> RECEIVED_AT = DSL.field("received_at", SQLDataType.TIMESTAMPWITHTIMEZONE.notNull());
        Field<OffsetDateTime> SENT_AT = DSL.field("sent_at", SQLDataType.TIMESTAMPWITHTIMEZONE.notNull());

        Name PK_CONSTRAINT_NAME = DSL.name("email_query_view_pkey");

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(MAILBOX_ID)
                .column(MESSAGE_ID)
                .column(RECEIVED_AT)
                .column(SENT_AT)
                .constraint(DSL.constraint(PK_CONSTRAINT_NAME).primaryKey(MAILBOX_ID, MESSAGE_ID))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex MAILBOX_ID_INDEX = PostgresIndex.name("email_query_view_mailbox_id_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, MAILBOX_ID));

        PostgresIndex MAILBOX_ID_RECEIVED_AT_INDEX = PostgresIndex.name("email_query_view_mailbox_id__received_at_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, MAILBOX_ID, RECEIVED_AT));

        PostgresIndex MAILBOX_ID_SENT_AT_INDEX = PostgresIndex.name("email_query_view_mailbox_id_sent_at_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, MAILBOX_ID, SENT_AT));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(MAILBOX_ID_INDEX)
        .addIndex(MAILBOX_ID_RECEIVED_AT_INDEX)
        .addIndex(MAILBOX_ID_SENT_AT_INDEX)
        .build();
}