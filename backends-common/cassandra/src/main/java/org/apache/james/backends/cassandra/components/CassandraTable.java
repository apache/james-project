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

import java.util.Objects;
import java.util.function.Function;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableWithOptions;
import com.google.common.base.MoreObjects;

public class CassandraTable {
    public enum InitializationStatus {
        ALREADY_DONE,
        PARTIAL,
        FULL;

        public InitializationStatus reduce(InitializationStatus other) {
            if (this == other) {
                return this;
            }

            return PARTIAL;
        }
    }

    private final Function<CassandraTypesProvider, CreateTableWithOptions> createStatement;
    private final String name;

    public CassandraTable(String name, Function<CassandraTypesProvider, CreateTableWithOptions> createStatement) {
        this.createStatement = createStatement;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public InitializationStatus initialize(KeyspaceMetadata keyspaceMetadata, CqlSession session, CassandraTypesProvider typesProvider) {
        if (keyspaceMetadata.getTable(name).isPresent()) {
            return InitializationStatus.ALREADY_DONE;
        }

        session.execute(createStatement.apply(typesProvider).build()
            .setExecutionProfile(JamesExecutionProfiles.getTableCreationProfile(session)));

        return InitializationStatus.FULL;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraTable) {
            CassandraTable that = (CassandraTable) o;

            return Objects.equals(this.name, that.name)
                    && Objects.equals(this.createStatement, that.createStatement);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, createStatement);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("createStatement", createStatement)
                .toString();
    }
}
