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

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraEventStoreExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private CassandraClusterExtension cassandra;
    private final JsonEventSerializer eventSerializer;
    private EventStoreDao eventStoreDao;

    public CassandraEventStoreExtension(JsonEventSerializer eventSerializer) {
        this(new CassandraClusterExtension(CassandraEventStoreModule.MODULE), eventSerializer);
    }

    public CassandraEventStoreExtension(CassandraClusterExtension cassandra, JsonEventSerializer eventSerializer) {
        this.cassandra = cassandra;
        this.eventSerializer = eventSerializer;
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
        eventStoreDao = new EventStoreDao(cassandra.getCassandraCluster().getConf(), eventSerializer);
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
