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

package org.apache.james.backends.cassandra.init;

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timestamp;

import java.util.Collections;
import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public class CassandraZonedDateTimeModule implements CassandraModule {

    public static final String ZONED_DATE_TIME = "zonedDateTime";
    public static final String DATE = "date";
    public static final String TIME_ZONE = "timeZone";

    private final List<CassandraTable> tables;
    private final List<CassandraIndex> index;
    private final List<CassandraType> types;

    public CassandraZonedDateTimeModule() {
        tables = Collections.emptyList();
        index = Collections.emptyList();
        types = Collections.singletonList(
            new CassandraType(ZONED_DATE_TIME,
                SchemaBuilder.createType(ZONED_DATE_TIME)
                    .ifNotExists()
                    .addColumn(DATE, timestamp())
                    .addColumn(TIME_ZONE, text())));
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
