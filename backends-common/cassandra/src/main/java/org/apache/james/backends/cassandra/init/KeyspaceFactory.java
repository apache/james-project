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

import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public class KeyspaceFactory {
    public static Mono<Void> createKeyspace(KeyspaceConfiguration configuration, CqlSession session) {
        if (!keyspaceExist(session, configuration.getKeyspace())) {
            return Mono.from(session.executeReactive(SchemaBuilder.createKeyspace(configuration.getKeyspace())
                .ifNotExists()
                .withReplicationOptions(ImmutableMap.<String, Object>builder()
                    .put("class", "SimpleStrategy")
                    .put("replication_factor", configuration.getReplicationFactor())
                    .build())
                .withDurableWrites(configuration.isDurableWrites())
                .build()))
                .then();
        }
        return Mono.empty();
    }

    @VisibleForTesting
    public static boolean keyspaceExist(CqlSession session, String keyspaceName) {
        return session.getMetadata().getKeyspaces().get(CqlIdentifier.fromCql(keyspaceName)) != null;
    }
}
