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

package org.apache.james.blob.postgres;

import static org.jooq.impl.SQLDataType.BLOB;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresBlobStorageModule {
    interface PostgresBlobStorageTable {
        Table<Record> TABLE_NAME = DSL.table("blob_storage");

        Field<String> BUCKET_NAME = DSL.field("bucket_name", SQLDataType.VARCHAR(200).notNull());
        Field<String> BLOB_ID = DSL.field("blob_id", SQLDataType.VARCHAR(200).notNull());
        Field<byte[]> DATA = DSL.field("data", BLOB.notNull());
        Field<Integer> SIZE = DSL.field("size", SQLDataType.INTEGER.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(BUCKET_NAME)
                .column(BLOB_ID)
                .column(DATA)
                .column(SIZE)
                .constraint(DSL.primaryKey(BUCKET_NAME, BLOB_ID))))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresBlobStorageTable.TABLE)
        .build();
}
