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

package org.apache.james.backends.cassandra;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.cassandra.init.ClusterFactory;
import org.apache.james.backends.cassandra.init.configuration.ClusterConfiguration;
import org.apache.james.util.Host;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

import com.google.common.primitives.Ints;

public class CassandraWaitStrategy implements WaitStrategy {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
    private final GenericContainer<?> cassandraContainer;
    private final Duration timeout;

    public CassandraWaitStrategy(GenericContainer<?> cassandraContainer) {
        this(cassandraContainer, DEFAULT_TIMEOUT);
    }

    public CassandraWaitStrategy(GenericContainer<?> cassandraContainer, Duration timeout) {
        this.cassandraContainer = cassandraContainer;
        this.timeout = timeout;
    }

    @Override
    public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
        Unreliables.retryUntilTrue(Ints.checkedCast(timeout.getSeconds()), TimeUnit.SECONDS, () -> {
                try {
                    ClusterFactory.createWithoutKeyspace(ClusterConfiguration.builder()
                        .host(Host.from(cassandraContainer.getHost(), cassandraContainer.getMappedPort(9042)))
                        .username("cassandra")
                        .password("cassandra")
                        .maxRetry(1)
                        .build())
                        .forceCloseAsync();

                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        );
    }

    @Override
    public WaitStrategy withStartupTimeout(Duration startupTimeout) {
        return new CassandraWaitStrategy(cassandraContainer, startupTimeout);
    }
}
