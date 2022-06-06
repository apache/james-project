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

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.DATE_LOOKUP_TABLE;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MAILBOX_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.SENT_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_RECEIVED_AT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraEmailQueryViewTable.TABLE_NAME_SENT_AT;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraEmailQueryViewModule {
    CassandraModule MODULE = CassandraModule.table(TABLE_NAME_SENT_AT)
        .comment("Storing the JMAP projections for list of emails within a mailbox to not rely on ElasticSearch for basic Email/query (sorts sentAt).")
        .options(options -> options
            .withClusteringOrder(SENT_AT, DESC)
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(MAILBOX_ID, DataTypes.UUID)
            .withClusteringColumn(SENT_AT, DataTypes.TIMESTAMP)
            .withClusteringColumn(MESSAGE_ID, DataTypes.UUID))

        .table(TABLE_NAME_RECEIVED_AT)
        .comment("Storing the JMAP projections for list of emails within a mailbox to not rely on ElasticSearch for basic Email/query (sorts and filter on receivedAt).")
        .options(options -> options
            .withClusteringOrder(RECEIVED_AT, DESC)
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(MAILBOX_ID, DataTypes.UUID)
            .withClusteringColumn(RECEIVED_AT, DataTypes.TIMESTAMP)
            .withClusteringColumn(MESSAGE_ID, DataTypes.UUID)
            .withColumn(SENT_AT, DataTypes.TIMESTAMP))

        .table(DATE_LOOKUP_TABLE)
        .comment("Given a MailboxId+MessageId lookup the dates of a message to delete it.")
        .options(options -> options
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(MAILBOX_ID, DataTypes.UUID)
            .withClusteringColumn(MESSAGE_ID, DataTypes.UUID)
            .withColumn(SENT_AT, DataTypes.TIMESTAMP)
            .withColumn(RECEIVED_AT, DataTypes.TIMESTAMP))

        .build();
}
