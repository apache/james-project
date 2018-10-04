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

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class RabbitMQExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

    private DockerRabbitMQ rabbitMQ;
    private RabbitChannelPoolImpl rabbitChannelPoolImpl;

    @Override
    public void beforeAll(ExtensionContext context) {
        rabbitMQ = DockerRabbitMQ.withoutCookie();
        rabbitMQ.start();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        rabbitChannelPoolImpl = createRabbitChannelPool();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        rabbitChannelPoolImpl.close();
        rabbitMQ.reset();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        rabbitMQ.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerRabbitMQ.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return rabbitMQ;
    }

    public RabbitChannelPoolImpl getRabbitChannelPool() {
        return rabbitChannelPoolImpl;
    }

    public DockerRabbitMQ getRabbitMQ() {
        return rabbitMQ;
    }

    private RabbitChannelPoolImpl createRabbitChannelPool() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQ.amqpUri())
            .managementUri(rabbitMQ.managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(
            rabbitMQConfiguration,
            new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor()));
        return new RabbitChannelPoolImpl(rabbitMQConnectionFactory);
    }
}
