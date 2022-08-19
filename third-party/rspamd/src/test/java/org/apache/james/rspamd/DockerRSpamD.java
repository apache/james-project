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
import org.testcontainers.utility.MountableFile;

public class DockerRSpamD {
    public static final String PASSWORD = "admin";
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("a16bitsysop/rspamd");
    private static final String DEFAULT_TAG = "3.2-r2-alpine3.16.0-r0";
    private static final int DEFAULT_PORT = 11334;

    private final DockerRedis dockerRedis;
    private final DockerClamAV dockerClamAV;
    private final GenericContainer<?> container;
    private final Network network;

    public DockerRSpamD() {
        this.network = Network.newNetwork();
        this.dockerRedis = new DockerRedis(network);
        this.dockerClamAV = new DockerClamAV(network);
        this.container = createRspamD();
    }

    private GenericContainer<?> createRspamD() {
        return new GenericContainer<>(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG))
            .withExposedPorts(DEFAULT_PORT)
            .withEnv("REDIS", "redis")
            .withEnv("CLAMAV", "clamav")
            .withEnv("PASSWORD", PASSWORD)
            .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/antivirus.conf"), "/etc/rspamd/override.d/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/actions.conf"), "/etc/rspamd/")
            .withNetwork(network);
    }

    public Integer getPort() {
        return container.getMappedPort(DEFAULT_PORT);
    }

    public void start() {
        dockerClamAV.start();
        dockerRedis.start();
        if (!container.isRunning()) {
            container.start();
        }
    }

    public void stop() {
        container.stop();
        dockerRedis.stop();
        dockerClamAV.stop();
    }

    public void flushAll() {
        dockerRedis.flushAll();
    }
}
