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

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.mail.internet.MimeMessage;

import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitChannelPool;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backend.rabbitmq.RabbitMQExtension;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueFactoryContract;
import org.apache.james.queue.rabbitmq.view.api.MailQueueView;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

@ExtendWith(RabbitMQExtension.class)
class RabbitMqMailQueueFactoryTest implements MailQueueFactoryContract<RabbitMQMailQueue> {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    private RabbitMQMailQueueFactory mailQueueFactory;

    @BeforeEach
    void setup(DockerRabbitMQ rabbitMQ) throws IOException, TimeoutException, URISyntaxException {
        Store<MimeMessage, MimeMessagePartsId> mimeMessageStore = mock(Store.class);
        MailQueueView mailQueueView = mock(MailQueueView.class);

        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQ.amqpUri())
            .managementUri(rabbitMQ.managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(
            rabbitMQConfiguration,
            new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor()));

        RabbitClient rabbitClient = new RabbitClient(new RabbitChannelPool(rabbitMQConnectionFactory));
        RabbitMQMailQueue.Factory factory = new RabbitMQMailQueue.Factory(
            new NoopMetricFactory(),
            new NoopGaugeRegistry(),
            rabbitClient,
            mimeMessageStore,
            BLOB_ID_FACTORY,
            mailQueueView,
            Clock.systemUTC());
        RabbitMQManagementApi mqManagementApi = new RabbitMQManagementApi(rabbitMQConfiguration);
        mailQueueFactory = new RabbitMQMailQueueFactory(rabbitClient, mqManagementApi, factory);
    }

    @Override
    public MailQueueFactory<RabbitMQMailQueue> getMailQueueFactory() {
        return mailQueueFactory;
    }

    @Test
    void createQueueShouldReturnTheSameInstanceWhenParallelCreateSameQueueName() throws Exception {
        Set<RabbitMQMailQueue> createdRabbitMQMailQueues =  ConcurrentHashMap.newKeySet();

        ConcurrentTestRunner.builder()
            .threadCount(100)
            .operationCount(10)
            .build((threadNumber, operationNumber) ->
                createdRabbitMQMailQueues.add(mailQueueFactory.createQueue("spool")))
            .run()
            .awaitTermination(10, TimeUnit.MINUTES);

        assertThat(mailQueueFactory.listCreatedMailQueues())
            .hasSize(1)
            .isEqualTo(createdRabbitMQMailQueues)
            .extracting(RabbitMQMailQueue::getName)
            .hasOnlyOneElementSatisfying(queueName -> assertThat(queueName).isEqualTo("spool"));
    }
}
