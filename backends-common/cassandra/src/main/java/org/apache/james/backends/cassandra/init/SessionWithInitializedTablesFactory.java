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

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraTable;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;

import com.datastax.oss.driver.api.core.CqlSession;

@Singleton
public class SessionWithInitializedTablesFactory implements Provider<CqlSession> {
    private final CassandraModule module;
    private final CqlSession session;

    @Inject
    public SessionWithInitializedTablesFactory(CqlSession cluster,
                                               CassandraModule module) {
        this.module = module;
        this.session = createSession(cluster);
    }

    private CqlSession createSession(CqlSession cluster) {
        try {
            if (allOperationsAreFullyPerformed(cluster, module)) {
                new CassandraSchemaVersionDAO(cluster)
                    .updateVersion(CassandraSchemaVersionManager.MAX_VERSION)
                    .block();
            }
            return cluster;
        } catch (Exception e) {
            cluster.close();
            throw e;
        }
    }

    private boolean allOperationsAreFullyPerformed(CqlSession session, CassandraModule module) {
        boolean types = createTypes(session, module);

        boolean tables = createTables(session, module);
        return types && tables;
    }

    private boolean createTypes(CqlSession session, CassandraModule module) {
        return new CassandraTypesCreator(module, session)
                .initializeTypes() == CassandraType.InitializationStatus.FULL;
    }

    private boolean createTables(CqlSession session, CassandraModule module) {
        CassandraTypesProvider cassandraTypesProvider = new CassandraTypesProvider(session);
        return new CassandraTableManager(module, session)
            .initializeTables(cassandraTypesProvider) == CassandraTable.InitializationStatus.FULL;
    }

    @Override
    public CqlSession get() {
        return session;
    }

    @PreDestroy
    public synchronized void destroy() {
        session.close();
    }

}
