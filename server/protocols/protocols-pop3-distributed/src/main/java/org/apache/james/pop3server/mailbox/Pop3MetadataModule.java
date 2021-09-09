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

package org.apache.james.pop3server.mailbox;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.timeuuid;
import static com.datastax.driver.core.schemabuilder.SchemaBuilder.Direction.ASC;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface Pop3MetadataModule {
    String TABLE_NAME = "pop3metadata";
    String MAILBOX_ID = "mailboxId";
    String MESSAGE_ID = "messageId";
    String SIZE = "size";

    CassandraModule MODULE = CassandraModule.table(TABLE_NAME)
        .comment("Store metadata to answer efficiently the STAT queries based on the messageId. No further reads required on other tables.")
        .options(options -> options
            .clusteringOrder(MESSAGE_ID, ASC)
            .caching(SchemaBuilder.KeyCaching.ALL, SchemaBuilder.rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(MAILBOX_ID, timeuuid())
            .addClusteringColumn(MESSAGE_ID, timeuuid())
            .addColumn(SIZE, bigint()))
        .build();
}
