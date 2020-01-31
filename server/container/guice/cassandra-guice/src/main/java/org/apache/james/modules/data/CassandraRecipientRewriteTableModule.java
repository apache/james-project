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
package org.apache.james.modules.data;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.CassandraRRTModule;
import org.apache.james.rrt.cassandra.CassandraRecipientRewriteTable;
import org.apache.james.rrt.cassandra.CassandraRecipientRewriteTableDAO;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class CassandraRecipientRewriteTableModule extends AbstractModule {
    @Override
    public void configure() {
        bind(CassandraRecipientRewriteTable.class).in(Scopes.SINGLETON);
        bind(CassandraRecipientRewriteTableDAO.class).in(Scopes.SINGLETON);
        bind(CassandraMappingsSourcesDAO.class).in(Scopes.SINGLETON);
        bind(RecipientRewriteTable.class).to(CassandraRecipientRewriteTable.class);
        bind(CanSendFromImpl.class).in(Scopes.SINGLETON);
        bind(CanSendFrom.class).to(CanSendFromImpl.class);
        Multibinder<CassandraModule> cassandraDataDefinitions = Multibinder.newSetBinder(binder(), CassandraModule.class);
        cassandraDataDefinitions.addBinding().toInstance(CassandraRRTModule.MODULE);
    }

    @ProvidesIntoSet
    InitializationOperation configureRecipientRewriteTable(ConfigurationProvider configurationProvider, CassandraRecipientRewriteTable recipientRewriteTable) {
        return InitilizationOperationBuilder
            .forClass(CassandraRecipientRewriteTable.class)
            .init(() -> recipientRewriteTable.configure(configurationProvider.getConfiguration("recipientrewritetable")));
    }
}
