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

package org.apache.james.rrt.postgres;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresRecipientRewriteTableModule {
    interface PostgresRecipientRewriteTableTable {
        Table<Record> TABLE_NAME = DSL.table("rrt");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR(255).notNull());
        Field<String> DOMAIN_NAME = DSL.field("domain_name", SQLDataType.VARCHAR(255).notNull());
        Field<String> TARGET_ADDRESS = DSL.field("target_address", SQLDataType.VARCHAR(255).notNull());

        Name PK_CONSTRAINT_NAME = DSL.name("rrt_pkey");

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(DOMAIN_NAME)
                .column(TARGET_ADDRESS)
                .constraint(DSL.constraint(PK_CONSTRAINT_NAME).primaryKey(USERNAME, DOMAIN_NAME, TARGET_ADDRESS))))
            .supportsRowLevelSecurity();

        PostgresIndex INDEX = PostgresIndex.name("idx_rrt_target_address")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndex(indexName)
                .on(TABLE_NAME, TARGET_ADDRESS));
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(PostgresRecipientRewriteTableTable.TABLE)
        .addIndex(PostgresRecipientRewriteTableTable.INDEX)
        .build();
}
