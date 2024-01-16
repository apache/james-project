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

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresAttachmentModule {

    interface PostgresAttachmentTable {

        Table<Record> TABLE_NAME = DSL.table("attachment");
        Field<UUID> ID = DSL.field("id", SQLDataType.UUID.notNull());
        Field<String> BLOB_ID = DSL.field("blob_id", SQLDataType.VARCHAR);
        Field<String> TYPE = DSL.field("type", SQLDataType.VARCHAR);
        Field<UUID> MESSAGE_ID = DSL.field("message_id", SQLDataType.UUID);
        Field<Long> SIZE = DSL.field("size", SQLDataType.BIGINT);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(ID)
                .column(BLOB_ID)
                .column(TYPE)
                .column(MESSAGE_ID)
                .column(SIZE)
                .constraint(DSL.primaryKey(ID))))
            .supportsRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresAttachmentTable.TABLE)
        .build();
}
