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

package org.apache.james.backends.rabbitmq;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

import com.google.common.primitives.Ints;
import com.rabbitmq.client.Connection;

public class RabbitMQWaitStrategy implements WaitStrategy {
    private final DockerRabbitMQ rabbitMQ;
    private final Duration timeout;

    public RabbitMQWaitStrategy(DockerRabbitMQ rabbitMQ, Duration timeout) {
        this.rabbitMQ = rabbitMQ;
        this.timeout = timeout;
    }

    @Override
    public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
        int seconds = Ints.checkedCast(this.timeout.getSeconds());

        Unreliables.retryUntilTrue(seconds, TimeUnit.SECONDS, this::isConnected);
    }

    private Boolean isConnected() throws IOException, TimeoutException {
        try (Connection connection = rabbitMQ.connectionFactory().newConnection()) {
            return connection.isOpen();
        }
    }

    @Override
    public WaitStrategy withStartupTimeout(Duration startupTimeout) {
        return new RabbitMQWaitStrategy(rabbitMQ, startupTimeout);
    }
}
