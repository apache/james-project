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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueFactoryContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueBrowser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMqMailQueueFactoryTest implements MailQueueFactoryContract<RabbitMQMailQueue> {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private RabbitMQMailQueueFactory mailQueueFactory;
    private RabbitMQMailQueueManagement mqManagementApi;

    @BeforeEach
    void setup() throws Exception {
        MimeMessageStore.Factory mimeMessageStoreFactory = mock(MimeMessageStore.Factory.class);
        MailQueueView.Factory mailQueueViewFactory = mock(MailQueueView.Factory.class);
        MailQueueView<CassandraMailQueueBrowser.CassandraMailQueueItemView> mailQueueView = mock(MailQueueView.class);
        when(mailQueueViewFactory.create(any()))
            .thenReturn(mailQueueView);

        RabbitMQMailQueueConfiguration configuration = RabbitMQMailQueueConfiguration.builder()
            .sizeMetricsEnabled(true)
            .build();

        RabbitMQMailQueueFactory.PrivateFactory privateFactory = new RabbitMQMailQueueFactory.PrivateFactory(
            new RecordingMetricFactory(),
            new NoopGaugeRegistry(),
            rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(),
            mimeMessageStoreFactory,
            BLOB_ID_FACTORY,
            mailQueueViewFactory,
            Clock.systemUTC(),
            new RawMailQueueItemDecoratorFactory(),
            configuration);
        mqManagementApi = new RabbitMQMailQueueManagement(rabbitMQExtension.managementAPI());
        mailQueueFactory = new RabbitMQMailQueueFactory(rabbitMQExtension.getSender(), mqManagementApi, privateFactory);
    }

    @AfterEach
    void tearDown() {
        mqManagementApi.deleteAllQueues();
    }

    @Override
    public MailQueueFactory<RabbitMQMailQueue> getMailQueueFactory() {
        return mailQueueFactory;
    }

}
