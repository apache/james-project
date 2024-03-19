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

import java.util.List;
import java.util.function.Function;

import org.jooq.DDLQuery;
import org.jooq.DSLContext;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class PostgresTable {
    @FunctionalInterface
    public interface RequireCreateTableStep {
        RequireRowLevelSecurity createTableStep(CreateTableFunction createTableFunction);
    }

    @FunctionalInterface
    public interface CreateTableFunction {
        DDLQuery createTable(DSLContext dsl, String tableName);
    }

    @FunctionalInterface
    public interface RequireRowLevelSecurity {
        FinalStage supportsRowLevelSecurity(boolean rowLevelSecurityEnabled);

        default FinalStage disableRowLevelSecurity() {
            return supportsRowLevelSecurity(false);
        }

        default FinalStage supportsRowLevelSecurity() {
            return supportsRowLevelSecurity(true);
        }
    }

    public static class FinalStage {
        private final String tableName;
        private final boolean supportsRowLevelSecurity;
        private final Function<DSLContext, DDLQuery> createTableStepFunction;
        private final ImmutableList.Builder<String> additionalAlterQueries;

        public FinalStage(String tableName, boolean supportsRowLevelSecurity, Function<DSLContext, DDLQuery> createTableStepFunction) {
            this.tableName = tableName;
            this.supportsRowLevelSecurity = supportsRowLevelSecurity;
            this.createTableStepFunction = createTableStepFunction;
            this.additionalAlterQueries = ImmutableList.builder();
        }

        /**
         * Raw SQL ALTER queries in case not supported by jOOQ DSL.
         */
        public FinalStage addAdditionalAlterQueries(String... additionalAlterQueries) {
            this.additionalAlterQueries.add(additionalAlterQueries);
            return this;
        }

        public PostgresTable build() {
            return new PostgresTable(tableName, supportsRowLevelSecurity, createTableStepFunction, additionalAlterQueries.build());
        }
    }

    public static RequireCreateTableStep name(String tableName) {
        Preconditions.checkNotNull(tableName);
        String strategyName = tableName.toLowerCase();

        return createTableFunction -> supportsRowLevelSecurity -> new FinalStage(strategyName, supportsRowLevelSecurity, dsl -> createTableFunction.createTable(dsl, strategyName));
    }

    private final String name;
    private final boolean supportsRowLevelSecurity;
    private final Function<DSLContext, DDLQuery> createTableStepFunction;
    private final List<String> additionalAlterQueries;

    private PostgresTable(String name, boolean supportsRowLevelSecurity, Function<DSLContext, DDLQuery> createTableStepFunction, List<String> additionalAlterQueries) {
        this.name = name;
        this.supportsRowLevelSecurity = supportsRowLevelSecurity;
        this.createTableStepFunction = createTableStepFunction;
        this.additionalAlterQueries = additionalAlterQueries;
    }


    public String getName() {
        return name;
    }

    public Function<DSLContext, DDLQuery> getCreateTableStepFunction() {
        return createTableStepFunction;
    }

    public boolean supportsRowLevelSecurity() {
        return supportsRowLevelSecurity;
    }

    public List<String> getAdditionalAlterQueries() {
        return additionalAlterQueries;
    }
}
