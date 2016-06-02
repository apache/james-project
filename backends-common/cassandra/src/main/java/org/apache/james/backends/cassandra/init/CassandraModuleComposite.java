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

import java.util.Arrays;
import java.util.List;

import org.apache.james.backends.cassandra.components.CassandraIndex;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.util.streams.ImmutableCollectors;

import com.google.common.collect.ImmutableList;

public class CassandraModuleComposite implements CassandraModule {

    private final ImmutableList<CassandraTable> tables;
    private final ImmutableList<CassandraIndex> index;
    private final ImmutableList<CassandraType> types;

    public CassandraModuleComposite(CassandraModule... modules) {
        tables = Arrays.stream(modules)
            .flatMap(module -> module.moduleTables().stream())
            .collect(ImmutableCollectors.toImmutableList());
        index = Arrays.stream(modules)
            .flatMap(module -> module.moduleIndex().stream())
            .collect(ImmutableCollectors.toImmutableList());
        types = Arrays.stream(modules)
            .flatMap(module -> module.moduleTypes().stream())
            .collect(ImmutableCollectors.toImmutableList());
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
