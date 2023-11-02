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

import java.util.function.Function;

import org.jooq.DDLQuery;
import org.jooq.DSLContext;

import com.google.common.base.Preconditions;

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
        PostgresTable enableRLS(boolean enableRowLevelSecurity);

        default PostgresTable noRLS() {
            return enableRLS(false);
        }

        default PostgresTable enableRLS() {
            return enableRLS(true);
        }
    }

    public static RequireCreateTableStep name(String tableName) {
        Preconditions.checkNotNull(tableName);

        return createTableFunction -> enableRLS -> new PostgresTable(tableName, enableRLS, dsl -> createTableFunction.createTable(dsl, tableName));
    }

    private final String name;
    private final boolean enableRowLevelSecurity;
    private final Function<DSLContext, DDLQuery> createTableStepFunction;

    private PostgresTable(String name, boolean enableRowLevelSecurity, Function<DSLContext, DDLQuery> createTableStepFunction) {
        this.name = name;
        this.enableRowLevelSecurity = enableRowLevelSecurity;
        this.createTableStepFunction = createTableStepFunction;
    }


    public String getName() {
        return name;
    }

    public Function<DSLContext, DDLQuery> getCreateTableStepFunction() {
        return createTableStepFunction;
    }

    public boolean isEnableRowLevelSecurity() {
        return enableRowLevelSecurity;
    }
}
