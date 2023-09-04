/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.oss.driver.api.core.type.DataTypes.INT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TEXT;
import static com.datastax.oss.driver.api.core.type.DataTypes.TIMEUUID;
import static com.datastax.oss.driver.api.core.type.DataTypes.frozenSetOf;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadLookupTable.MIME_MESSAGE_IDS;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.BASE_SUBJECT;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.MIME_MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraThreadTable.USERNAME;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.mailbox.cassandra.table.CassandraThreadLookupTable;

public interface CassandraThreadModule {
    CassandraModule MODULE = CassandraModule.builder()
        .table(TABLE_NAME)
        .comment("Related data needed for guessing threadId algorithm")
        .statement(statement -> types -> statement
            .withPartitionKey(USERNAME, TEXT)
            .withPartitionKey(MIME_MESSAGE_ID, INT)
            .withClusteringColumn(MESSAGE_ID, TIMEUUID)
            .withColumn(THREAD_ID, TIMEUUID)
            .withColumn(BASE_SUBJECT, INT))
        .table(CassandraThreadLookupTable.TABLE_NAME)
        .comment("Thread table lookup by messageId, using for deletion thread data")
        .statement(statement -> types -> statement
            .withPartitionKey(MESSAGE_ID, TIMEUUID)
            .withColumn(USERNAME, TEXT)
            .withColumn(MIME_MESSAGE_IDS, frozenSetOf(INT)))
        .build();

}
