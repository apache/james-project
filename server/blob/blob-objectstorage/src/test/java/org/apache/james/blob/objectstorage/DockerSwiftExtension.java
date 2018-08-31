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

import static org.testcontainers.containers.wait.strategy.Wait.forHttp;

import java.net.URI;

import org.apache.james.util.docker.RateLimiters;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

public class DockerSwiftExtension implements ParameterResolver, BeforeAllCallback,
    AfterAllCallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerSwiftExtension.class);
    private static final String SWIFT_DOCKER_IMAGE = "jeantil/openstack-keystone-swift:pike";
    private static final int SWIFT_PORT = 8080;
    private static final int KEYSTONE_ADMIN_PORT = 35357;

    private final GenericContainer<?> swiftContainer;

    private DockerSwift dockerSwift;

    public DockerSwiftExtension() {
        swiftContainer = new GenericContainer<>(SWIFT_DOCKER_IMAGE)
            .withExposedPorts(KEYSTONE_ADMIN_PORT)
            .withExposedPorts(SWIFT_PORT)
            .waitingFor(
                new WaitAllStrategy().withStrategy(
                    forHttp("/v3")
                        .forPort(KEYSTONE_ADMIN_PORT)
                        .forStatusCode(200)
                        .withRateLimiter(RateLimiters.DEFAULT)
                ).withStrategy(
                    forHttp("/info")
                        .forPort(SWIFT_PORT)
                        .forStatusCode(200)
                        .withRateLimiter(RateLimiters.DEFAULT)
                )
            );
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        swiftContainer.start();
        Integer keystonePort = swiftContainer.getMappedPort(KEYSTONE_ADMIN_PORT);
        Integer swiftPort = swiftContainer.getMappedPort(SWIFT_PORT);
        String containerIpAddress = swiftContainer.getContainerIpAddress();
        Container.ExecResult execResult = swiftContainer.execInContainer("/swift/bin/register-swift-endpoint.sh", "http://" + containerIpAddress + ":" + swiftPort);
        if (!execResult.getStdout().isEmpty()) {
            LOGGER.debug(execResult.getStdout());
        }
        if (!execResult.getStderr().isEmpty()) {
            LOGGER.error(execResult.getStderr());
        }
        URI keystoneV2Endpoint =
            URI.create("http://" + containerIpAddress + ":" + keystonePort + "/v2.0");
        URI keystoneV3Endpoint =
            URI.create("http://" + containerIpAddress + ":" + keystonePort + "/v3");
        URI swiftEndpoint =
            URI.create("http://" + containerIpAddress + ":" + swiftPort + "/auth/v1.0");
        dockerSwift = new DockerSwift(keystoneV2Endpoint, keystoneV3Endpoint, swiftEndpoint);
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
}

