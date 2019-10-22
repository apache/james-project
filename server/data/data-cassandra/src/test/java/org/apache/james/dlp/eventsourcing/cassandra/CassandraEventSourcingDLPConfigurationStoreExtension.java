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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.dlp.eventsourcing.EventSourcingDLPConfigurationStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraEventSourcingDLPConfigurationStoreExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private final DockerCassandraExtension dockerCassandraExtension;
    private CassandraCluster cassandra;

    public CassandraEventSourcingDLPConfigurationStoreExtension() {
        dockerCassandraExtension = new DockerCassandraExtension();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        dockerCassandraExtension.beforeAll(context);
        cassandra = CassandraCluster.create(
            CassandraEventStoreModule.MODULE,
            dockerCassandraExtension.getDockerCassandra().getHost());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cassandra.clearTables();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        cassandra.closeCluster();
        dockerCassandraExtension.afterAll(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DLPConfigurationStore.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(DLPConfigurationModules.DLP_CONFIGURATION_STORE, DLPConfigurationModules.DLP_CONFIGURATION_CLEAR)
            .withoutNestedType();

        EventStoreDao eventStoreDao = new EventStoreDao(
            cassandra.getConf(),
            jsonEventSerializer);

        return new EventSourcingDLPConfigurationStore(new CassandraEventStore(eventStoreDao));
    }
}
