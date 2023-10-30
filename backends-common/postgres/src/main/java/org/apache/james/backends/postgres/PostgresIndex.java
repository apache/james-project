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

public class PostgresIndex {

    @FunctionalInterface
    public interface RequireCreateIndexStep {
        PostgresIndex createIndexStep(CreateIndexFunction createIndexFunction);
    }

    @FunctionalInterface
    public interface CreateIndexFunction {
        DDLQuery createIndex(DSLContext dsl, String indexName);
    }

    public static RequireCreateIndexStep name(String indexName) {
        Preconditions.checkNotNull(indexName);
        String strategyIndexName = indexName.toLowerCase();

        return createIndexFunction -> new PostgresIndex(strategyIndexName, dsl -> createIndexFunction.createIndex(dsl, strategyIndexName));
    }

    private final String name;
    private final Function<DSLContext, DDLQuery> createIndexStepFunction;

    private PostgresIndex(String name, Function<DSLContext, DDLQuery> createIndexStepFunction) {
        this.name = name;
        this.createIndexStepFunction = createIndexStepFunction;
    }

    public String getName() {
        return name;
    }

    public Function<DSLContext, DDLQuery> getCreateIndexStepFunction() {
        return createIndexStepFunction;
    }

}
