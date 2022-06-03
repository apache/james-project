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

import static org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobParts.DATA;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.BlobTables.BucketBlobParts;
import org.apache.james.blob.cassandra.BlobTables.BucketBlobTable;
import org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobParts;
import org.apache.james.blob.cassandra.BlobTables.DefaultBucketBlobTable;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraBlobModule {
    CassandraModule MODULE = CassandraModule
        .builder()

        .table(DefaultBucketBlobParts.TABLE_NAME)
        .comment("Holds blob parts composing blobs in the default bucket." +
            "Messages` headers and bodies are stored, chunked in blobparts.")
        .statement(statement -> types -> statement
            .withPartitionKey(DefaultBucketBlobParts.ID, DataTypes.TEXT)
            .withClusteringColumn(DefaultBucketBlobParts.CHUNK_NUMBER, DataTypes.INT)
            .withColumn(DefaultBucketBlobParts.DATA, DataTypes.BLOB))

        .table(DefaultBucketBlobTable.TABLE_NAME)
        .comment("Holds information for retrieving all blob parts composing this blob within the default bucket. " +
            "Messages` headers and bodies are stored as blobparts.")
        .statement(statement -> types -> statement
            .withPartitionKey(DefaultBucketBlobTable.ID, DataTypes.TEXT)
            .withClusteringColumn(DefaultBucketBlobTable.NUMBER_OF_CHUNK, DataTypes.INT))

        .table(BucketBlobParts.TABLE_NAME)
        .comment("Holds blob parts composing blobs in a non-default bucket." +
            "Messages` headers and bodies are stored, chunked in blobparts.")
        .statement(statement -> types -> statement
            .withPartitionKey(BucketBlobParts.BUCKET, DataTypes.TEXT)
            .withPartitionKey(BucketBlobParts.ID, DataTypes.TEXT)
            .withClusteringColumn(BucketBlobParts.CHUNK_NUMBER, DataTypes.INT)
            .withColumn(DATA, DataTypes.BLOB))

        .table(BucketBlobTable.TABLE_NAME)
        .comment("Holds information for retrieving all blob parts composing this blob in a non-default bucket. " +
            "Messages` headers and bodies are stored as blobparts.")
        .statement(statement -> types -> statement
            .withPartitionKey(BucketBlobParts.BUCKET, DataTypes.TEXT)
            .withPartitionKey(BucketBlobParts.ID, DataTypes.TEXT)
            .withClusteringColumn(BucketBlobTable.NUMBER_OF_CHUNK, DataTypes.INT))

        .build();
}
