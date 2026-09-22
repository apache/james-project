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

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.james.filesystem.api.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Artemis broker replacing the legacy ActiveMQ embedded broker.
 * Uses Apache ActiveMQ Artemis (Jakarta JMS) as the underlying message broker.
 */
public class EmbeddedActiveMQ {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedActiveMQ.class);
    private static final String DATA_DIRECTORY_RELATIVE = "var/store/artemis";
    private static final String BROKER_NAME = "james";

    private final ActiveMQConnectionFactory connectionFactory;
    private final org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ embeddedServer;

    @Inject
    public EmbeddedActiveMQ(FileSystem fileSystem, ActiveMQConfiguration configuration) {
        try {
            String dataDirectory = fileSystem.getFile("file://" + DATA_DIRECTORY_RELATIVE).getAbsolutePath();
            embeddedServer = createAndStartBroker(dataDirectory);
            connectionFactory = createConnectionFactory();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start embedded Artemis broker", e);
        }
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    @PreDestroy
    public void stop() throws Exception {
        LOGGER.info("Stopping embedded Artemis broker...");
        embeddedServer.stop();
        connectionFactory.close();
        LOGGER.info("Stopped embedded Artemis broker");
    }

    private org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ createAndStartBroker(String dataDirectory) throws Exception {
        Configuration config = new ConfigurationImpl()
            .setSecurityEnabled(false)
            .setJMXManagementEnabled(false)
            .setPersistenceEnabled(true)
            .setJournalDirectory(dataDirectory + "/journal")
            .setBindingsDirectory(dataDirectory + "/bindings")
            .setLargeMessagesDirectory(dataDirectory + "/largemessages")
            .setPagingDirectory(dataDirectory + "/paging")
            .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()))
            .setName(BROKER_NAME);

        org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ server = new org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ();
        server.setConfiguration(config);
        server.start();
        LOGGER.info("Started embedded Artemis broker, data directory: {}", dataDirectory);
        return server;
    }

    private ActiveMQConnectionFactory createConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
            "vm://0?broker-name=" + BROKER_NAME
        );
        factory.setConsumerWindowSize(0);
        factory.setBlockOnAcknowledge(true);
        return factory;
    }
}
