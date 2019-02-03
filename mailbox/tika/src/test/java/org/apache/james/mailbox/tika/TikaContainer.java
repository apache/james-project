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
package org.apache.james.mailbox.tika;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.apache.james.util.docker.SwarmGenericContainer;
import org.junit.rules.ExternalResource;
import org.testcontainers.containers.wait.strategy.Wait;

import com.google.common.primitives.Ints;

public class TikaContainer extends ExternalResource {
    
    private static final int DEFAULT_TIKA_PORT = 9998;
    private static final int DEFAULT_TIMEOUT_IN_MS = Ints.checkedCast(TimeUnit.MINUTES.toMillis(3));

    private final SwarmGenericContainer tika;

    public TikaContainer() {
        tika = new SwarmGenericContainer(Images.TIKA)
                .withExposedPorts(DEFAULT_TIKA_PORT)
                .waitingFor(Wait.forHttp("/tika").withRateLimiter(RateLimiters.TWENTIES_PER_SECOND))
                .withStartupTimeout(Duration.ofSeconds(30));
    }

    @Override
    protected void before() throws Throwable {
        start();
    }

    public void start() {
        tika.start();
    }

    @Override
    protected void after() {
        stop();
    }

    public void stop() {
        tika.stop();
    }

    public String getIp() {
        return tika.getHostIp();
    }

    public int getPort() {
        return tika.getMappedPort(DEFAULT_TIKA_PORT);
    }

    public int getTimeoutInMillis() {
        return DEFAULT_TIMEOUT_IN_MS;
    }
}
