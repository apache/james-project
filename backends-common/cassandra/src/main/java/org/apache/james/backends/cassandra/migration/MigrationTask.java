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

package org.apache.james.backends.cassandra.migration;

import java.util.Optional;

public class MigrationTask implements Migration {
    public static final String CASSANDRA_MIGRATION = "CassandraMigration";

    public static class Details {
        private final int toVersion;

        public Details(int toVersion) {
            this.toVersion = toVersion;
        }

        public int getToVersion() {
            return toVersion;
        }
    }

    private final Migration migration;
    private final int toVersion;

    public MigrationTask(Migration migration, int toVersion) {
        this.migration = migration;
        this.toVersion = toVersion;
    }

    @Override
    public Result run() {
        return migration.run();
    }

    @Override
    public String type() {
        return CASSANDRA_MIGRATION;
    }

    @Override
    public Optional<Object> details() {
        return Optional.of(new Details(toVersion));
    }
}
