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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.fge.lambdas.Throwing;

public class RspamdExtension implements GuiceModuleTestExtension {
    public static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(5);
    public static final String PASSWORD = "admin";

    private static final DockerImageName RSPAMD_IMAGE = DockerImageName.parse("a16bitsysop/rspamd").withTag("3.5-r7-alpine3.18.2-r0");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis").withTag("7.0.12");
    private static final DockerImageName CLAMAV_IMAGE = DockerImageName.parse("clamav/clamav").withTag("1.1");
    private static final int RSPAMD_DEFAULT_PORT = 11334;
    private static final int REDIS_DEFAULT_PORT = 6379;
    private static final int CLAMAV_DEFAULT_PORT = 3310;

    private final GenericContainer<?> rspamdContainer;
    private final GenericContainer<?> redisContainer;
    private final GenericContainer<?> clamAVContainer;
    private final Network network;

    public RspamdExtension() {
        this.network = Network.newNetwork();
        this.redisContainer = redisContainer(network);
        this.clamAVContainer = clamAVContainer(network);
        this.rspamdContainer = rspamdContainer(network);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        if (!rspamdContainer.isRunning()) {
            rspamdContainer.start();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        rspamdContainer.stop();
        Runnables.runParallel(redisContainer::stop, clamAVContainer::stop);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        redisFlushAll();
    }

    public GenericContainer<?> rspamdContainer(Network network) {
        return new GenericContainer<>(RSPAMD_IMAGE)
            .withExposedPorts(RSPAMD_DEFAULT_PORT)
            .withEnv("REDIS", "redis")
            .withEnv("CLAMAV", "clamav")
            .withEnv("PASSWORD", PASSWORD)
            .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/antivirus.conf"), "/etc/rspamd/override.d/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/actions.conf"), "/etc/rspamd/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("rspamd-config/statistic.conf"), "/etc/rspamd/")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-rspamd-test-" + UUID.randomUUID()))
            .withNetwork(network)
            .dependsOn(redisContainer, clamAVContainer)
            .waitingFor(Wait.forHealthcheck())
            .withStartupTimeout(STARTUP_TIMEOUT);
    }


    public GenericContainer<?> redisContainer(Network network) {
        return new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_DEFAULT_PORT)
            .withNetwork(network)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-redis-test-" + UUID.randomUUID()))
            .withNetworkAliases("redis");
    }

    public GenericContainer<?> clamAVContainer(Network network) {
        return new GenericContainer<>(CLAMAV_IMAGE)
            .withExposedPorts(CLAMAV_DEFAULT_PORT)
            .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
            .withEnv("CLAMAV_NO_MILTERD", "true")
            .withNetwork(network)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("james-clamav-test-" + UUID.randomUUID()))
            .withNetworkAliases("clamav")
            .waitingFor(Wait.forHealthcheck())
            .withStartupTimeout(STARTUP_TIMEOUT);
    }


    public void redisFlushAll() {
        try {
            redisContainer.execInContainer("redis-cli", "flushall");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public URL rspamdURL() {
        return Throwing.supplier(() -> new URI("http://" +
            rspamdContainer.getHost() + ":" +
            rspamdContainer.getMappedPort(RSPAMD_DEFAULT_PORT) +
            "/").toURL()).get();
    }

    public URL getBaseUrl() {
        return rspamdURL();
    }

    public int rspamdPort() {
        return rspamdContainer.getMappedPort(RSPAMD_DEFAULT_PORT);
    }


    public void pause() {
        rspamdContainer.getDockerClient().pauseContainerCmd(rspamdContainer.getContainerId()).exec();
    }

    public void unPause() {
        rspamdContainer.getDockerClient().unpauseContainerCmd(rspamdContainer.getContainerId()).exec();
    }

    public boolean isPaused() {
        return rspamdContainer.getDockerClient().inspectContainerCmd(rspamdContainer.getContainerId())
            .exec()
            .getState()
            .getPaused();
    }

}
