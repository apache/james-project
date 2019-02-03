/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james.modules;

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceModuleTestRule;
import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;

public class DockerRabbitMQRule implements GuiceModuleTestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return base;
    }

    @Override
    public void await() {
    }

    @Override
    public Module getModule() {
        return Modules.combine((binder) -> {
                try {
                    binder.bind(RabbitMQConfiguration.class)
                        .toInstance(RabbitMQConfiguration.builder()
                            .amqpUri(DockerRabbitMQSingleton.SINGLETON.amqpUri())
                            .managementUri(DockerRabbitMQSingleton.SINGLETON.managementUri())
                            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                            .build());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            },
            binder -> Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class)
                .addBinding()
                .to(TestRabbitMQModule.QueueCleanUp.class));
    }

    public DockerRabbitMQ dockerRabbitMQ() {
        return DockerRabbitMQSingleton.SINGLETON;
    }

    public void start() {
        DockerRabbitMQSingleton.SINGLETON.start();
    }

    public void stop() {
    }
}
