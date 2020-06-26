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

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import org.junit.rules.ExternalResource;

import com.github.fge.lambdas.runnable.ThrowingRunnable;

public class DockerRabbitMQTestRule extends ExternalResource {

    private DockerRabbitMQ dockerRabbitMQ;

    public DockerRabbitMQTestRule() {
        dockerRabbitMQ = DockerRabbitMQSingleton.SINGLETON;
    }

    @Override
    protected void before() throws Throwable {
        dockerRabbitMQ.start();
    }

    @Override
    protected void after() {
        performQuietly(() -> {
            RabbitMQManagementAPI managementAPI = managementAPI();
            managementAPI.listQueues()
                .forEach(queue -> managementAPI.deleteQueue("/", queue.getName()));
        });
    }

    private void performQuietly(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            // ignore
        }
    }

    private RabbitMQManagementAPI managementAPI() throws Exception {
        return RabbitMQManagementAPI.from(RabbitMQConfiguration.builder()
            .amqpUri(dockerRabbitMQ.amqpUri())
            .managementUri(dockerRabbitMQ.managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build());
    }

    public DockerRabbitMQ getDockerRabbitMQ() {
        return dockerRabbitMQ;
    }
}
