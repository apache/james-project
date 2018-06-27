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

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;

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

    public Session createSession(Cluster cluster, String keyspace) {
        Session session = cluster.connect(keyspace);
        try {
            new CassandraTypesCreator(module, session)
                .initializeTypes();
            new CassandraTableManager(module, session)
                .ensureAllTables();
            return session;
        } catch (Exception e) {
            session.close();
            throw e;
        }
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
