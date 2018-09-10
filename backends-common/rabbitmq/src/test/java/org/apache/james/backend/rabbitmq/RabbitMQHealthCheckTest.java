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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.rabbitmq.client.ConnectionFactory;

@ExtendWith(DockerRabbitMQExtension.class)
class RabbitMQHealthCheckTest {
    private RabbitMQHealthCheck healthCheck;

    @BeforeEach
    void setUp(DockerRabbitMQ rabbitMQ) throws Exception {
        URI amqpUri = URI.create("amqp://" + rabbitMQ.getHostIp() + ":" + rabbitMQ.getPort());
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setUri(amqpUri);
        healthCheck = new RabbitMQHealthCheck(
            new RabbitChannelPool(connectionFactory.newConnection()));
    }

    @Test
    void checkShouldReturnHealthyWhenRabbitMQIsRunning() {
        Result check = healthCheck.check();

        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenRabbitMQIsNotRunning(DockerRabbitMQ rabbitMQ) throws Exception {
        rabbitMQ.stopApp();

        Result check = healthCheck.check();

        assertThat(check.isHealthy()).isFalse();
    }

    @Test
    @Disabled("connection don't recover instantly, we should try several time (depending on heartbeat rabbit conf")
    void checkShouldDetectWhenRabbitMQRecovered(DockerRabbitMQ rabbitMQ) throws Exception {
        rabbitMQ.stopApp();
        healthCheck.check();

        rabbitMQ.startApp();

        Result check = healthCheck.check();
        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldDetectWhenRabbitMQFail(DockerRabbitMQ rabbitMQ) throws Exception {
        healthCheck.check();

        rabbitMQ.stopApp();

        Result check = healthCheck.check();
        assertThat(check.isHealthy()).isFalse();
    }
}