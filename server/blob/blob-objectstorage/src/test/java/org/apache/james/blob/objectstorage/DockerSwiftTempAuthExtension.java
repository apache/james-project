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

package org.apache.james.blob.objectstorage;

import java.net.URI;

import org.apache.james.util.docker.RateLimiters;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

class DockerSwiftTempAuthExtension implements ParameterResolver, BeforeAllCallback, AfterAllCallback {
    public static final int SWIFT_PORT = 8080;
    private static GenericContainer<?> swiftContainer =
        new GenericContainer<>("bouncestorage/swift-aio:ea10837d")
            .withExposedPorts(SWIFT_PORT)
            .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.DEFAULT));
    private DockerSwift dockerSwift;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        swiftContainer.start();
        String host = swiftContainer.getContainerIpAddress();
        Integer port = swiftContainer.getMappedPort(SWIFT_PORT);
        dockerSwift = new DockerSwift(new URI("http", null, host, port, "/auth/v1.0", null, null));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        swiftContainer.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerSwift.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerSwift;
    }

    public static class DockerSwift {
        private final URI endpoint;

        public DockerSwift(URI endpoint) {
            this.endpoint = endpoint;
        }

        public URI getEndpoint() {
            return endpoint;
        }
    }
}
