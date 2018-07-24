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

import static com.datastax.driver.core.DataType.text;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraNotificationTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public class CassandraNotificationRegistryModule {
    public static final CassandraModule MODULE = CassandraModule.table(CassandraNotificationTable.TABLE_NAME)
        .statement(statement -> statement
            .ifNotExists()
            .addPartitionKey(CassandraNotificationTable.ACCOUNT_ID, text())
            .addClusteringColumn(CassandraNotificationTable.RECIPIENT_ID, text())
            .withOptions()
            .comment("Stores registry of vacation notification being sent.")
            .compactionOptions(SchemaBuilder.dateTieredStrategy())
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .build();
}
