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

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.google.common.collect.ImmutableMap;

public class CassandraTypesProvider {
    private final CqlSession session;

    @Inject
    public CassandraTypesProvider(CqlSession session) {
        this.session = session;
    }

    public Map<CqlIdentifier, UserDefinedType> userDefinedTypes() {
        return session.getKeyspace().map(keyspace ->
            session.getMetadata()
                .getKeyspaces()
                .get(keyspace)
                .getUserDefinedTypes())
            .orElse(ImmutableMap.of());
    }

    public UserDefinedType getDefinedUserType(String typeName) {
        return Optional.ofNullable(userDefinedTypes().get(CqlIdentifier.fromCql(typeName)))
            .orElseThrow(() -> new RuntimeException("Cassandra UDT " + typeName + " can not be retrieved"))
            .copy(true);
    }

}
