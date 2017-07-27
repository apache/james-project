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

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.blob.BlobTransferPolicy;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.ManagementContext;
import org.apache.activemq.plugin.StatisticsBrokerPlugin;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.james.filesystem.api.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public class EmbeddedActiveMQ {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedActiveMQ.class);

    private final ActiveMQConnectionFactory activeMQConnectionFactory;
    private final PersistenceAdapter persistenceAdapter;
    private BrokerService brokerService;

    @Inject
        private EmbeddedActiveMQ(FileSystem fileSystem, PersistenceAdapter persistenceAdapter) {
        this.persistenceAdapter = persistenceAdapter;
        try {
            persistenceAdapter.setDirectory(fileSystem.getFile("file://var/store/activemq/brokers/KahaDB"));
            launchEmbeddedBroker(fileSystem);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        activeMQConnectionFactory = createActiveMQConnectionFactory(createBlobTransferPolicy(fileSystem));
    }

    public ConnectionFactory getConnectionFactory() {
        return activeMQConnectionFactory;
    }

    @PreDestroy
    public void stop() throws Exception {
        brokerService.stop();
    }

    private ActiveMQConnectionFactory createActiveMQConnectionFactory(BlobTransferPolicy blobTransferPolicy) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://james?create=false");
        connectionFactory.setBlobTransferPolicy(blobTransferPolicy);
        connectionFactory.setPrefetchPolicy(createActiveMQPrefetchPolicy());
        return connectionFactory;
    }

    private ActiveMQPrefetchPolicy createActiveMQPrefetchPolicy() {
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(0);
        prefetchPolicy.setTopicPrefetch(0);
        return prefetchPolicy;
    }

    private BlobTransferPolicy createBlobTransferPolicy(FileSystem fileSystem) {
        FileSystemBlobTransferPolicy blobTransferPolicy = new FileSystemBlobTransferPolicy();
        blobTransferPolicy.setDefaultUploadUrl("file://var/store/activemq/blob-transfer");
        blobTransferPolicy.setFileSystem(fileSystem);
        return blobTransferPolicy;
    }

    private void launchEmbeddedBroker(FileSystem fileSystem) throws Exception {
        brokerService = new BrokerService();
        brokerService.setBrokerName("james");
        brokerService.setUseJmx(false);
        brokerService.setPersistent(true);
        brokerService.setDataDirectoryFile(fileSystem.getFile("file://var/store/activemq/brokers"));
        brokerService.setUseShutdownHook(false);
        brokerService.setSchedulerSupport(false);
        brokerService.setBrokerId("broker");
        String[] uris = {"tcp://localhost:0"};
        brokerService.setTransportConnectorURIs(uris);
        ManagementContext managementContext = new ManagementContext();
        managementContext.setCreateConnector(false);
        brokerService.setManagementContext(managementContext);
        brokerService.setPersistenceAdapter(persistenceAdapter);
        BrokerPlugin[] brokerPlugins = {new StatisticsBrokerPlugin()};
        brokerService.setPlugins(brokerPlugins);
        String[] transportConnectorsURIs = {"tcp://localhost:0"};
        brokerService.setTransportConnectorURIs(transportConnectorsURIs);
        brokerService.start();
        LOGGER.info("Started embedded activeMq");
    }
}
