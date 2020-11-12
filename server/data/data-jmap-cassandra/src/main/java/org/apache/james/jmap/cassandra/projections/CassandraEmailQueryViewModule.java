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

package org.apache.james.jmap.cassandra.projections;

import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.uuid;
import static com.datastax.driver.core.schemabuilder.SchemaBuilder.Direction.DESC;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.DATE_LOOKUP_TABLE;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_SENT_AT;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraEmailQueryViewModule {
    CassandraModule MODULE = CassandraModule.table(TABLE_NAME_SENT_AT)
        .comment("Storing the JMAP projections for list of emails within a mailbox to not rely on ElasticSearch for basic Email/query (sorts sentAt).")
        .options(options -> options
            .clusteringOrder(SENT_AT, DESC)
            .caching(SchemaBuilder.KeyCaching.ALL, SchemaBuilder.rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(MAILBOX_ID, uuid())
            .addClusteringColumn(SENT_AT, timestamp())
            .addClusteringColumn(MESSAGE_ID, uuid()))

        .table(TABLE_NAME_RECEIVED_AT)
        .comment("Storing the JMAP projections for list of emails within a mailbox to not rely on ElasticSearch for basic Email/query (sorts and filter on receivedAt).")
        .options(options -> options
            .clusteringOrder(RECEIVED_AT, DESC)
            .caching(SchemaBuilder.KeyCaching.ALL, SchemaBuilder.rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(MAILBOX_ID, uuid())
            .addClusteringColumn(RECEIVED_AT, timestamp())
            .addClusteringColumn(MESSAGE_ID, uuid())
            .addColumn(SENT_AT, timestamp()))

        .table(DATE_LOOKUP_TABLE)
        .comment("Given a MailboxId+MessageId lookup the dates of a message to delete it.")
        .options(options -> options
            .caching(SchemaBuilder.KeyCaching.ALL, SchemaBuilder.rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(MAILBOX_ID, uuid())
            .addClusteringColumn(MESSAGE_ID, uuid())
            .addColumn(SENT_AT, timestamp())
            .addColumn(RECEIVED_AT, timestamp()))

        .build();
}
