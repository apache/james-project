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

package org.apache.james.queue.activemq;

import jakarta.inject.Inject;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;

import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ActiveMQHealthCheck implements HealthCheck {
    public static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQHealthCheck.class);
    public static final ComponentName COMPONENT_NAME = new ComponentName("Embedded ActiveMQ");
    private final ConnectionFactory connectionFactory;

    @Inject
    public ActiveMQHealthCheck(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Publisher<Result> check() {
        return Mono.fromCallable(() -> {
            try {
                Connection connection = connectionFactory.createConnection();
                try {
                    Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                    session.close();
                } finally {
                    connection.close();
                }
                return Result.healthy(COMPONENT_NAME);
            } catch (Exception e) {
                LOGGER.warn("{} is unhealthy. {}", COMPONENT_NAME.getName(), e.getMessage());
                return Result.unhealthy(COMPONENT_NAME, e.toString(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

}

