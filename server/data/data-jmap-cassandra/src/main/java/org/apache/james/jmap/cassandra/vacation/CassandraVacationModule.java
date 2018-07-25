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

package org.apache.james.jmap.cassandra.vacation;

import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.text;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraVacationTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public class CassandraVacationModule {
    public static final CassandraModule MODULE = CassandraModule.table(CassandraVacationTable.TABLE_NAME)
        .statement(statement -> statement
            .addPartitionKey(CassandraVacationTable.ACCOUNT_ID, text())
            .addColumn(CassandraVacationTable.IS_ENABLED, cboolean())
            .addUDTColumn(CassandraVacationTable.FROM_DATE, SchemaBuilder.frozen(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
            .addUDTColumn(CassandraVacationTable.TO_DATE, SchemaBuilder.frozen(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
            .addColumn(CassandraVacationTable.TEXT, text())
            .addColumn(CassandraVacationTable.SUBJECT, text())
            .addColumn(CassandraVacationTable.HTML, text())
            .withOptions()
            .comment("Holds vacation definition. Allow one to automatically respond to emails with a custom message.")
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .build();
}
