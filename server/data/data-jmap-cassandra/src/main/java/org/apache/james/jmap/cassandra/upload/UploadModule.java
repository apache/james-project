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

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;
import static com.datastax.oss.driver.api.querybuilder.schema.compaction.TimeWindowCompactionStrategy.CompactionWindowUnit.DAYS;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;

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
            .withCompaction(SchemaBuilder.timeWindowCompactionStrategy()
                .withCompactionWindow(7, DAYS))
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(ID, DataTypes.TIMEUUID)
            .withColumn(CONTENT_TYPE, DataTypes.TEXT)
            .withColumn(SIZE, DataTypes.BIGINT)
            .withColumn(BUCKET_ID, DataTypes.TEXT)
            .withColumn(BLOB_ID, DataTypes.TEXT)
            .withColumn(USER, DataTypes.TEXT))
        .build();
}
