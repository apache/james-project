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

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.HAS_ATTACHMENT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.PREVIEW;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.TABLE_NAME;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraMessageFastViewProjectionModule {
    CassandraModule MODULE = CassandraModule.table(TABLE_NAME)
        .comment("Storing the JMAP projections for MessageFastView, an aggregation of JMAP properties expected to be fast to fetch.")
        .options(options -> options
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION))
            .withLZ4Compression(8, 1.0))
        .statement(statement -> types -> statement
            .withPartitionKey(MESSAGE_ID, DataTypes.UUID)
            .withColumn(PREVIEW, DataTypes.TEXT)
            .withColumn(HAS_ATTACHMENT, DataTypes.BOOLEAN))
        .build();
}
