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

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable;

public interface CassandraSubscriptionModule {

    int PER_USER_CACHED_SUBSCRIPTIONS = 100;

    CassandraModule MODULE = CassandraModule.table(CassandraSubscriptionTable.TABLE_NAME)
        .comment("Holds per user list of IMAP subscriptions")
        .options(options -> options
            .withCaching(true, rows(PER_USER_CACHED_SUBSCRIPTIONS)))
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraSubscriptionTable.USER, TEXT)
            .withClusteringColumn(CassandraSubscriptionTable.MAILBOX, TEXT))
        .build();
}
