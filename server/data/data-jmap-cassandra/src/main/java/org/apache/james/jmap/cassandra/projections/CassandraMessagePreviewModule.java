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

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.uuid;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.jmap.cassandra.projections.table.CassandraMessagePreviewTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraMessagePreviewModule {
    CassandraModule MODULE = CassandraModule.table(CassandraMessagePreviewTable.TABLE_NAME)
        .comment("Storing the JMAP preview property extracted from message bodies")
        .options(options -> options
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(CassandraMessagePreviewTable.MESSAGE_ID, uuid())
            .addColumn(CassandraMessagePreviewTable.PREVIEW, text()))
        .build();
}
