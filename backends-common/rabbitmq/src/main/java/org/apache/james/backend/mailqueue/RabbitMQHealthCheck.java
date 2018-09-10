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

package org.apache.james.backend.mailqueue;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQHealthCheck implements HealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQHealthCheck.class);
    private static final ComponentName COMPONENT_NAME = new ComponentName("RabbitMQ backend");

    private final ConnectionFactory connectionFactory;

    @Inject
    public RabbitMQHealthCheck(RabbitMQConfiguration configuration) throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        this.connectionFactory = new ConnectionFactory();
        this.connectionFactory.setUri(configuration.getUri());
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Result check() {
        try (Connection connection = connectionFactory.newConnection()) {
            if (connection.isOpen()) {
                return Result.healthy(COMPONENT_NAME);
            }
            LOGGER.error("The created connection was not opened");
            return Result.unhealthy(COMPONENT_NAME);
        } catch (Exception e) {
            LOGGER.error("Unhealthy RabbitMQ instances: could not establish a connection", e);
            return Result.unhealthy(COMPONENT_NAME);
        }
    }
}
