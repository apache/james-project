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

package org.apache.james.blob.cassandra.cache;

import static com.datastax.driver.core.schemabuilder.TableOptions.CompactionOptions.TimeWindowCompactionStrategyOptions.CompactionWindowUnit.HOURS;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.DATA;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.ID;
import static org.apache.james.blob.cassandra.BlobTables.BlobStoreCache.TABLE_NAME;

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraBlobCacheModule {

    double NO_READ_REPAIR = 0d;

    CassandraModule MODULE = CassandraModule
        .builder()
        .table(TABLE_NAME)
        .options(options -> options
            .compactionOptions(SchemaBuilder.timeWindowCompactionStrategy()
                .compactionWindowSize(1)
                .compactionWindowUnit(HOURS))
            .readRepairChance(NO_READ_REPAIR))
        .comment("Write through cache for small blobs stored in a slower blob store implementation.")
        .statement(statement -> statement
            .addPartitionKey(ID, DataType.text())
            .addColumn(DATA, DataType.blob()))
        .build();
}
