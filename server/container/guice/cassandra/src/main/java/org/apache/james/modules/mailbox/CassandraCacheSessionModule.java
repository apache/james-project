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

import java.util.Set;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.ResilientClusterProvider;
import org.apache.james.backends.cassandra.init.SessionWithInitializedTablesFactory;
import org.apache.james.backends.cassandra.init.configuration.InjectionNames;
import org.apache.james.backends.cassandra.init.configuration.KeyspaceConfiguration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class CassandraCacheSessionModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(InitializedCacheCluster.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), CassandraModule.class, Names.named(InjectionNames.CACHE));
    }

    @Named(InjectionNames.CACHE)
    @Provides
    @Singleton
    KeyspaceConfiguration provideCacheKeyspaceConfiguration(KeyspacesConfiguration keyspacesConfiguration) {
        return keyspacesConfiguration.cacheKeyspaceConfiguration();
    }

    @Singleton
    @Named(InjectionNames.CACHE)
    @Provides
    CqlSession provideSession(@Named(InjectionNames.CACHE) KeyspaceConfiguration keyspaceConfiguration,
                              InitializedCacheCluster cluster,
                              @Named(InjectionNames.CACHE) CassandraModule module) {
        return new SessionWithInitializedTablesFactory(cluster.cluster, module).get();
    }

    @Named(InjectionNames.CACHE)
    @Provides
    @Singleton
    CassandraModule composeCacheDefinitions(@Named(InjectionNames.CACHE) Set<CassandraModule> modules) {
        return CassandraModule.aggregateModules(modules);
    }

    static class InitializedCacheCluster {
        private final CqlSession cluster;

        @Inject
        private InitializedCacheCluster(ResilientClusterProvider sessionProvider, KeyspacesConfiguration keyspacesConfiguration) {
            this.cluster = sessionProvider.get(keyspacesConfiguration.cacheKeyspaceConfiguration());
        }
    }
}
