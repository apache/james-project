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

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraSessionConfiguration;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.ClusterBuilder;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.common.base.Throwables;

public final class CassandraCluster implements AutoCloseable {

    private static final int REPLICATION_FACTOR = 1;

    private static final long SLEEP_BEFORE_RETRY = 200;
    private static final int MAX_RETRY = 2000;

    private final CassandraModule module;
    private Session session;
    private CassandraTypesProvider typesProvider;
    private Cluster cluster;
    private String keyspace;
    private CassandraSessionConfiguration cassandraSessionConfiguration;

    public static CassandraCluster create(CassandraModule module, String host, int port) {
        return new CassandraCluster(module, host, port);
    }
    
    @Inject
    private CassandraCluster(CassandraModule module, @Named("cassandraHost") String host, @Named("cassandraPort") int port) throws RuntimeException {
        this.module = module;
        try {
            cluster = ClusterBuilder.builder()
                .host(host)
                .port(port)
                .build();
            keyspace = new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(10);
            cassandraSessionConfiguration = () -> {
                PropertiesConfiguration conf = new PropertiesConfiguration();
                conf.addProperty("cassandra.nodes", host + ":" + port);
                conf.addProperty("cassandra.keyspace", keyspace);
                conf.addProperty("cassandra.replication.factor", 1);
                conf.addProperty("cassandra.retryConnection.maxRetries", 10);
                conf.addProperty("cassandra.retryConnection", 5000);
                return conf;
            };
            session = new FunctionRunnerWithRetry(MAX_RETRY).executeAndRetrieveObject(CassandraCluster.this::tryInitializeSession);
            typesProvider = new CassandraTypesProvider(module, session);
        } catch (Exception exception) {
            Throwables.propagate(exception);
        }
    }



    public Session getConf() {
        return session;
    }

    @PreDestroy
    public void clearAllTables() {
        session.close();
        cluster.close();
    }
    
    private Optional<Session> tryInitializeSession() {
        try {
            Cluster clusterWithInitializedKeyspace = ClusterWithKeyspaceCreatedFactory
                .config(getCluster(), keyspace)
                .replicationFactor(REPLICATION_FACTOR)
                .disableDurableWrites()
                .clusterWithInitializedKeyspace();
            return Optional.of(new SessionWithInitializedTablesFactory(cassandraSessionConfiguration, clusterWithInitializedKeyspace, module).createSession(clusterWithInitializedKeyspace, keyspace));
        } catch (NoHostAvailableException exception) {
            sleep(SLEEP_BEFORE_RETRY);
            return Optional.empty();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public Cluster getCluster() {
        return cluster;
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

    @Override
    public void close() {
        cluster.closeAsync();
    }
}
