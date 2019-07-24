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

import static org.apache.james.blob.cassandra.BlobTables.BlobParts.CHUNK_NUMBER;
import static org.apache.james.blob.cassandra.BlobTables.BlobParts.DATA;
import static org.apache.james.blob.cassandra.BlobTables.BlobTable.ID;
import static org.apache.james.blob.cassandra.BlobTables.BlobTable.NUMBER_OF_CHUNK;
import static org.apache.james.blob.cassandra.BlobTables.BlobTable.TABLE_NAME;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.cassandra.BlobTables.BlobParts;

import com.datastax.driver.core.DataType;

public interface CassandraBlobModule {
    CassandraModule MODULE = CassandraModule
        .builder()

        .table(BlobParts.TABLE_NAME)
        .comment("Holds blob parts composing blobs ." +
            "Messages` headers and bodies are stored, chunked in blobparts.")
        .statement(statement -> statement
            .addPartitionKey(ID, DataType.text())
            .addClusteringColumn(CHUNK_NUMBER, DataType.cint())
            .addColumn(DATA, DataType.blob()))

        .table(TABLE_NAME)
        .comment("Holds information for retrieving all blob parts composing this blob. " +
            "Messages` headers and bodies are stored as blobparts.")
        .statement(statement -> statement
            .addPartitionKey(ID, DataType.text())
            .addClusteringColumn(NUMBER_OF_CHUNK, DataType.cint()))

        .build();
}
