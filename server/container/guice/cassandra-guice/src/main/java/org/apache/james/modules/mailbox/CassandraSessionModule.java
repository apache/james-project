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
package org.apache.james.modules.mailbox;

import java.io.FileNotFoundException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.ClusterWithKeyspaceCreatedFactory;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.filesystem.api.FileSystem;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class CassandraSessionModule extends AbstractModule {

    private static final int DEFAULT_CONNECTION_MAX_RETRIES = 10;
    private static final int DEFAULT_CONNECTION_MIN_DELAY = 5000;

    @Override
    protected void configure() {
        bind(ScheduledExecutorService.class).toProvider(ScheduledExecutorServiceProvider.class);

        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().to(CassandraZonedDateTimeModule.class);
    }
    
    @Provides
    @Singleton
    CassandraModule composeDataDefinitions(Set<CassandraModule> modules) {
        return new CassandraModuleComposite(modules.toArray(new CassandraModule[0]));
    }

    @Provides
    @Singleton
    Session provideSession(FileSystem fileSystem, Cluster cluster, CassandraModule cassandraModule)
            throws FileNotFoundException, ConfigurationException{
        PropertiesConfiguration configuration = getConfiguration(fileSystem);
        String keyspace = configuration.getString("cassandra.keyspace");
        return new SessionWithInitializedTablesFactory(cassandraModule).createSession(cluster, keyspace);
    }

    @Provides
    @Singleton
    Cluster provideCluster(FileSystem fileSystem, AsyncRetryExecutor executor) throws FileNotFoundException, ConfigurationException, ExecutionException, InterruptedException {
        PropertiesConfiguration configuration = getConfiguration(fileSystem);

        return getRetryer(executor, configuration)
                .getWithRetry(ctx -> ClusterWithKeyspaceCreatedFactory.clusterWithInitializedKeyspace(
                        ClusterFactory.createClusterForSingleServerWithoutPassWord(
                                configuration.getString("cassandra.ip"),
                                configuration.getInt("cassandra.port")),
                        configuration.getString("cassandra.keyspace"),
                        configuration.getInt("cassandra.replication.factor")))
                .get();
    }

    private static AsyncRetryExecutor getRetryer(AsyncRetryExecutor executor, PropertiesConfiguration configuration) {
        return executor.retryOn(NoHostAvailableException.class)
                .withProportionalJitter()
                .withMaxRetries(configuration.getInt("cassandra.retryConnection.maxRetries", DEFAULT_CONNECTION_MAX_RETRIES))
                .withMinDelay(configuration.getInt("cassandra.retryConnection.minDelay", DEFAULT_CONNECTION_MIN_DELAY));
    }

    @Provides
    private AsyncRetryExecutor provideAsyncRetryExecutor(ScheduledExecutorService scheduler) {
        return new AsyncRetryExecutor(scheduler);
    }

    private PropertiesConfiguration getConfiguration(FileSystem fileSystem) throws FileNotFoundException, ConfigurationException {
        return new PropertiesConfiguration(fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + "cassandra.properties"));
    }

}