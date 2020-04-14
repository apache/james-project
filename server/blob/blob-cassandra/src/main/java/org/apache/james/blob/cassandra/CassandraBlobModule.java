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

package org.apache.james.blob.cassandra;

import static com.datastax.driver.core.schemabuilder.TableOptions.CompactionOptions.TimeWindowCompactionStrategyOptions.CompactionWindowUnit.HOURS;
import static org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobParts.DATA;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.BlobTables.BucketBlobParts;
import org.apache.james.blob.cassandra.BlobTables.BucketBlobTable;
import org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobParts;
import org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobTable;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraBlobModule {
    CassandraModule MODULE = CassandraModule
        .builder()

        .table(DefaultBucketBlobParts.TABLE_NAME)
        .comment("Holds blob parts composing blobs in the default bucket." +
            "Messages` headers and bodies are stored, chunked in blobparts.")
        .statement(statement -> statement
            .addPartitionKey(DefaultBucketBlobParts.ID, DataType.text())
            .addClusteringColumn(DefaultBucketBlobParts.CHUNK_NUMBER, DataType.cint())
            .addColumn(DefaultBucketBlobParts.DATA, DataType.blob()))

        .table(DefaultBucketBlobTable.TABLE_NAME)
        .comment("Holds information for retrieving all blob parts composing this blob within the default bucket. " +
            "Messages` headers and bodies are stored as blobparts.")
        .statement(statement -> statement
            .addPartitionKey(DefaultBucketBlobTable.ID, DataType.text())
            .addClusteringColumn(DefaultBucketBlobTable.NUMBER_OF_CHUNK, DataType.cint()))

        .table(BucketBlobParts.TABLE_NAME)
        .comment("Holds blob parts composing blobs in a non-default bucket." +
            "Messages` headers and bodies are stored, chunked in blobparts.")
        .statement(statement -> statement
            .addPartitionKey(BucketBlobParts.BUCKET, DataType.text())
            .addPartitionKey(BucketBlobParts.ID, DataType.text())
            .addClusteringColumn(BucketBlobParts.CHUNK_NUMBER, DataType.cint())
            .addColumn(DATA, DataType.blob()))

        .table(BucketBlobTable.TABLE_NAME)
        .comment("Holds information for retrieving all blob parts composing this blob in a non-default bucket. " +
            "Messages` headers and bodies are stored as blobparts.")
        .statement(statement -> statement
            .addPartitionKey(BucketBlobParts.BUCKET, DataType.text())
            .addPartitionKey(BucketBlobParts.ID, DataType.text())
            .addClusteringColumn(BucketBlobTable.NUMBER_OF_CHUNK, DataType.cint()))

        .table(BlobTables.DumbBlobCache.TABLE_NAME)
        .options(options -> options
            .compactionOptions(SchemaBuilder.timeWindowCompactionStrategy()
                .compactionWindowSize(1)
                .compactionWindowUnit(HOURS))
            .readRepairChance(0.0))
        .comment("Write through cache for small blobs stored in a slower blob store implementation which is object storage" +
            "Messages` headers and bodies are stored as blobparts.")
        .statement(statement -> statement
            .addPartitionKey(BlobTables.DumbBlobCache.ID, DataType.text())
            .addColumn(BlobTables.DumbBlobCache.DATA, DataType.blob()))

        .build();
}
