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

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.vacation.cassandra.tables.CassandraNotificationTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraNotificationRegistryModule {
    CassandraModule MODULE = CassandraModule.table(CassandraNotificationTable.TABLE_NAME)
        .comment("Stores registry of vacation notification being sent.")
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraNotificationTable.ACCOUNT_ID, DataTypes.TEXT)
            .withClusteringColumn(CassandraNotificationTable.RECIPIENT_ID, DataTypes.TEXT))
        .build();
}
