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

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class CassandraClusterExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {
    private final DockerCassandraExtension cassandraExtension;
    private final CassandraDataDefinition cassandraDataDefinition;
    private CassandraCluster cassandraCluster;

    public CassandraClusterExtension(CassandraDataDefinition cassandraDataDefinition) {
        this.cassandraDataDefinition = cassandraDataDefinition;
        this.cassandraExtension = new DockerCassandraExtension();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        if (testClass.getEnclosingClass() == null) {
            cassandraExtension.beforeAll(extensionContext);
            start();
        }
    }

    public ClusterConfiguration.Builder clusterConfiguration() {
        return cassandraExtension.clusterConfiguration();
    }

    public void pause() {
        cassandraExtension.getDockerCassandra().getContainer().pause();
    }

    public void unpause() {
        cassandraExtension.getDockerCassandra().getContainer().unpause();
    }

    private void start() {
        cassandraCluster = CassandraCluster.create(cassandraDataDefinition, cassandraExtension.getDockerCassandra().getHost());
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        DockerCassandraSingleton.incrementTestsPlayed();
        DockerCassandraSingleton.restartAfterMaxTestsPlayed(
            cassandraCluster::close,
            this::start);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        cassandraCluster.clearTables();
        cassandraCluster.getConf().resetInstrumentation();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        Class<?> testClass = extensionContext.getRequiredTestClass();
        if (testClass.getEnclosingClass() == null) {
            cassandraCluster.close();
            cassandraExtension.afterAll(extensionContext);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        return paramType.isAssignableFrom(CassandraCluster.class)
            || paramType.isAssignableFrom(DockerCassandra.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> paramType = parameterContext.getParameter().getType();
        if (paramType.isAssignableFrom(CassandraCluster.class)) {
            return cassandraCluster;
        } else if (paramType.isAssignableFrom(DockerCassandra.class)) {
            return DockerCassandraSingleton.singleton;
        }
        throw new IllegalArgumentException("Unsupported parameter type " + paramType.getName());
    }

    public CassandraCluster getCassandraCluster() {
        return cassandraCluster;
    }
}
