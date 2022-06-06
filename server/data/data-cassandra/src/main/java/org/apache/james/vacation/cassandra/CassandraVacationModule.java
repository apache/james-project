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

package org.apache.james.vacation.cassandra;


import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.vacation.cassandra.tables.CassandraVacationTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraVacationModule {
    CassandraModule MODULE = CassandraModule.table(CassandraVacationTable.TABLE_NAME)
        .comment("Holds vacation definition. Allow one to automatically respond to emails with a custom message.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraVacationTable.ACCOUNT_ID, DataTypes.TEXT)
            .withColumn(CassandraVacationTable.IS_ENABLED, DataTypes.BOOLEAN)
            .withColumn(CassandraVacationTable.FROM_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
            .withColumn(CassandraVacationTable.TO_DATE, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
            .withColumn(CassandraVacationTable.TEXT, DataTypes.TEXT)
            .withColumn(CassandraVacationTable.SUBJECT, DataTypes.TEXT)
            .withColumn(CassandraVacationTable.HTML, DataTypes.TEXT))
        .build();
}
