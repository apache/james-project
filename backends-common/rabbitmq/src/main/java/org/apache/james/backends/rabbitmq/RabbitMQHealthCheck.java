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

import javax.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQHealthCheck implements HealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQHealthCheck.class);
    private static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQ backend");

    private final SimpleConnectionPool connectionPool;
    private final ReactorRabbitMQChannelPool rabbitChannelPoolImpl;

    @Inject
    public RabbitMQHealthCheck(SimpleConnectionPool connectionPool, ReactorRabbitMQChannelPool rabbitChannelPoolImpl) {
        this.connectionPool = connectionPool;
        this.rabbitChannelPoolImpl = rabbitChannelPoolImpl;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Result check() {
        try {
            if (connectionPool.tryConnection() && rabbitChannelPoolImpl.tryChannel()) {
                return Result.healthy(COMPONENT_NAME);
            } else {
                String message = "The created connection was not opened";
                LOGGER.error("Unhealthy RabbitMQ instances: {}", message);
                return Result.unhealthy(COMPONENT_NAME, message);
            }
        } catch (Exception e) {
            LOGGER.error("Unhealthy RabbitMQ instances: could not establish a connection", e);
            return Result.unhealthy(COMPONENT_NAME, e.getMessage());
        }
    }
}
