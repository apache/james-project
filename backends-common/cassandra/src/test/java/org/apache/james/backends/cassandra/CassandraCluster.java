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

import static org.apache.james.backends.cassandra.Scenario.NOTHING;

import java.util.Optional;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.KeyspaceFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;

import com.datastax.driver.core.Cluster;

public final class CassandraCluster implements AutoCloseable {
    public static final String KEYSPACE = "testing";

    private static Optional<Exception> startStackTrace = Optional.empty();
    private final CassandraModule module;
    private TestingSession session;
    private CassandraTypesProvider typesProvider;
    private Cluster cluster;

    public static CassandraCluster create(CassandraModule module, Host host) {
        assertClusterNotRunning();
        CassandraCluster cassandraCluster = new CassandraCluster(module, host);
        startStackTrace = Optional.of(new Exception("initial connection call trace"));
        return cassandraCluster;
    }

    private static void assertClusterNotRunning() {
      startStackTrace.ifPresent(e -> {
          throw new IllegalStateException("Cluster already running, look at the cause for the initial connection creation call trace", e);
      });
    }

    private CassandraCluster(CassandraModule module, Host host) throws RuntimeException {
        this.module = module;
        try {
            ClusterConfiguration clusterConfiguration = ClusterConfiguration.builder()
                .host(host)
                .keyspace(KEYSPACE)
                .createKeyspace()
                .disableDurableWrites()
                .build();
            cluster = ClusterFactory.create(clusterConfiguration);
            KeyspaceFactory.createKeyspace(clusterConfiguration, cluster);
            session = new TestingSession(
                new SessionWithInitializedTablesFactory(clusterConfiguration, cluster, module).get());
            typesProvider = new CassandraTypesProvider(module, session);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public TestingSession getConf() {
        return session;
    }

    public CassandraTypesProvider getTypesProvider() {
        return typesProvider;
    }

    @Override
    public void close() {
        session.registerScenario(NOTHING);
        if (!cluster.isClosed()) {
            clearTables();
            closeCluster();
        }
    }

    public void closeCluster() {
        cluster.closeAsync().force();
        startStackTrace = Optional.empty();
    }

    public void clearTables() {
        new CassandraTableManager(module, session).clearAllTables();
    }
}
