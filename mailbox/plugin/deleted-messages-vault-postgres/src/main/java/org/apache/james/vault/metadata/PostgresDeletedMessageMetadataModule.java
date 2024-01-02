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

package org.apache.james.vault.metadata;

import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.OWNER_MESSAGE_ID_INDEX;
import static org.apache.james.vault.metadata.PostgresDeletedMessageMetadataModule.DeletedMessageMetadataTable.TABLE;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresDeletedMessageMetadataModule {
    interface DeletedMessageMetadataTable {
        Table<Record> TABLE_NAME = DSL.table("deleted_messages_metadata");

        Field<String> BUCKET_NAME = DSL.field("bucket_name", SQLDataType.VARCHAR.notNull());
        Field<String> OWNER = DSL.field("owner", SQLDataType.VARCHAR.notNull());
        Field<String> MESSAGE_ID = DSL.field("messageId", SQLDataType.VARCHAR.notNull());
        Field<String> BLOB_ID = DSL.field("blob_id", SQLDataType.VARCHAR.notNull());
        Field<JSONB> METADATA = DSL.field("metadata", SQLDataType.JSONB.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(BUCKET_NAME)
                .column(OWNER)
                .column(MESSAGE_ID)
                .column(BLOB_ID)
                .column(METADATA)
                .primaryKey(BUCKET_NAME, OWNER, MESSAGE_ID)))
            .disableRowLevelSecurity()
            .build();

        PostgresIndex OWNER_MESSAGE_ID_INDEX = PostgresIndex.name("owner_messageId_index")
            .createIndexStep((dsl, indexName) -> dsl.createUniqueIndexIfNotExists(indexName)
                .on(TABLE_NAME, OWNER, MESSAGE_ID));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(OWNER_MESSAGE_ID_INDEX)
        .build();
}
