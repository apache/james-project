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

import java.util.Optional;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraTableManager;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.base.Throwables;

public final class CassandraCluster {
    private static final String CLUSTER_IP = "localhost";
    private static final int CLUSTER_PORT_TEST = 9142;
    private static final String KEYSPACE_NAME = "apache_james";
    private static final int REPLICATION_FACTOR = 1;

    private static final long SLEEP_BEFORE_RETRY = 200;
    private static final int MAX_RETRY = 200;

    private final CassandraModule module;
    private Session session;
    private CassandraTypesProvider typesProvider;

    public static CassandraCluster create(CassandraModule module) throws RuntimeException {
        return new CassandraCluster(module);
    }

    private CassandraCluster(CassandraModule module) throws RuntimeException {
        this.module = module;
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra();

            session = new FunctionRunnerWithRetry(MAX_RETRY)
                .executeAndRetrieveObject(CassandraCluster.this::tryInitializeSession);
            typesProvider = new CassandraTypesProvider(module, session);
        } catch(Exception exception) {
            Throwables.propagate(exception);
        }
    }

    public Session getConf() {
        return session;
    }

    public void ensureAllTables() {
        new CassandraTableManager(module, session).ensureAllTables();
    }

    public void clearAllTables() {
        new CassandraTableManager(module, session).clearAllTables();
    }

    private Optional<Session> tryInitializeSession() {
        try {
            Cluster clusterWithInitializedKeyspace = ClusterWithKeyspaceCreatedFactory
                .clusterWithInitializedKeyspace(getCluster(), KEYSPACE_NAME, REPLICATION_FACTOR);
            return Optional.of(new SessionWithInitializedTablesFactory(module).createSession(clusterWithInitializedKeyspace, KEYSPACE_NAME));
        } catch (NoHostAvailableException exception) {
            sleep(SLEEP_BEFORE_RETRY);
            return Optional.empty();
        }
    }

    public Cluster getCluster() {
        return ClusterFactory.createClusterForSingleServerWithoutPassWord(CLUSTER_IP, CLUSTER_PORT_TEST);
    }

    private void sleep(long sleepMs) {
        try {
            Thread.sleep(sleepMs);
        } catch(InterruptedException interruptedException) {
            Throwables.propagate(interruptedException);
        }
    }

    public CassandraTypesProvider getTypesProvider() {
        return typesProvider;
    }
}