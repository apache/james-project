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

package org.apache.james.eventsourcing.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraExtension;
import org.apache.james.backends.cassandra.DockerCassandraExtension.DockerCassandra;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.eventsourcing.EventStore;
import org.apache.james.eventsourcing.cassandra.dto.TestEventDTOModule;
import org.apache.james.mailbox.quota.cassandra.dto.QuotaThresholdChangedEventDTOModule;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraEventStoreExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private final DockerCassandraExtension dockerCassandraExtension;
    private CassandraCluster cassandra;
    private DockerCassandra dockerCassandra;
    private EventStoreDao eventStoreDao;

    public CassandraEventStoreExtension() {
        dockerCassandraExtension = new DockerCassandraExtension();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        dockerCassandraExtension.beforeAll(context);
        dockerCassandra = dockerCassandraExtension.getDockerCassandra();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        dockerCassandraExtension.afterAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        cassandra = CassandraCluster.create(
                new CassandraEventStoreModule(), dockerCassandra.getIp(), dockerCassandra.getBindingPort());

        JsonEventSerializer jsonEventSerializer = new JsonEventSerializer(
            new QuotaThresholdChangedEventDTOModule(),
            new TestEventDTOModule());

        eventStoreDao = new EventStoreDao(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION,
            jsonEventSerializer);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        cassandra.close();
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
