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

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.DataType;

public interface CassandraBlobModule {
    CassandraModule MODULE = CassandraModule
        .builder()
        .table(BlobTable.BlobParts.TABLE_NAME)
        .statement(statement -> statement
            .ifNotExists()
            .addPartitionKey(BlobTable.ID, DataType.text())
            .addClusteringColumn(BlobTable.BlobParts.CHUNK_NUMBER, DataType.cint())
            .addColumn(BlobTable.BlobParts.DATA, DataType.blob())
            .withOptions()
            .comment("Holds blob parts composing blobs ." +
                "Messages` headers and bodies are stored, chunked in blobparts."))
        .table(BlobTable.TABLE_NAME)
        .statement(statement -> statement
            .ifNotExists()
            .addPartitionKey(BlobTable.ID, DataType.text())
            .addClusteringColumn(BlobTable.NUMBER_OF_CHUNK, DataType.cint())
            .withOptions()
            .comment("Holds information for retrieving all blob parts composing this blob. " +
                "Messages` headers and bodies are stored as blobparts."))
        .build();
}
