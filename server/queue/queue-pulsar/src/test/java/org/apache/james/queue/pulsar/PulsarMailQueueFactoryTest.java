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

package org.apache.james.queue.pulsar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.backends.pulsar.DockerPulsarExtension;
import org.apache.james.backends.pulsar.PulsarClients;
import org.apache.james.backends.pulsar.PulsarConfiguration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.junit.categories.Unstable;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueFactoryContract;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueMetricExtension;
import org.apache.james.queue.api.ManageableMailQueueFactoryContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag(Unstable.TAG)
@ExtendWith(MailQueueMetricExtension.class)
@ExtendWith(DockerPulsarExtension.class)
class PulsarMailQueueFactoryTest implements MailQueueFactoryContract<PulsarMailQueue>, ManageableMailQueueFactoryContract<PulsarMailQueue> {

    PulsarMailQueueFactory mailQueueFactory;
    private HashBlobId.Factory blobIdFactory;
    private Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private MailQueueItemDecoratorFactory factory;
    private PulsarConfiguration config;
    private MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem;
    private PulsarClients pulsarClients;

    @BeforeEach
    void setUp(DockerPulsarExtension.DockerPulsar dockerPulsar, MailQueueMetricExtension.MailQueueMetricTestSystem metricTestSystem) throws PulsarClientException, PulsarAdminException {
        this.metricTestSystem = metricTestSystem;

        blobIdFactory = new HashBlobId.Factory();

        MemoryBlobStoreDAO memoryBlobStore = new MemoryBlobStoreDAO();
        PassThroughBlobStore blobStore = new PassThroughBlobStore(memoryBlobStore, BucketName.DEFAULT, blobIdFactory);
        MimeMessageStore.Factory mimeMessageStoreFactory = new MimeMessageStore.Factory(blobStore);
        mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
        factory = new RawMailQueueItemDecoratorFactory();
        config = dockerPulsar.getConfiguration();
        pulsarClients = PulsarClients.create(config);
        mailQueueFactory = newInstance();
    }

    @AfterEach
    void tearDown() {
        mailQueueFactory.stop();
        pulsarClients.stop();
    }

    private PulsarMailQueueFactory newInstance() {
        return new PulsarMailQueueFactory(
                config,
                pulsarClients,
                blobIdFactory,
                mimeMessageStore,
                factory,
                metricTestSystem.getMetricFactory(),
                metricTestSystem.getSpyGaugeRegistry());
    }

    @Override
    public MailQueueFactory<PulsarMailQueue> getMailQueueFactory() {
        return mailQueueFactory;
    }

    @Test
    void createAlreadyCreatedQueueShouldReturnPreviouslyCreatedMailQueueInstance() {
        MailQueueFactory<PulsarMailQueue> mailQueueFactory = getMailQueueFactory();

        PulsarMailQueue queue1 = mailQueueFactory.createQueue(MailQueueFactoryContract.NAME_1);
        PulsarMailQueue queue2 = mailQueueFactory.createQueue(MailQueueFactoryContract.NAME_1);

        assertThat(queue1).isSameAs(queue2);
    }

    @Test
    void getExistingMailQueueShouldReturnPreviouslyCreatedMailQueueInstance() {
        MailQueueFactory<PulsarMailQueue> mailQueueFactory = getMailQueueFactory();

        PulsarMailQueue queue1 = mailQueueFactory.createQueue(MailQueueFactoryContract.NAME_1);
        Optional<PulsarMailQueue> queue2 = mailQueueFactory.getQueue(MailQueueFactoryContract.NAME_1);

        assertThat(queue2.get()).isSameAs(queue1);
    }

}
