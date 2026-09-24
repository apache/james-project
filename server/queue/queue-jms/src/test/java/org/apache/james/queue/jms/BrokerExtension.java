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

package org.apache.james.queue.jms;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.james.queue.api.MailQueueName;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit 5 extension providing an embedded Artemis broker for JMS queue tests.
 * Replaces the legacy ActiveMQ BrokerService-based extension.
 */
public class BrokerExtension implements ParameterResolver, BeforeAllCallback, AfterAllCallback {

    public static final String STATISTICS = "Statistics";

    /** Unique broker ID counter to avoid conflicts when multiple tests run. */
    private static final AtomicInteger BROKER_COUNTER = new AtomicInteger(0);

    /**
     * Generate a random queue name for the embedded broker.
     * Priority support is handled by Artemis natively; no extra configuration needed.
     */
    public static MailQueueName generateRandomQueueName() {
        String queueName = new RandomStringGenerator.Builder().withinRange('a', 'z').build().generate(10);
        return MailQueueName.of(queueName);
    }

    private final EmbeddedActiveMQ broker;
    private final int brokerId;

    public BrokerExtension() throws Exception {
        brokerId = BROKER_COUNTER.incrementAndGet();
        Configuration config = new ConfigurationImpl()
            .setSecurityEnabled(false)
            .setJMXManagementEnabled(false)
            .setPersistenceEnabled(false)
            .addAcceptorConfiguration(new TransportConfiguration(
                InVMAcceptorFactory.class.getName(),
                java.util.Collections.singletonMap("server-id", String.valueOf(brokerId))
            ))
            .setName("test-broker-" + brokerId);
        broker = new EmbeddedActiveMQ();
        broker.setConfiguration(config);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        broker.start();
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        broker.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == EmbeddedActiveMQ.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return broker;
    }
}
