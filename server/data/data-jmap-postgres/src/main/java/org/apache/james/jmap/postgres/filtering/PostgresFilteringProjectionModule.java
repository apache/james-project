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

package org.apache.james.jmap.postgres.filtering;

import static org.apache.james.jmap.postgres.filtering.PostgresFilteringProjectionModule.PostgresFilteringProjectionTable.TABLE;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresFilteringProjectionModule {
    interface PostgresFilteringProjectionTable {
        Table<Record> TABLE_NAME = DSL.table("filters_projection");

        Field<String> AGGREGATE_ID = DSL.field("aggregate_id", SQLDataType.VARCHAR.notNull());
        Field<Integer> EVENT_ID = DSL.field("event_id", SQLDataType.INTEGER.notNull());
        Field<JSON> RULES = DSL.field("rules", SQLDataType.JSON.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(AGGREGATE_ID)
                .column(EVENT_ID)
                .column(RULES)
                .constraint(DSL.primaryKey(AGGREGATE_ID))))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .build();
}
