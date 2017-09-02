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

package org.apache.james.user.cassandra;

import static com.datastax.driver.core.DataType.text;

import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.user.cassandra.tables.CassandraUserTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraUsersRepositoryModule implements CassandraModule {

    private final List<CassandraTable> tables;
    private final List<CassandraType> types;

    public CassandraUsersRepositoryModule() {
        tables = ImmutableList.of(
                new CassandraTable(CassandraUserTable.TABLE_NAME,
                    SchemaBuilder.createTable(CassandraUserTable.TABLE_NAME)
                        .ifNotExists()
                        .addPartitionKey(CassandraUserTable.NAME, text())
                        .addColumn(CassandraUserTable.REALNAME, text())
                        .addColumn(CassandraUserTable.PASSWORD, text())
                        .addColumn(CassandraUserTable.ALGORITHM, text())
                        .withOptions()
                        .comment("Holds users of this James server.")));
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
