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

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraClusterExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {
    private final DockerCassandraExtension cassandraExtension;
    private final CassandraModule cassandraModule;
    private CassandraCluster cassandraCluster;

    public CassandraClusterExtension(CassandraModule cassandraModule) {
        this.cassandraModule = cassandraModule;
        this.cassandraExtension = new DockerCassandraExtension();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        cassandraExtension.beforeAll(extensionContext);
        cassandraCluster = CassandraCluster.create(cassandraModule, cassandraExtension.getDockerCassandra().getHost());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        cassandraCluster.clearTables();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        cassandraCluster.close();
        cassandraExtension.afterAll(extensionContext);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == CassandraCluster.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return cassandraCluster;
    }

    public CassandraCluster getCassandraCluster() {
        return cassandraCluster;
    }
}
