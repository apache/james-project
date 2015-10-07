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
package org.apache.james.mailbox.cassandra;


import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.base.Throwables;
import org.apache.james.mailbox.cassandra.mail.utils.FunctionRunnerWithRetry;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Class that will creates a single instance of Cassandra session.
 */
public final class CassandraClusterSingleton {
    private static final String CLUSTER_IP = "localhost";
    private static final int CLUSTER_PORT_TEST = 9142;
    private static final String KEYSPACE_NAME = "apache_james";
    private static final int REPLICATION_FACTOR = 1;

    private static final long SLEEP_BEFORE_RETRY = 200;
    private static final int MAX_RETRY = 200;

    private static final Logger LOG = LoggerFactory.getLogger(CassandraClusterSingleton.class);
    private static CassandraClusterSingleton cluster = null;
    private Session session;
    private CassandraTypesProvider typesProvider;

    /**
     * Builds a MiniCluster instance.
     *
     * @return the {@link CassandraClusterSingleton} instance
     * @throws RuntimeException
     */
    public static synchronized CassandraClusterSingleton build() throws RuntimeException {
        LOG.info("Retrieving cluster instance.");
        if (cluster == null) {
            cluster = new CassandraClusterSingleton();
        }
        return cluster;
    }

    private CassandraClusterSingleton() throws RuntimeException {
        try {
            EmbeddedCassandraServerHelper.startEmbeddedCassandra();
            session = new FunctionRunnerWithRetry(MAX_RETRY)
                .executeAndRetrieveObject(CassandraClusterSingleton.this::tryInitializeSession);
            typesProvider = new CassandraTypesProvider(session);
        } catch(Exception exception) {
            Throwables.propagate(exception);
        }
    }

    public Session getConf() {
        return session;
    }

    public void ensureAllTables() {
        new CassandraTableManager(session).ensureAllTables();
    }

    public void clearAllTables() {
        new CassandraTableManager(session).clearAllTables();
    }

    private Optional<Session> tryInitializeSession() {
        try {
            Cluster cluster = ClusterFactory.createClusterForSingleServerWithoutPassWord(CLUSTER_IP, CLUSTER_PORT_TEST);
            Cluster clusterWithInitializedKeyspace = ClusterWithKeyspaceCreatedFactory
                .clusterWithInitializedKeyspace(cluster, KEYSPACE_NAME, REPLICATION_FACTOR);
            return Optional.of(SessionFactory.createSession(clusterWithInitializedKeyspace, KEYSPACE_NAME));
        } catch (NoHostAvailableException exception) {
            sleep(SLEEP_BEFORE_RETRY);
            return Optional.empty();
        }
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
