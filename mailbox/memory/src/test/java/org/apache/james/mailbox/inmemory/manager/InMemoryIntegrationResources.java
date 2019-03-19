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

package org.apache.james.mailbox.inmemory.manager;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.manager.IntegrationResources;
import org.apache.james.mailbox.manager.ManagerTestProvisionner;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.api.NoopMetricFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class InMemoryIntegrationResources implements IntegrationResources<StoreMailboxManager> {
    public static class Factory {
        private Optional<Authenticator> authenticator;
        private Optional<Authorizator> authorizator;
        private Optional<EventBus> eventBus;
        private Optional<Integer> limitAnnotationCount;
        private Optional<QuotaManager> quotaManager;
        private Optional<Integer> limitAnnotationSize;
        private Optional<MessageParser> messageParser;
        private Optional<Function<MailboxManagerPreInstanciationStage, MessageSearchIndex>> searchIndexInstanciator;
        private ImmutableSet.Builder<Function<MailboxManagerPreInstanciationStage, PreDeletionHook>> preDeletionHooksInstanciators;
        private ImmutableList.Builder<MailboxListener.GroupMailboxListener> listenersToBeRegistered;

        public Factory() {
            this.authenticator = Optional.empty();
            this.authorizator = Optional.empty();
            this.eventBus = Optional.empty();
            this.limitAnnotationCount = Optional.empty();
            this.limitAnnotationSize = Optional.empty();
            this.searchIndexInstanciator = Optional.empty();
            this.messageParser = Optional.empty();
            this.quotaManager = Optional.empty();
            this.preDeletionHooksInstanciators = ImmutableSet.builder();
            this.listenersToBeRegistered = ImmutableList.builder();
        }

        public Factory messageParser(MessageParser messageParser) {
            this.messageParser = Optional.of(messageParser);
            return this;
        }

        public Factory quotaManager(QuotaManager quotaManager) {
            this.quotaManager = Optional.of(quotaManager);
            return this;
        }

        public Factory authenticator(Authenticator authenticator) {
            this.authenticator = Optional.of(authenticator);
            return this;
        }

        public Factory authorizator(Authorizator authorizator) {
            this.authorizator = Optional.of(authorizator);
            return this;
        }

        public Factory eventBus(EventBus eventBus) {
            this.eventBus = Optional.of(eventBus);
            return this;
        }

        public Factory annotationLimits(int limitAnnotationCount, int limitAnnotationSize) {
            this.limitAnnotationCount = Optional.of(limitAnnotationCount);
            this.limitAnnotationSize = Optional.of(limitAnnotationSize);
            return this;
        }

        public Factory preDeletionHooks(Collection<PreDeletionHook> preDeletionHooks) {
            this.preDeletionHooksInstanciators.addAll(preDeletionHooks.stream()
                .map(this::toFactory)
                .collect(Guavate.toImmutableList()));
            return this;
        }

        public Factory preDeletionHook(Function<MailboxManagerPreInstanciationStage, PreDeletionHook> preDeletionHook) {
            this.preDeletionHooksInstanciators.add(preDeletionHook);
            return this;
        }

        public Factory searchIndex(Function<MailboxManagerPreInstanciationStage, MessageSearchIndex> searchIndex) {
            this.searchIndexInstanciator = Optional.of(searchIndex);
            return this;
        }

        public Factory listeningSearchIndex(Function<MailboxManagerPreInstanciationStage, ListeningMessageSearchIndex> searchIndex) {
            this.searchIndexInstanciator = Optional.of(stage -> {
                ListeningMessageSearchIndex listeningMessageSearchIndex = searchIndex.apply(stage);
                listenersToBeRegistered.add(listeningMessageSearchIndex);
                return listeningMessageSearchIndex;
            });
            return this;
        }

        private Function<MailboxManagerPreInstanciationStage, PreDeletionHook> toFactory(PreDeletionHook preDeletionHook) {
            return any -> preDeletionHook;
        }

        public InMemoryIntegrationResources create() {
            InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
            EventBus eventBus = this.eventBus.orElseGet(() -> new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory())));
            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
            StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), groupMembershipResolver, eventBus);
            StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory,
                storeRightManager, limitAnnotationCount.orElse(MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX),
                limitAnnotationSize.orElse(MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE));

            SessionProvider sessionProvider = new SessionProvider(
                authenticator.orElse(defaultAuthenticator()),
                authorizator.orElse(FakeAuthorizator.defaultReject()));

            InMemoryPerUserMaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
            DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
            InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
            QuotaManager quotaManager = this.quotaManager.orElseGet(() -> new StoreQuotaManager(currentQuotaManager, maxQuotaManager));
            ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
            QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver, listeningCurrentQuotaUpdater);

            MailboxManagerPreInstanciationStage preInstanciationStage = new MailboxManagerPreInstanciationStage(mailboxSessionMapperFactory, sessionProvider);

            MessageSearchIndex index = searchIndexInstanciator
                .orElse(stage -> new SimpleMessageSearchIndex(stage.mapperFactory, stage.mapperFactory, new DefaultTextExtractor()))
                .apply(preInstanciationStage);

            InMemoryMailboxManager manager = new InMemoryMailboxManager(
                mailboxSessionMapperFactory,
                sessionProvider,
                new JVMMailboxPathLocker(),
                messageParser.orElse(new MessageParser()),
                new InMemoryMessageId.Factory(),
                eventBus,
                annotationManager,
                storeRightManager,
                quotaComponents,
                index,
                createHooks(preInstanciationStage));

            eventBus.register(listeningCurrentQuotaUpdater);
            eventBus.register(new MailboxAnnotationListener(mailboxSessionMapperFactory, sessionProvider));

            listenersToBeRegistered.build().forEach(eventBus::register);

            return new InMemoryIntegrationResources(manager, storeRightManager, new InMemoryMessageId.Factory(), currentQuotaManager, quotaRootResolver, maxQuotaManager, quotaManager, index);
        }

        FakeAuthenticator defaultAuthenticator() {
            FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
            fakeAuthenticator.addUser(ManagerTestProvisionner.USER, ManagerTestProvisionner.USER_PASS);
            fakeAuthenticator.addUser(ManagerTestProvisionner.OTHER_USER, ManagerTestProvisionner.OTHER_USER_PASS);
            return fakeAuthenticator;
        }

        PreDeletionHooks createHooks(MailboxManagerPreInstanciationStage preInstanciationStage) {
            return new PreDeletionHooks(preDeletionHooksInstanciators.build()
                .stream()
                .map(biFunction -> biFunction.apply(preInstanciationStage))
                .collect(Guavate.toImmutableSet()));
        }
    }

    public static class MailboxManagerPreInstanciationStage {
        private final InMemoryMailboxSessionMapperFactory mapperFactory;
        private final SessionProvider sessionProvider;

        public MailboxManagerPreInstanciationStage(InMemoryMailboxSessionMapperFactory mapperFactory, SessionProvider sessionProvider) {
            this.mapperFactory = mapperFactory;
            this.sessionProvider = sessionProvider;
        }

        public InMemoryMailboxSessionMapperFactory getMapperFactory() {
            return mapperFactory;
        }

        public SessionProvider getSessionProvider() {
            return sessionProvider;
        }
    }


    private final InMemoryMailboxManager mailboxManager;
    private final StoreRightManager storeRightManager;
    private final MessageId.Factory messageIdFactory;
    private final InMemoryCurrentQuotaManager currentQuotaManager;
    private final DefaultUserQuotaRootResolver defaultUserQuotaRootResolver;
    private final InMemoryPerUserMaxQuotaManager maxQuotaManager;
    private final QuotaManager quotaManager;
    private final StoreMessageIdManager storeMessageIdManager;
    private final MessageSearchIndex searchIndex;

    InMemoryIntegrationResources(InMemoryMailboxManager mailboxManager, StoreRightManager storeRightManager, MessageId.Factory messageIdFactory, InMemoryCurrentQuotaManager currentQuotaManager, DefaultUserQuotaRootResolver defaultUserQuotaRootResolver, InMemoryPerUserMaxQuotaManager maxQuotaManager, QuotaManager quotaManager, MessageSearchIndex searchIndex) {
        this.mailboxManager = mailboxManager;
        this.storeRightManager = storeRightManager;
        this.messageIdFactory = messageIdFactory;
        this.currentQuotaManager = currentQuotaManager;
        this.defaultUserQuotaRootResolver = defaultUserQuotaRootResolver;
        this.maxQuotaManager = maxQuotaManager;
        this.quotaManager = quotaManager;
        this.searchIndex = searchIndex;

        this.storeMessageIdManager = new StoreMessageIdManager(
            mailboxManager,
            mailboxManager.getMapperFactory(),
            mailboxManager.getEventBus(),
            messageIdFactory,
            quotaManager,
            defaultUserQuotaRootResolver,
            mailboxManager.getPreDeletionHooks());
    }

    public DefaultUserQuotaRootResolver getDefaultUserQuotaRootResolver() {
        return defaultUserQuotaRootResolver;
    }

    public InMemoryMailboxManager getMailboxManager() {
        return mailboxManager;
    }

    public InMemoryCurrentQuotaManager getCurrentQuotaManager() {
        return currentQuotaManager;
    }

    public StoreRightManager getStoreRightManager() {
        return storeRightManager;
    }

    public MessageId.Factory getMessageIdFactory() {
        return messageIdFactory;
    }

    public InMemoryPerUserMaxQuotaManager getMaxQuotaManager() {
        return maxQuotaManager;
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    public MessageIdManager getMessageIdManager() {
        return storeMessageIdManager;
    }

    @Override
    public QuotaRootResolver getQuotaRootResolver() {
        return defaultUserQuotaRootResolver;
    }

    public MessageSearchIndex getSearchIndex() {
        return searchIndex;
    }
}
