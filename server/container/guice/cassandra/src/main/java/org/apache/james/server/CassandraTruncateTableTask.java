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

package org.apache.james.server;

import jakarta.inject.Inject;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.versions.table.CassandraSchemaVersionTable;

public class CassandraTruncateTableTask implements CleanupTasksPerformer.CleanupTask {
    private final CassandraTableManager tableManager;

    @Inject
    public CassandraTruncateTableTask(CassandraTableManager tableManager) {
        this.tableManager = tableManager;
    }

    @Override
    public Result run() {
        tableManager
            .clearTables(table -> !table.getName().equals(CassandraSchemaVersionTable.TABLE_NAME));
        return Result.COMPLETED;
    }
}
