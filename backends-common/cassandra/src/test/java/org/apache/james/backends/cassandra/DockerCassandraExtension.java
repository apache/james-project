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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;

public class DockerCassandraExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private final DockerCassandraRule cassandraContainer;
    private DockerCassandra dockerCassandra;

    public DockerCassandraExtension() {
        cassandraContainer = new DockerCassandraRule();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        cassandraContainer.start();
        dockerCassandra = DockerCassandra.from(cassandraContainer.getRawContainer());
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        cassandraContainer.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerCassandra.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerCassandra;
    }
    
    public static class DockerCassandra {
        
        private static final int CASSANDRA_PORT = 9042;

        public static DockerCassandra from(GenericContainer<?> cassandraContainer) {
            return new DockerCassandra(cassandraContainer.getContainerIpAddress(), cassandraContainer.getMappedPort(CASSANDRA_PORT));
        }

        private final String ip;
        private final int bindingPort;

        private DockerCassandra(String ip, int bindingPort) {
            this.ip = ip;
            this.bindingPort = bindingPort;
        }

        public String getIp() {
            return ip;
        }
    
        public int getBindingPort() {
            return bindingPort;
        }
    }

}
