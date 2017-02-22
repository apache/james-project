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

import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;

import java.util.Collections;
import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.mailbox.cassandra.table.CassandraApplicableFlagTable;
import org.apache.james.mailbox.cassandra.table.Flag;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public class CassandraApplicableFlagsModule implements CassandraModule {
    private final List<CassandraTable> tables;
    private final List<CassandraIndex> index;
    private final List<CassandraType> types;

    public CassandraApplicableFlagsModule() {
        tables = ImmutableList.of(
            new CassandraTable(CassandraApplicableFlagTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraApplicableFlagTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraApplicableFlagTable.MAILBOX_ID, timeuuid())
                    .addColumn(Flag.ANSWERED, cboolean())
                    .addColumn(Flag.DELETED, cboolean())
                    .addColumn(Flag.DRAFT, cboolean())
                    .addColumn(Flag.FLAGGED, cboolean())
                    .addColumn(Flag.SEEN, cboolean())
                    .addColumn(Flag.USER_FLAGS, set(text()))));
        index = Collections.emptyList();
        types = Collections.emptyList();
    }

    @Override
    public List<CassandraTable> moduleTables() {
        return tables;
    }

    @Override
    public List<CassandraIndex> moduleIndex() {
        return index;
    }

    @Override
    public List<CassandraType> moduleTypes() {
        return types;
    }

}
