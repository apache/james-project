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

import static org.apache.james.backend.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.james.backend.rabbitmq.DockerRabbitMQ;
import org.apache.james.backend.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backend.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.event.json.EventSerializer;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.events.RabbitMQEventBus;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.RoutingKeyConverter;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.util.concurrent.NamedThreadFactory;

import com.google.common.collect.ImmutableSet;
import com.nurkiewicz.asyncretry.AsyncRetryExecutor;

public class RabbitMQEventBusHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOVE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.QUOTA_SUPPORT,
        Feature.ANNOTATION_SUPPORT,
        Feature.MOD_SEQ_SEARCH);


    private static final int THREE_RETRIES = 3;
    private static final int ONE_HUNDRED_MILLISECONDS = 100;

    private final DockerRabbitMQ dockerRabbitMQ;
    private StoreMailboxManager mailboxManager;
    private RabbitMQEventBus eventBus;
    private InMemoryIntegrationResources integrationResources;

    public RabbitMQEventBusHostSystem(DockerRabbitMQ dockerRabbitMQ) {
        this.dockerRabbitMQ = dockerRabbitMQ;
    }

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        InMemoryId.Factory mailboxIdFactory = new InMemoryId.Factory();
        EventSerializer eventSerializer = new EventSerializer(mailboxIdFactory, messageIdFactory);
        RoutingKeyConverter routingKeyConverter = new RoutingKeyConverter(ImmutableSet.of(new MailboxIdRegistrationKey.Factory(mailboxIdFactory)));
        RabbitMQConnectionFactory rabbitConnectionFactory = createRabbitConnectionFactory();
        eventBus = new RabbitMQEventBus(rabbitConnectionFactory, eventSerializer, RetryBackoffConfiguration.DEFAULT, routingKeyConverter, new MemoryEventDeadLetters());
        eventBus.start();

        integrationResources = new InMemoryIntegrationResources();
        mailboxManager = integrationResources.createResources(eventBus, authenticator, authorizator).getMailboxManager();


        ImapProcessor defaultImapProcessorFactory =
            DefaultImapProcessorFactory.createDefaultProcessor(
                mailboxManager,
                eventBus,
                new StoreSubscriptionManager(mailboxManager.getMapperFactory()),
                integrationResources.retrieveQuotaManager(mailboxManager),
                integrationResources.retrieveQuotaRootResolver(mailboxManager),
                new DefaultMetricFactory());
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            defaultImapProcessorFactory);
    }


    private RabbitMQConnectionFactory createRabbitConnectionFactory() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(dockerRabbitMQ.amqpUri())
            .managementUri(dockerRabbitMQ.managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(THREE_RETRIES)
            .minDelay(ONE_HUNDRED_MILLISECONDS)
            .build();

        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        return new RabbitMQConnectionFactory(
            rabbitMQConfiguration,
            new AsyncRetryExecutor(Executors.newSingleThreadScheduledExecutor(threadFactory)));
    }

    @Override
    public void afterTest() {
        eventBus.stop();
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(QuotaCount maxMessageQuota, QuotaSize maxStorageQuota) {
        try {
            integrationResources.retrieveMaxQuotaManager(mailboxManager).setGlobalMaxMessage(maxMessageQuota);
            integrationResources.retrieveMaxQuotaManager(mailboxManager).setGlobalMaxStorage(maxStorageQuota);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void await() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
