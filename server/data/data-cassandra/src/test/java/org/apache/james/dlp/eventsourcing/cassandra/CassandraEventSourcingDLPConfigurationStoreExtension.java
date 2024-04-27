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

package org.apache.james.dlp.eventsourcing.cassandra;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.eventsourcing.EventSourcingDLPConfigurationStore;
import org.apache.james.eventsourcing.eventstore.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraEventSourcingDLPConfigurationStoreExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private final CassandraClusterExtension cassandraExtension;

    public CassandraEventSourcingDLPConfigurationStoreExtension() {
        cassandraExtension = new CassandraClusterExtension(CassandraEventStoreModule.MODULE());
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        cassandraExtension.beforeAll(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cassandraExtension.afterEach(context);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        cassandraExtension.afterAll(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DLPConfigurationStore.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(DLPConfigurationModules.DLP_CONFIGURATION_STORE, DLPConfigurationModules.DLP_CONFIGURATION_CLEAR)
            .withoutNestedType();

        EventStoreDao eventStoreDao = new EventStoreDao(
            cassandraExtension.getCassandraCluster().getConf(),
            jsonEventSerializer);

        return new EventSourcingDLPConfigurationStore(new CassandraEventStore(eventStoreDao));
    }
}
