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

import java.util.stream.Stream;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

@Singleton
public class SessionWithInitializedTablesFactory implements Provider<Session> {
    private final CassandraModule module;
    private final Session session;

    @Inject
    public SessionWithInitializedTablesFactory(ClusterConfiguration clusterConfiguration, Cluster cluster, CassandraModule module) {
        this.module = module;
        this.session = createSession(cluster, clusterConfiguration.getKeyspace());
    }

    private Session createSession(Cluster cluster, String keyspace) {
        Session session = cluster.connect(keyspace);
        try {
            if (allOperationsAreFullyPerformed(session)) {
                new CassandraSchemaVersionDAO(session)
                    .updateVersion(CassandraSchemaVersionManager.MAX_VERSION)
                    .block();
            }
            return session;
        } catch (Exception e) {
            session.close();
            throw e;
        }
    }

    private boolean allOperationsAreFullyPerformed(Session session) {
        Stream<Boolean> operations = Stream.of(createTypes(session), createTables(session));
        return operations.allMatch(updated -> updated);
    }

    private boolean createTypes(Session session) {
        return new CassandraTypesCreator(module, session)
                .initializeTypes() == CassandraType.InitializationStatus.FULL;
    }

    private boolean createTables(Session session) {
        return new CassandraTableManager(module, session)
            .initializeTables() == CassandraTable.InitializationStatus.FULL;
    }

    @Override
    public Session get() {
        return session;
    }

    @PreDestroy
    public synchronized void destroy() {
        session.close();
    }

}
