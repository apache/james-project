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
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;

import com.datastax.driver.core.Cluster;

public final class CassandraCluster implements AutoCloseable {

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

    private static Optional<Exception> startStackTrace = Optional.empty();

    private final CassandraModule module;
    private final Cluster nonPrivilegedCluster;
    private final TestingSession nonPrivilegedSession;
    private final CassandraTypesProvider typesProvider;

    private CassandraCluster(CassandraModule module, Host host) throws RuntimeException {
        this.module = module;

        ClusterConfiguration configuration = DockerCassandra.configurationBuilder(host)
            .build();
        this.nonPrivilegedCluster = ClusterFactory.create(configuration);
        this.nonPrivilegedSession = new TestingSession(new SessionWithInitializedTablesFactory(configuration,
            nonPrivilegedCluster, module, CassandraModule.EMPTY_MODULE).get());
        this.typesProvider = new CassandraTypesProvider(module, nonPrivilegedSession);
    }

    public TestingSession getConf() {
        return nonPrivilegedSession;
    }

    public CassandraTypesProvider getTypesProvider() {
        return typesProvider;
    }

    @Override
    public void close() {
        nonPrivilegedSession.registerScenario(NOTHING);
        if (!nonPrivilegedCluster.isClosed()) {
            clearTables();
            closeCluster();
        }
    }

    void closeCluster() {
        nonPrivilegedCluster.closeAsync().force();
        startStackTrace = Optional.empty();
    }

    void clearTables() {
        new CassandraTableManager(module, nonPrivilegedSession).clearAllTables();
    }
}
