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

import org.apache.james.util.Host;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DockerCassandraExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, ParameterResolver {

    private final DockerCassandraRule cassandraContainer;
    private DockerCassandra dockerCassandra;

    public DockerCassandraExtension() {
        cassandraContainer = new DockerCassandraRule();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        cassandraContainer.start();
        dockerCassandra = new DockerCassandra(cassandraContainer);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        cassandraContainer.stop();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
      cassandraContainer.before();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerCassandra.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerCassandra;
    }

    public DockerCassandra getDockerCassandra() {
        return dockerCassandra;
    }
    
    public static class DockerCassandra {
        private final DockerCassandraRule container;

        private DockerCassandra(DockerCassandraRule container) {
            this.container = container;
        }

        public Host getHost() {
            return container.getHost();
        }

        public DockerCassandraRule getContainer() {
            return container;
        }
    }

}
