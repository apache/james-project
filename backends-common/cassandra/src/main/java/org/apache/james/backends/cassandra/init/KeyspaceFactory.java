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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

public class KeyspaceFactory {
    private static final String SYSTEM_SCHEMA = "system_schema";
    private static final String KEYSPACES = "keyspaces";
    private static final String KEYSPACE_NAME = "keyspace_name";

    public static void createKeyspace(KeyspaceConfiguration configuration, CqlSession session) {
        if (!keyspaceExist(session, configuration.getKeyspace())) {
            session.execute(SchemaBuilder.createKeyspace(configuration.getKeyspace())
                .ifNotExists()
                .withReplicationOptions(ImmutableMap.<String, Object>builder()
                    .put("class", "SimpleStrategy")
                    .put("replication_factor", configuration.getReplicationFactor())
                    .build())
                .withDurableWrites(configuration.isDurableWrites())
                .build());
        }
    }

    @VisibleForTesting
    public static boolean keyspaceExist(CqlSession session, String keyspaceName) {
        long numberOfKeyspaces = session.execute(selectFrom(SYSTEM_SCHEMA, KEYSPACES)
                .countAll()
                .whereColumn(KEYSPACE_NAME)
                    .isEqualTo(literal(keyspaceName))
            .build())
            .one()
            .getLong("count");

        if (numberOfKeyspaces > 1 || numberOfKeyspaces < 0) {
            throw new IllegalStateException(String.format("unexpected keyspace('%s') count being %d", keyspaceName, numberOfKeyspaces));
        }

        return numberOfKeyspaces == 1;
    }
}
