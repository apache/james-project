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
package org.apache.james.backends.cassandra;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

public final class CassandraCluster implements AutoCloseable {
    public static final String KEYSPACE = "testing";

    private final CassandraModule module;
    private Session session;
    private CassandraTypesProvider typesProvider;
    private Cluster cluster;

    public static CassandraCluster create(CassandraModule module, Host host) {
        return new CassandraCluster(module, host);
    }

    private CassandraCluster(CassandraModule module, Host host) throws RuntimeException {
        this.module = module;
        try {
            cluster = ClusterBuilder.builder()
                .host(host.getHostName())
                .port(host.getPort())
                .build();
            session = new SessionWithInitializedTablesFactory(
                ClusterConfiguration.builder()
                    .host(host)
                    .keyspace(KEYSPACE)
                    .replicationFactor(1)
                    .build(),
                ClusterWithKeyspaceCreatedFactory
                    .config(cluster, KEYSPACE)
                    .replicationFactor(1)
                    .disableDurableWrites()
                    .clusterWithInitializedKeyspace(),
                module)
                .get();
            typesProvider = new CassandraTypesProvider(module, session);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public Session getConf() {
        return session;
    }

    public CassandraTypesProvider getTypesProvider() {
        return typesProvider;
    }

    @Override
    public void close() {
        clearTables();
        closeCluster();
    }

    public void closeCluster() {
        cluster.closeAsync();
    }

    public void clearTables() {
        new CassandraTableManager(module, session).clearAllTables();
    }
}
