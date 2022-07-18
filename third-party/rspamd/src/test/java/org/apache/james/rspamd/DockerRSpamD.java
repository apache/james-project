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

import org.apache.james.rate.limiter.DockerRedis;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class DockerRSpamD {
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("a16bitsysop/rspamd");
    private static final String DEFAULT_TAG = "3.2-r2-alpine3.16.0-r0";
    private static final int DEFAULT_PORT = 11334;

    private final DockerRedis dockerRedis;
    private final GenericContainer<?> container;
    private Network network;

    public DockerRSpamD() {
        this.network = Network.newNetwork();
        this.dockerRedis = new DockerRedis(network);
        this.container = createRspamD();
    }

    private GenericContainer<?> createRspamD() {
        return new GenericContainer<>(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG))
            .withExposedPorts(DEFAULT_PORT)
            .withEnv("REDIS", "redis")
            .withNetwork(network);
    }

    public Integer getPort() {
        return container.getMappedPort(DEFAULT_PORT);
    }

    public void start() {
        dockerRedis.start();
        if (!container.isRunning()) {
            container.start();
        }
    }

    public void stop() {
        container.stop();
        dockerRedis.stop();
    }

    public void flushAll() {
        dockerRedis.flushAll();
    }
}
