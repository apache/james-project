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

package org.apache.james.util.scanner;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.WaitStrategy;

import com.google.common.primitives.Ints;

public class SpamAssassinWaitStrategy implements WaitStrategy {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private Duration timeout = DEFAULT_TIMEOUT;

    public SpamAssassinWaitStrategy() {
        this(DEFAULT_TIMEOUT);
    }

    public SpamAssassinWaitStrategy(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public void waitUntilReady(@SuppressWarnings("rawtypes") GenericContainer container) {
        Unreliables.retryUntilTrue(Ints.checkedCast(timeout.getSeconds()), TimeUnit.SECONDS, () -> {
                try {
                    return container
                        .execInContainer("spamassassin", "-V")
                        .getStdout()
                        .contains("SpamAssassin version 3.4.1");
                } catch (IOException | InterruptedException e) {
                    return false;
                }
            }
        );
    }

    @Override
    public WaitStrategy withStartupTimeout(Duration startupTimeout) {
        return new SpamAssassinWaitStrategy(startupTimeout);
    }
}
