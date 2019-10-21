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

package org.apache.james.eventsourcing.eventstore.cassandra;

import java.util.Set;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.collect.ImmutableSet;

public class CassandraEventStoreExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private final Set<EventDTOModule<?, ?>> modules;
    private CassandraClusterExtension cassandra;
    private EventStoreDao eventStoreDao;

    public CassandraEventStoreExtension(@SuppressWarnings("rawtypes") EventDTOModule... modules) {
        this(new CassandraClusterExtension(CassandraEventStoreModule.MODULE), ImmutableSet.copyOf(modules));
    }

    public CassandraEventStoreExtension(CassandraClusterExtension cassandra, Set<EventDTOModule<?, ?>> module) {
        this.cassandra = cassandra;
        this.modules = module;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        cassandra.beforeAll(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        cassandra.afterAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        JsonEventSerializer jsonEventSerializer = new JsonEventSerializer(modules.toArray(new EventDTOModule[0]));

        eventStoreDao = new EventStoreDao(cassandra.getCassandraCluster().getConf(), jsonEventSerializer);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cassandra.afterEach(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == EventStore.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return new CassandraEventStore(eventStoreDao);
    }
}
