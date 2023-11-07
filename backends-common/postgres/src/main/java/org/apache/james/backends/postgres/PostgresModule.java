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

package org.apache.james.backends.postgres;


import java.util.Collection;
import java.util.List;

import com.google.common.collect.ImmutableList;

public interface PostgresModule {

    static PostgresModule aggregateModules(PostgresModule... modules) {
        return builder()
            .modules(modules)
            .build();
    }

    static PostgresModule aggregateModules(Collection<PostgresModule> modules) {
        return builder()
            .modules(modules)
            .build();
    }

    PostgresModule EMPTY_MODULE = builder().build();

    List<PostgresTable> tables();

    List<PostgresIndex> tableIndexes();

    class Impl implements PostgresModule {
        private final List<PostgresTable> tables;
        private final List<PostgresIndex> tableIndexes;

        private Impl(List<PostgresTable> tables, List<PostgresIndex> tableIndexes) {
            this.tables = tables;
            this.tableIndexes = tableIndexes;
        }

        @Override
        public List<PostgresTable> tables() {
            return tables;
        }

        @Override
        public List<PostgresIndex> tableIndexes() {
            return tableIndexes;
        }
    }

    class Builder {
        private final ImmutableList.Builder<PostgresTable> tables;
        private final ImmutableList.Builder<PostgresIndex> tableIndexes;

        public Builder() {
            tables = ImmutableList.builder();
            tableIndexes = ImmutableList.builder();
        }

        public Builder addTable(PostgresTable... table) {
            tables.add(table);
            return this;
        }

        public Builder addIndex(PostgresIndex... index) {
            tableIndexes.add(index);
            return this;
        }

        public Builder addTable(List<PostgresTable> tables) {
            this.tables.addAll(tables);
            return this;
        }

        public Builder addIndex(List<PostgresIndex> indexes) {
            this.tableIndexes.addAll(indexes);
            return this;
        }

        public Builder modules(Collection<PostgresModule> modules) {
            modules.forEach(module -> {
                addTable(module.tables());
                addIndex(module.tableIndexes());
            });
            return this;
        }

        public Builder modules(PostgresModule... modules) {
            return modules(ImmutableList.copyOf(modules));
        }

        public PostgresModule build() {
            return new Impl(tables.build(), tableIndexes.build());
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static PostgresModule table(PostgresTable... tables) {
        return builder()
            .addTable(ImmutableList.copyOf(tables))
            .build();
    }

    static PostgresModule tableIndex(PostgresIndex... tableIndexes) {
        return builder()
            .addIndex(ImmutableList.copyOf(tableIndexes))
            .build();
    }

}
