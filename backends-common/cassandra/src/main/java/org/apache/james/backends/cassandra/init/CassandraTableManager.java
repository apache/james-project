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

import java.util.List;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.ImmutableList;
import org.apache.james.backends.cassandra.components.CassandraModule;

public class CassandraTableManager {

    private final Session session;
    private final ImmutableList<CassandraModule> modules;

    public CassandraTableManager(List<CassandraModule> modules, Session session) {
        this.session = session;
        this.modules = ImmutableList.copyOf(modules);
    }

    public CassandraTableManager ensureAllTables() {
        modules.stream()
            .flatMap(module -> module.moduleTables().stream())
            .forEach(table -> session.execute(table.getCreateStatement()));
        modules.stream()
            .flatMap(module -> module.moduleIndex().stream())
            .forEach(index -> session.execute(index.getCreateIndexStatement()));
        return this;
    }

    public void clearAllTables() {
        modules.stream()
            .flatMap(module -> module.moduleTables().stream())
            .forEach(table -> clearTable(table.getName()));
    }

    private void clearTable(String tableName) {
        session.execute(QueryBuilder.truncate(tableName));
    }
}
