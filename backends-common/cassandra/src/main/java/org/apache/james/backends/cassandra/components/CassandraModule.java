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

package org.apache.james.backends.cassandra.components;

import java.util.List;
import java.util.function.Function;

import com.datastax.driver.core.Statement;
import com.datastax.driver.core.schemabuilder.Create;
import com.datastax.driver.core.schemabuilder.CreateType;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableList;

public interface CassandraModule {

    class Impl implements CassandraModule {
        private final List<CassandraTable> tables;
        private final List<CassandraType> types;

        private Impl(List<CassandraTable> tables, List<CassandraType> types) {
            this.tables = tables;
            this.types = types;
        }

        @Override
        public List<CassandraTable> moduleTables() {
            return tables;
        }

        @Override
        public List<CassandraType> moduleTypes() {
            return types;
        }
    }

    class Builder {
        private ImmutableList.Builder<CassandraTable> tables;
        private ImmutableList.Builder<CassandraType> types;

        private Builder() {
            tables = ImmutableList.builder();
            types = ImmutableList.builder();
        }

        public TableBuilder table(String tableName) {
            return new TableBuilder(this, tableName);
        }

        public TypeBuilder type(String typeName) {
            return new TypeBuilder(this, typeName);
        }

        public Impl build() {
            return new Impl(
                tables.build(),
                types.build());
        }

        private Builder addTable(CassandraTable table) {
            tables.add(table);
            return this;
        }

        private Builder addType(CassandraType type) {
            types.add(type);
            return this;
        }
    }

    class TableBuilder {
        private final Builder originalBuilderReference;
        private final String tableName;

        private TableBuilder(Builder originalBuilderReference, String tableName) {
            this.originalBuilderReference = originalBuilderReference;
            this.tableName = tableName;
        }

        public Builder statement(Function<Create, Statement> createStatement) {
            return originalBuilderReference.addTable(
                new CassandraTable(tableName, createStatement.apply(SchemaBuilder.createTable(tableName))));
        }
    }

    class TypeBuilder {
        private final Builder originalBuilderReference;
        private final String typeName;

        private TypeBuilder(Builder originalBuilderReference, String typeName) {
            this.originalBuilderReference = originalBuilderReference;
            this.typeName = typeName;
        }

        public Builder statement(Function<CreateType, CreateType> createStatement) {
            return originalBuilderReference.addType(
                new CassandraType(typeName, createStatement.apply(
                    SchemaBuilder.createType(typeName))));
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static TypeBuilder type(String typeName) {
        return builder().type(typeName);
    }

    static TableBuilder table(String tableName) {
        return builder().table(tableName);
    }

    List<CassandraTable> moduleTables();

    List<CassandraType> moduleTypes();

}
