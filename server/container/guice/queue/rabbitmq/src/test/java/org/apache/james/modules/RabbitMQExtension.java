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

package org.apache.james.modules;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.google.inject.Module;

public class RabbitMQExtension implements GuiceModuleTestExtension {

    private final DockerRabbitMQRule rabbitMQRule = new DockerRabbitMQRule();

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        rabbitMQRule.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        rabbitMQRule.stop();
    }

    @Override
    public Module getModule() {
        return new TestRabbitMQModule(rabbitMQRule.dockerRabbitMQ());
    }

    public DockerRabbitMQ dockerRabbitMQ() {
        return rabbitMQRule.dockerRabbitMQ();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerRabbitMQ.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerRabbitMQ();
    }
}