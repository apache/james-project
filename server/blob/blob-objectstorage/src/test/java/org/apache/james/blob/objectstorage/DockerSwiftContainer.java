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

import org.apache.james.util.Host;
import org.apache.james.util.docker.RateLimiters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import com.github.fge.lambdas.Throwing;

public class DockerSwiftContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerSwiftContainer.class);
    private static final String SWIFT_DOCKER_IMAGE = "linagora/openstack-keystone-swift:pike";
    private static final int SWIFT_PORT = 8080;
    private static final int KEYSTONE_ADMIN_PORT = 35357;
    private final GenericContainer<?> swiftContainer;
    private DockerSwift dockerSwift;

    public DockerSwiftContainer() {
        this.swiftContainer = new GenericContainer<>(SWIFT_DOCKER_IMAGE);
        this.swiftContainer
            .withExposedPorts(KEYSTONE_ADMIN_PORT)
            .withExposedPorts(SWIFT_PORT)
            .withLogConsumer(DockerSwiftContainer::displayDockerLog)
            .waitingFor(
                new WaitAllStrategy()
                    .withStrategy(
                        forHttp("/v3")
                            .forPort(KEYSTONE_ADMIN_PORT)
                            .forStatusCode(200)
                            .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND)
                    ).withStrategy(
                    forHttp("/info")
                        .forPort(SWIFT_PORT)
                        .forStatusCode(200)
                        .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND)
                )
            );

    }

    public void start() {
        swiftContainer.start();
        Integer swiftPort = swiftContainer.getMappedPort(SWIFT_PORT);
        String containerIpAddress = swiftContainer.getContainerIpAddress();
        Container.ExecResult execResult =
            Throwing.supplier(() ->
                swiftContainer.execInContainer(
                    "/swift/bin/register-swift-endpoint.sh",
                    "http://" + containerIpAddress + ":" + swiftPort))
                .sneakyThrow()
                .get();
        if (!execResult.getStdout().isEmpty()) {
            LOGGER.debug(execResult.getStdout());
        }
        if (!execResult.getStderr().isEmpty()) {
            LOGGER.error(execResult.getStderr());
        }
        URI keystoneV2Endpoint =
            URI.create("http://" + getKeystoneHost() + "/v2.0");
        URI keystoneV3Endpoint =
            URI.create("http://" + getKeystoneHost() + "/v3");
        URI swiftEndpoint =
            URI.create("http://" + getSwiftHost() + "/auth/v1.0");
        dockerSwift = new DockerSwift(keystoneV2Endpoint, keystoneV3Endpoint, swiftEndpoint);

    }

    public void stop() {
        swiftContainer.stop();
    }

    public Host getKeystoneHost() {
        return Host.from(
            getIp(),
            getKeystonePort());
    }

    public Host getSwiftHost() {
        return Host.from(
            getIp(),
            getSwiftPort());
    }

    public String getIp() {
        return swiftContainer.getContainerIpAddress();
    }

    public int getKeystonePort() {
        return swiftContainer.getMappedPort(KEYSTONE_ADMIN_PORT);
    }

    public int getSwiftPort() {
        return swiftContainer.getMappedPort(SWIFT_PORT);
    }

    public DockerSwift dockerSwift() {
        return dockerSwift;
    }

    public GenericContainer<?> getRawContainer() {
        return swiftContainer;
    }

    private static void displayDockerLog(OutputFrame outputFrame) {
        LOGGER.info(outputFrame.getUtf8String());
    }

}
