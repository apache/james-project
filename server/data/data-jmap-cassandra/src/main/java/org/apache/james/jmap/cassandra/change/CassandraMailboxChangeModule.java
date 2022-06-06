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

package org.apache.james.jmap.cassandra.change;

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.ASC;
import static com.datastax.oss.driver.api.core.type.DataTypes.BOOLEAN;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenSetOf;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;
import static org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule.ZONED_DATE_TIME;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.ACCOUNT_ID;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.CREATED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.DATE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.DESTROYED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.IS_COUNT_CHANGE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.IS_DELEGATED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.STATE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.TABLE_NAME;
import static org.apache.james.jmap.cassandra.change.tables.CassandraMailboxChangeTable.UPDATED;

import org.apache.james.backends.cassandra.components.CassandraModule;

public interface CassandraMailboxChangeModule {
    CassandraModule MODULE = CassandraModule.table(TABLE_NAME)
        .comment("Holds MailboxChange definition. Used to manage Mailbox state in JMAP.")
        .options(options -> options
            .withClusteringOrder(STATE, ASC)
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(ACCOUNT_ID, TEXT)
            .withClusteringColumn(STATE, TIMEUUID)
            .withColumn(DATE, types.getDefinedUserType(ZONED_DATE_TIME))
            .withColumn(IS_DELEGATED, BOOLEAN)
            .withColumn(IS_COUNT_CHANGE, BOOLEAN)
            .withColumn(CREATED, frozenSetOf(TIMEUUID))
            .withColumn(UPDATED, frozenSetOf(TIMEUUID))
            .withColumn(DESTROYED, frozenSetOf(TIMEUUID)))
        .build();
}
