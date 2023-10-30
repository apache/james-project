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

package org.apache.james.jmap.postgres.upload;


import static org.apache.james.jmap.postgres.upload.PostgresUploadModule.PostgresUploadTable.TABLE;

import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresUploadModule {
    interface PostgresUploadTable {

        Table<Record> TABLE_NAME = DSL.table("uploads");

        Field<UUID> ID = DSL.field("id", SQLDataType.UUID.notNull());
        Field<String> CONTENT_TYPE = DSL.field("content_type", SQLDataType.VARCHAR);
        Field<Long> SIZE = DSL.field("size", SQLDataType.BIGINT.notNull());
        Field<String> BLOB_ID = DSL.field("blob_id", SQLDataType.VARCHAR.notNull());
        Field<String> USER_NAME = DSL.field("user_name", SQLDataType.VARCHAR.notNull());
        Field<LocalDateTime> UPLOAD_DATE = DSL.field("upload_date", PostgresCommons.DataTypes.TIMESTAMP.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(ID)
                .column(CONTENT_TYPE)
                .column(SIZE)
                .column(BLOB_ID)
                .column(USER_NAME)
                .column(UPLOAD_DATE)
                .primaryKey(ID)))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex USER_NAME_INDEX = PostgresIndex.name("uploads_user_name_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USER_NAME));
        PostgresIndex ID_USERNAME_INDEX = PostgresIndex.name("uploads_id_user_name_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, ID, USER_NAME));
        PostgresIndex UPLOAD_DATE_INDEX = PostgresIndex.name("uploads_upload_date_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, UPLOAD_DATE));

    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .addIndex(PostgresUploadTable.USER_NAME_INDEX, PostgresUploadTable.ID_USERNAME_INDEX, PostgresUploadTable.UPLOAD_DATE_INDEX)
        .build();
}
