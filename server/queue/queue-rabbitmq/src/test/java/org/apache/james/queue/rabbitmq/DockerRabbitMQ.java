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
package org.apache.james.queue.rabbitmq;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import com.rabbitmq.client.ConnectionFactory;

public class DockerRabbitMQ {

    private static final int DEFAULT_RABBITMQ_PORT = 5672;
    private static final String DEFAULT_RABBITMQ_HOSTNAME = "my-rabbit";
    private static final String DEFAULT_RABBITMQ_USERNAME = "guest";
    private static final String DEFAULT_RABBITMQ_PASSWORD = "guest";

    private GenericContainer<?> container;

    @SuppressWarnings("resource")
    public DockerRabbitMQ() {
        container = new GenericContainer<>("rabbitmq:3.7.5")
                .withCreateContainerCmdModifier(cmd -> cmd.withHostName(DEFAULT_RABBITMQ_HOSTNAME))
                .withExposedPorts(DEFAULT_RABBITMQ_PORT)
                .waitingFor(RabbitMQWaitStrategy.withDefaultTimeout(this));
    }

    public String getHostIp() {
        return container.getContainerIpAddress();
    }

    public Integer getPort() {
        return container.getMappedPort(DEFAULT_RABBITMQ_PORT);
    }

    public String getUsername() {
        return DEFAULT_RABBITMQ_USERNAME;
    }

    public String getPassword() {
        return DEFAULT_RABBITMQ_PASSWORD;
    }

    public ConnectionFactory connectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(getHostIp());
        connectionFactory.setPort(getPort());
        connectionFactory.setUsername(getUsername());
        connectionFactory.setPassword(getPassword());
        return connectionFactory;
    }

    public void start() {
        container.start();
    }

    public void stop() {
        container.stop();
    }

    public void restart() {
        DockerClientFactory.instance().client()
            .restartContainerCmd(container.getContainerId());
    }
}
