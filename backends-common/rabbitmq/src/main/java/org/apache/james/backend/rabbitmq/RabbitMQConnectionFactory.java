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
package org.apache.james.backend.rabbitmq;

import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import org.apache.james.util.retry.RetryExecutorUtil;

import com.nurkiewicz.asyncretry.AsyncRetryExecutor;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQConnectionFactory {

    private final AsyncRetryExecutor executor;
    private final ConnectionFactory connectionFactory;

    private final int maxRetries;
    private final int minDelay;

    @Inject
    public RabbitMQConnectionFactory(RabbitMQConfiguration rabbitMQConfiguration, AsyncRetryExecutor executor) {
        this.executor = executor;
        this.connectionFactory = from(rabbitMQConfiguration);
        this.maxRetries = rabbitMQConfiguration.getMaxRetries();
        this.minDelay = rabbitMQConfiguration.getMinDelay();
    }

    private ConnectionFactory from(RabbitMQConfiguration rabbitMQConfiguration) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(rabbitMQConfiguration.getUri());
            connectionFactory.useNio();
            return connectionFactory;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Connection create() {
        try {
            return RetryExecutorUtil.retryOnExceptions(executor, maxRetries, minDelay, Exception.class)
                    .getWithRetry(context -> connectionFactory.newConnection())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
