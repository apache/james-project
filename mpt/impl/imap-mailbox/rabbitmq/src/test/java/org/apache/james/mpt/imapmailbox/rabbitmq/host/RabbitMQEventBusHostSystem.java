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
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.events.RabbitMQEventBus;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.RoutingKeyConverter;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
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
    private InMemoryPerUserMaxQuotaManager maxQuotaManager;
    private RabbitMQConnectionFactory rabbitConnectionFactory;
    private RabbitMQEventBus eventBus;

    public RabbitMQEventBusHostSystem(DockerRabbitMQ dockerRabbitMQ) {
        this.dockerRabbitMQ = dockerRabbitMQ;
    }

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        MailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        StoreSubscriptionManager sm = new StoreSubscriptionManager(mailboxSessionMapperFactory);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();


        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        InMemoryId.Factory mailboxIdFactory = new InMemoryId.Factory();
        EventSerializer eventSerializer = new EventSerializer(mailboxIdFactory, messageIdFactory);
        RoutingKeyConverter routingKeyConverter = new RoutingKeyConverter(ImmutableSet.of(new MailboxIdRegistrationKey.Factory(mailboxIdFactory)));
        rabbitConnectionFactory = createRabbitConnectionFactory();
        eventBus = new RabbitMQEventBus(rabbitConnectionFactory, eventSerializer, RetryBackoffConfiguration.DEFAULT, routingKeyConverter, new MemoryEventDeadLetters());

        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, aclResolver, groupMembershipResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);
        SessionProvider sessionProvider = new SessionProvider(authenticator, authorizator);

        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        DefaultUserQuotaRootResolver quotaRootResolver =  new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver, listeningCurrentQuotaUpdater);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());

        mailboxManager = new InMemoryMailboxManager(mailboxSessionMapperFactory, sessionProvider, locker, messageParser,
            messageIdFactory, eventBus, annotationManager,  storeRightManager, quotaComponents, index);

        eventBus.start();
        eventBus.register(listeningCurrentQuotaUpdater);

        ImapProcessor defaultImapProcessorFactory =
            DefaultImapProcessorFactory.createDefaultProcessor(
                mailboxManager,
                eventBus,
                sm,
                quotaComponents.getQuotaManager(),
                quotaComponents.getQuotaRootResolver(),
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
        maxQuotaManager.setGlobalMaxMessage(maxMessageQuota);
        maxQuotaManager.setGlobalMaxStorage(maxStorageQuota);
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
