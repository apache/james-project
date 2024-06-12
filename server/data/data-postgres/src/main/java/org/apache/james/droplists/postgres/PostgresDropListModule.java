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

package org.apache.james.droplists.postgres;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresDropListModule {
    interface PostgresDropListsTable {
        Table<Record> TABLE_NAME = DSL.table("droplist");

        Field<UUID> DROPLIST_ID = DSL.field("droplist_id", SQLDataType.UUID.notNull());
        Field<String> OWNER_SCOPE = DSL.field("owner_scope", SQLDataType.VARCHAR);
        Field<String> OWNER = DSL.field("owner", SQLDataType.VARCHAR);
        Field<String> DENIED_ENTITY_TYPE = DSL.field("denied_entity_type", SQLDataType.VARCHAR);
        Field<String> DENIED_ENTITY = DSL.field("denied_entity", SQLDataType.VARCHAR);

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(DROPLIST_ID)
                .column(OWNER_SCOPE)
                .column(OWNER)
                .column(DENIED_ENTITY_TYPE)
                .column(DENIED_ENTITY)
                .constraint(DSL.primaryKey(DROPLIST_ID))))
            .disableRowLevelSecurity()
            .build();

        PostgresIndex IDX_OWNER_SCOPE_OWNER = PostgresIndex.name("idx_owner_scope_owner")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, OWNER_SCOPE, OWNER));

        PostgresIndex IDX_OWNER_SCOPE_OWNER_DENIED_ENTITY = PostgresIndex.name("idx_owner_scope_owner_denied_entity")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, OWNER_SCOPE, OWNER, DENIED_ENTITY));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresDropListsTable.TABLE)
        .addIndex(PostgresDropListsTable.IDX_OWNER_SCOPE_OWNER)
        .addIndex(PostgresDropListsTable.IDX_OWNER_SCOPE_OWNER_DENIED_ENTITY)
        .build();
}
