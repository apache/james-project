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

package org.apache.james.jmap.cassandra.upload;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;
import static com.datastax.driver.core.schemabuilder.TableOptions.CompactionOptions.TimeWindowCompactionStrategyOptions.CompactionWindowUnit.DAYS;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface UploadModule {

    String TABLE_NAME = "uploads";

    String ID = "id";
    String CONTENT_TYPE = "content_type";
    String SIZE = "size";
    String BUCKET_ID = "bucket_id";
    String BLOB_ID = "blob_id";
    String USER = "user";

    CassandraModule MODULE = CassandraModule.table(TABLE_NAME)
        .comment("Holds JMAP uploads")
        .options(options -> options
            .compactionOptions(SchemaBuilder.timeWindowCompactionStrategy()
                .compactionWindowSize(7)
                .compactionWindowUnit(DAYS))
            .caching(SchemaBuilder.KeyCaching.ALL, SchemaBuilder.rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(ID, timeuuid())
            .addColumn(CONTENT_TYPE, text())
            .addColumn(SIZE, bigint())
            .addColumn(BUCKET_ID, text())
            .addColumn(BLOB_ID, text())
            .addColumn(USER, text()))

        .build();
}
