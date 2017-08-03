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

package org.apache.james.backends.cassandra.versions;

import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.timeuuid;

import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraSchemaVersionModule implements CassandraModule {

    private final List<CassandraTable> tables;

    public CassandraSchemaVersionModule() {
        this.tables = ImmutableList.of(
            new CassandraTable(CassandraSchemaVersionTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraSchemaVersionTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraSchemaVersionTable.KEY, timeuuid())
                    .addClusteringColumn(CassandraSchemaVersionTable.VALUE, cint())
                    .withOptions()
                    .comment("Holds the history of the versions of the schema used.")));
    }


    @Override
    public List<CassandraTable> moduleTables() {
        return tables;
    }

    @Override
    public List<CassandraType> moduleTypes() {
        return ImmutableList.of();
    }
}
