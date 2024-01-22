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

package org.apache.james.crowdsec;

import java.util.UUID;

import org.apache.james.util.docker.RateLimiters;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.utility.MountableFile;

public class HAProxyExtension {
    private static final String HAPROXY_IMAGE = "haproxytech/haproxy-alpine:2.9.1";
    private static final int SMTP_PORT = 25;
    private static final int POP3_PORT = 110;
    private static final int IMAP_PORT = 143;

    private final GenericContainer<?> haproxyContainer;

    public HAProxyExtension(MountableFile haProxyConfigFile) {
        this.haproxyContainer = new GenericContainer<>(HAPROXY_IMAGE)
            .withCreateContainerCmdModifier(cmd -> cmd.withName("james-haproxy-test-" + UUID.randomUUID()))
            .withExposedPorts(SMTP_PORT, POP3_PORT, IMAP_PORT)
            .withCopyFileToContainer(haProxyConfigFile, "/usr/local/etc/haproxy/")
            .waitingFor(new HostPortWaitStrategy().withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
    }

    public void start() {
        if (!haproxyContainer.isRunning()) {
            haproxyContainer.start();
        }
    }

    public void stop() {
        if (haproxyContainer.isRunning()) {
            haproxyContainer.stop();
        }
    }

    public int getProxiedSmtpPort() {
        return haproxyContainer.getMappedPort(SMTP_PORT);
    }

    public int getProxiedImapPort() {
        return haproxyContainer.getMappedPort(IMAP_PORT);
    }

    public int getProxiedPop3Port() {
        return haproxyContainer.getMappedPort(POP3_PORT);
    }

    public GenericContainer<?> getHaproxyContainer() {
        return this.haproxyContainer;
    }
}
