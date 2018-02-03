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

import org.apache.james.backends.cassandra.components.CassandraModule;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraTableManager {

    private final Session session;
    private final CassandraModule module;

    public CassandraTableManager(CassandraModule module, Session session) {
        this.session = session;
        this.module = module;
    }

    public CassandraTableManager ensureAllTables() {
        module.moduleTables()
            .forEach(table -> session.execute(table.getCreateStatement()));
        return this;
    }

    public void clearAllTables() {
        module.moduleTables()
            .forEach(table -> clearTable(table.getName()));
    }

    private void clearTable(String tableName) {
        session.execute(QueryBuilder.truncate(tableName));
    }
}
