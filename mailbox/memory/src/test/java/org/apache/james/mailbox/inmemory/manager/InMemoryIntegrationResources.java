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
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreAttachmentManager;
import org.apache.james.mailbox.store.StoreBlobManager;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class InMemoryIntegrationResources implements IntegrationResources<StoreMailboxManager> {

    public interface Stages {
        interface RequireAuthenticator {
            RequireAuthorizator authenticator(Authenticator authenticator);

            default RequireAuthorizator preProvisionnedFakeAuthenticator() {
                FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
                fakeAuthenticator.addUser(ManagerTestProvisionner.USER, ManagerTestProvisionner.USER_PASS);
                fakeAuthenticator.addUser(ManagerTestProvisionner.OTHER_USER, ManagerTestProvisionner.OTHER_USER_PASS);

                return authenticator(fakeAuthenticator);
            }
        }

        interface RequireAuthorizator {
            RequireEventBus authorizator(Authorizator authorizator);

            default RequireEventBus fakeAuthorizator() {
                return authorizator(FakeAuthorizator.defaultReject());
            }
        }

        interface RequireEventBus {
            RequireAnnotationLimits eventBus(EventBus eventBus);

            default RequireAnnotationLimits inVmEventBus() {
                return eventBus(new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory())));
            }
        }

        interface RequireAnnotationLimits {
            RequireMessageParser annotationLimits(int limitAnnotationCount, int limitAnnotationSize);

            default RequireMessageParser defaultAnnotationLimits() {
                return annotationLimits(MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
            }
        }

        interface RequireMessageParser {
            RequireSearchIndex messageParser(MessageParser messageParser);

            default RequireSearchIndex defaultMessageParser() {
                return messageParser(new MessageParser());
            }
        }

        interface RequireSearchIndex {
            RequirePreDeletionHooks searchIndex(Function<MailboxManagerPreInstanciationStage, MessageSearchIndex> searchIndex);

            RequirePreDeletionHooks listeningSearchIndex(Function<MailboxManagerPreInstanciationStage, ListeningMessageSearchIndex> searchIndex);

            default RequirePreDeletionHooks scanningSearchIndex() {
                return searchIndex(stage -> new SimpleMessageSearchIndex(stage.mapperFactory, stage.mapperFactory, new DefaultTextExtractor()));
            }
        }

        interface RequirePreDeletionHooks {
            RequireQuotaManager preDeletionHooksFactories(Collection<Function<MailboxManagerPreInstanciationStage, PreDeletionHook>> preDeletionHooks);

            default RequireQuotaManager preDeletionHookFactory(Function<MailboxManagerPreInstanciationStage, PreDeletionHook> preDeletionHook) {
                return preDeletionHooksFactories(ImmutableList.of(preDeletionHook));
            }

            default RequireQuotaManager preDeletionHook(PreDeletionHook preDeletionHook) {
                return preDeletionHookFactory(toFactory(preDeletionHook));
            }

            default RequireQuotaManager preDeletionHooks(Collection<PreDeletionHook> preDeletionHooks) {
                return preDeletionHooksFactories(preDeletionHooks.stream()
                    .map(RequirePreDeletionHooks::toFactory)
                    .collect(Guavate.toImmutableList()));
            }

            default RequireQuotaManager noPreDeletionHooks() {
                return preDeletionHooksFactories(ImmutableList.of());
            }

            static Function<MailboxManagerPreInstanciationStage, PreDeletionHook> toFactory(PreDeletionHook preDeletionHook) {
                return any -> preDeletionHook;
            }
        }

        interface RequireQuotaManager {
            FinalStage quotaManager(Function<BaseQuotaComponentsStage, QuotaManager> quotaManager);

            default FinalStage storeQuotaManager() {
                return quotaManager(stage -> new StoreQuotaManager(stage.currentQuotaManager, stage.maxQuotaManager));
            }

            default FinalStage quotaManager(QuotaManager quotaManager) {
                return quotaManager(stage -> quotaManager);
            }
        }

        interface FinalStage {
            InMemoryIntegrationResources build();
        }
    }

    public static Stages.RequireAuthenticator builder() {
        return new Builder();
    }

    public static InMemoryIntegrationResources defaultResources() {
        return builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
    }

    public static class Builder implements Stages.RequireAuthenticator, Stages.RequireAuthorizator, Stages.RequireEventBus,
        Stages.RequireAnnotationLimits, Stages.RequireMessageParser, Stages.RequireSearchIndex, Stages.RequirePreDeletionHooks,
        Stages.RequireQuotaManager, Stages.FinalStage {

        private Optional<Authenticator> authenticator;
        private Optional<Authorizator> authorizator;
        private Optional<EventBus> eventBus;
        private Optional<Integer> limitAnnotationCount;
        private Optional<Function<BaseQuotaComponentsStage, QuotaManager>> quotaManager;
        private Optional<Integer> limitAnnotationSize;
        private Optional<MessageParser> messageParser;
        private Optional<Function<MailboxManagerPreInstanciationStage, MessageSearchIndex>> searchIndexFactory;
        private ImmutableSet.Builder<Function<MailboxManagerPreInstanciationStage, PreDeletionHook>> preDeletionHooksFactories;
        private ImmutableList.Builder<MailboxListener.GroupMailboxListener> listenersToBeRegistered;

        private Builder() {
            this.authenticator = Optional.empty();
            this.authorizator = Optional.empty();
            this.eventBus = Optional.empty();
            this.limitAnnotationCount = Optional.empty();
            this.limitAnnotationSize = Optional.empty();
            this.searchIndexFactory = Optional.empty();
            this.messageParser = Optional.empty();
            this.quotaManager = Optional.empty();
            this.preDeletionHooksFactories = ImmutableSet.builder();
            this.listenersToBeRegistered = ImmutableList.builder();
        }

        @Override
        public Builder messageParser(MessageParser messageParser) {
            this.messageParser = Optional.of(messageParser);
            return this;
        }

        @Override
        public Builder quotaManager(Function<BaseQuotaComponentsStage, QuotaManager> quotaManager) {
            this.quotaManager = Optional.of(quotaManager);
            return this;
        }

        @Override
        public Builder authenticator(Authenticator authenticator) {
            this.authenticator = Optional.of(authenticator);
            return this;
        }

        @Override
        public Builder authorizator(Authorizator authorizator) {
            this.authorizator = Optional.of(authorizator);
            return this;
        }

        @Override
        public Builder eventBus(EventBus eventBus) {
            this.eventBus = Optional.of(eventBus);
            return this;
        }

        @Override
        public Builder annotationLimits(int limitAnnotationCount, int limitAnnotationSize) {
            this.limitAnnotationCount = Optional.of(limitAnnotationCount);
            this.limitAnnotationSize = Optional.of(limitAnnotationSize);
            return this;
        }

        @Override
        public Builder preDeletionHooksFactories(Collection<Function<MailboxManagerPreInstanciationStage, PreDeletionHook>> preDeletionHooks) {
            this.preDeletionHooksFactories.addAll(preDeletionHooks);
            return this;
        }

        @Override
        public Builder searchIndex(Function<MailboxManagerPreInstanciationStage, MessageSearchIndex> searchIndex) {
            this.searchIndexFactory = Optional.of(searchIndex);
            return this;
        }

        @Override
        public Builder listeningSearchIndex(Function<MailboxManagerPreInstanciationStage, ListeningMessageSearchIndex> searchIndex) {
            this.searchIndexFactory = Optional.of(stage -> {
                ListeningMessageSearchIndex listeningMessageSearchIndex = searchIndex.apply(stage);
                listenersToBeRegistered.add(listeningMessageSearchIndex);
                return listeningMessageSearchIndex;
            });
            return this;
        }

        @Override
        public InMemoryIntegrationResources build() {
            Preconditions.checkState(authenticator.isPresent());
            Preconditions.checkState(authorizator.isPresent());
            Preconditions.checkState(eventBus.isPresent());
            Preconditions.checkState(quotaManager.isPresent());
            Preconditions.checkState(limitAnnotationSize.isPresent());
            Preconditions.checkState(limitAnnotationCount.isPresent());
            Preconditions.checkState(searchIndexFactory.isPresent());
            Preconditions.checkState(messageParser.isPresent());

            InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();

            GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
            EventBus eventBus = this.eventBus.get();
            StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), groupMembershipResolver, eventBus);

            StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory,
                storeRightManager, limitAnnotationCount.get(), limitAnnotationSize.get());

            SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator.get(), authorizator.get());

            InMemoryPerUserMaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
            DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
            InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
            QuotaManager quotaManager = this.quotaManager.get().apply(new BaseQuotaComponentsStage(maxQuotaManager, currentQuotaManager));
            ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
            QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver);

            MailboxManagerPreInstanciationStage preInstanciationStage = new MailboxManagerPreInstanciationStage(mailboxSessionMapperFactory, sessionProvider);
            MessageSearchIndex index = searchIndexFactory.get().apply(preInstanciationStage);

            InMemoryMailboxManager manager = new InMemoryMailboxManager(
                mailboxSessionMapperFactory,
                sessionProvider,
                new JVMMailboxPathLocker(),
                messageParser.get(),
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

            return new InMemoryIntegrationResources(manager, storeRightManager, new InMemoryMessageId.Factory(), currentQuotaManager, quotaRootResolver, maxQuotaManager, quotaManager, index, eventBus);
        }

        private PreDeletionHooks createHooks(MailboxManagerPreInstanciationStage preInstanciationStage) {
            ImmutableSet<PreDeletionHook> preDeletionHooksSet = preDeletionHooksFactories.build()
                .stream()
                .map(biFunction -> biFunction.apply(preInstanciationStage))
                .collect(Guavate.toImmutableSet());
            return new PreDeletionHooks(preDeletionHooksSet, new NoopMetricFactory());
        }
    }

    public static class BaseQuotaComponentsStage {
        private final InMemoryPerUserMaxQuotaManager maxQuotaManager;
        private final InMemoryCurrentQuotaManager currentQuotaManager;

        public BaseQuotaComponentsStage(InMemoryPerUserMaxQuotaManager maxQuotaManager, InMemoryCurrentQuotaManager currentQuotaManager) {
            this.maxQuotaManager = maxQuotaManager;
            this.currentQuotaManager = currentQuotaManager;
        }

        public InMemoryPerUserMaxQuotaManager getMaxQuotaManager() {
            return maxQuotaManager;
        }

        public InMemoryCurrentQuotaManager getCurrentQuotaManager() {
            return currentQuotaManager;
        }
    }

    public static class MailboxManagerPreInstanciationStage {
        private final InMemoryMailboxSessionMapperFactory mapperFactory;
        private final SessionProviderImpl sessionProvider;

        public MailboxManagerPreInstanciationStage(InMemoryMailboxSessionMapperFactory mapperFactory, SessionProviderImpl sessionProvider) {
            this.mapperFactory = mapperFactory;
            this.sessionProvider = sessionProvider;
        }

        public InMemoryMailboxSessionMapperFactory getMapperFactory() {
            return mapperFactory;
        }

        public SessionProviderImpl getSessionProvider() {
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
    private final EventBus eventBus;
    private final StoreBlobManager blobManager;

    InMemoryIntegrationResources(InMemoryMailboxManager mailboxManager, StoreRightManager storeRightManager, MessageId.Factory messageIdFactory, InMemoryCurrentQuotaManager currentQuotaManager, DefaultUserQuotaRootResolver defaultUserQuotaRootResolver, InMemoryPerUserMaxQuotaManager maxQuotaManager, QuotaManager quotaManager, MessageSearchIndex searchIndex, EventBus eventBus) {
        this.mailboxManager = mailboxManager;
        this.storeRightManager = storeRightManager;
        this.messageIdFactory = messageIdFactory;
        this.currentQuotaManager = currentQuotaManager;
        this.defaultUserQuotaRootResolver = defaultUserQuotaRootResolver;
        this.maxQuotaManager = maxQuotaManager;
        this.quotaManager = quotaManager;
        this.searchIndex = searchIndex;
        this.eventBus = eventBus;

        this.storeMessageIdManager = new StoreMessageIdManager(
            mailboxManager,
            mailboxManager.getMapperFactory(),
            mailboxManager.getEventBus(),
            messageIdFactory,
            quotaManager,
            defaultUserQuotaRootResolver,
            mailboxManager.getPreDeletionHooks());

        this.blobManager = new StoreBlobManager(
            new StoreAttachmentManager((InMemoryMailboxSessionMapperFactory) mailboxManager.getMapperFactory(), storeMessageIdManager),
            storeMessageIdManager,
            messageIdFactory);
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

    public EventBus getEventBus() {
        return eventBus;
    }

    public StoreBlobManager getBlobManager() {
        return blobManager;
    }
}
