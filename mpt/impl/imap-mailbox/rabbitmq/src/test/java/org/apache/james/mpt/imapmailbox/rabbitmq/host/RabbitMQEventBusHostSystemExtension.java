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

package org.apache.james.mpt.imapmailbox.rabbitmq.host;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.github.fge.lambdas.runnable.ThrowingRunnable;

public class RabbitMQEventBusHostSystemExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {
    private final RabbitMQExtension rabbitMQExtension;
    private RabbitMQEventBusHostSystem hostSystem;

    public RabbitMQEventBusHostSystemExtension() {
        rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
            .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        performQuietly(() -> {
            RabbitMQManagementAPI managementAPI = rabbitMQExtension.managementAPI();
            managementAPI.listQueues()
                .forEach(queue -> managementAPI.deleteQueue("/", queue.getName()));
        });
        hostSystem.afterTest();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        hostSystem = new RabbitMQEventBusHostSystem(rabbitMQExtension.getRabbitMQ());
        hostSystem.beforeTest();
    }

    private void performQuietly(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            // ignore
        }
    }

    public RabbitMQEventBusHostSystem getHostSystem() {
        return hostSystem;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        rabbitMQExtension.beforeAll(extensionContext);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        rabbitMQExtension.afterAll(extensionContext);
    }
}
