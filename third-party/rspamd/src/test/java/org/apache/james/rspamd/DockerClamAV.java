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

package org.apache.james.rspamd;

import java.time.Duration;
import java.util.UUID;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class DockerClamAV {
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("clamav/clamav").withTag("1.1");
    private static final int DEFAULT_PORT = 3310;

    private final GenericContainer<?> container;

    public DockerClamAV(Network network) {
        this.container = new GenericContainer<>(DEFAULT_IMAGE_NAME)
            .withExposedPorts(DEFAULT_PORT)
            .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
            .withEnv("CLAMAV_NO_MILTERD", "true")
            .withNetwork(network)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-clamav-test-" + UUID.randomUUID()))
            .withNetworkAliases("clamav")
            .waitingFor(Wait.forHealthcheck()
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public void start() {
        if (!container.isRunning()) {
            container.start();
        }
    }
}
