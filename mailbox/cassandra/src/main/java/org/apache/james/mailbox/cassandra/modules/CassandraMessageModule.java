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

import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.blob;
import static com.datastax.driver.core.DataType.cboolean;
import static com.datastax.driver.core.DataType.cint;
import static com.datastax.driver.core.DataType.set;
import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;
import static com.datastax.driver.core.DataType.timeuuid;

public class CassandraMessageModule implements CassandraModule {

    private final List<CassandraTable> tables;
    private final List<CassandraIndex> index;
    private final List<CassandraType> types;

    public CassandraMessageModule() {
        tables = Collections.singletonList(
            new CassandraTable(CassandraMessageTable.TABLE_NAME,
                SchemaBuilder.createTable(CassandraMessageTable.TABLE_NAME)
                    .ifNotExists()
                    .addPartitionKey(CassandraMessageTable.MAILBOX_ID, timeuuid())
                    .addClusteringColumn(CassandraMessageTable.IMAP_UID, bigint())
                    .addColumn(CassandraMessageTable.INTERNAL_DATE, timestamp())
                    .addColumn(CassandraMessageTable.BODY_START_OCTET, cint())
                    .addColumn(CassandraMessageTable.BODY_OCTECTS, bigint())
                    .addColumn(CassandraMessageTable.TEXTUAL_LINE_COUNT, bigint())
                    .addColumn(CassandraMessageTable.MOD_SEQ, bigint())
                    .addColumn(CassandraMessageTable.FULL_CONTENT_OCTETS, bigint())
                    .addColumn(CassandraMessageTable.BODY_CONTENT, blob())
                    .addColumn(CassandraMessageTable.HEADER_CONTENT, blob())
                    .addColumn(CassandraMessageTable.Flag.ANSWERED, cboolean())
                    .addColumn(CassandraMessageTable.Flag.DELETED, cboolean())
                    .addColumn(CassandraMessageTable.Flag.DRAFT, cboolean())
                    .addColumn(CassandraMessageTable.Flag.FLAGGED, cboolean())
                    .addColumn(CassandraMessageTable.Flag.RECENT, cboolean())
                    .addColumn(CassandraMessageTable.Flag.SEEN, cboolean())
                    .addColumn(CassandraMessageTable.Flag.USER, cboolean())
                    .addColumn(CassandraMessageTable.Flag.USER_FLAGS, set(text()))
                    .addUDTListColumn(CassandraMessageTable.PROPERTIES, SchemaBuilder.frozen(CassandraMessageTable.PROPERTIES))));
        index = Arrays.asList(
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMessageTable.Flag.RECENT)
                    .ifNotExists()
                    .onTable(CassandraMessageTable.TABLE_NAME)
                    .andColumn(CassandraMessageTable.Flag.RECENT)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMessageTable.Flag.SEEN)
                    .ifNotExists()
                    .onTable(CassandraMessageTable.TABLE_NAME)
                    .andColumn(CassandraMessageTable.Flag.SEEN)),
            new CassandraIndex(
                SchemaBuilder.createIndex(CassandraIndex.INDEX_PREFIX + CassandraMessageTable.Flag.DELETED)
                    .ifNotExists()
                    .onTable(CassandraMessageTable.TABLE_NAME)
                    .andColumn(CassandraMessageTable.Flag.DELETED)));
        types = Collections.singletonList(
            new CassandraType(CassandraMessageTable.PROPERTIES,
                SchemaBuilder.createType(CassandraMessageTable.PROPERTIES)
                    .ifNotExists()
                    .addColumn(CassandraMessageTable.Properties.NAMESPACE, text())
                    .addColumn(CassandraMessageTable.Properties.NAME, text())
                    .addColumn(CassandraMessageTable.Properties.VALUE, text())));
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
