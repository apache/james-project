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

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;

import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraAnnotationTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraAnnotationModule implements CassandraModule {
    private final List<CassandraTable> tables;
    private final List<CassandraType> types;

    public CassandraAnnotationModule() {
        tables = ImmutableList.of(
            new CassandraTable(CassandraAnnotationTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraAnnotationTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraAnnotationTable.MAILBOX_ID, timeuuid())
                    .addClusteringColumn(CassandraAnnotationTable.KEY, text())
                    .addColumn(CassandraAnnotationTable.VALUE, text())
                    .withOptions()
                    .comment("Holds Cassandra mailbox annotations")
                    .compactionOptions(SchemaBuilder.leveledStrategy())
                    .caching(SchemaBuilder.KeyCaching.ALL,
                        SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION))));
        types = ImmutableList.of();
    }

    @Override
    public List<CassandraTable> moduleTables() {
        return tables;
    }

    @Override
    public List<CassandraType> moduleTypes() {
        return types;
    }

}
