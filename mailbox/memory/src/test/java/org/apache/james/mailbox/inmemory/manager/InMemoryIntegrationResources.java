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
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.manager.IntegrationResources;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
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
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.api.NoopMetricFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

public class InMemoryIntegrationResources implements IntegrationResources<StoreMailboxManager> {

    public static class Resources {
        private final InMemoryMailboxManager mailboxManager;
        private final StoreRightManager storeRightManager;
        private final MessageId.Factory messageIdFactory;
        private final InMemoryCurrentQuotaManager currentQuotaManager;
        private final DefaultUserQuotaRootResolver defaultUserQuotaRootResolver;
        private final InMemoryPerUserMaxQuotaManager maxQuotaManager;
        private final QuotaManager quotaManager;

        Resources(InMemoryMailboxManager mailboxManager, StoreRightManager storeRightManager, MessageId.Factory messageIdFactory, InMemoryCurrentQuotaManager currentQuotaManager, DefaultUserQuotaRootResolver defaultUserQuotaRootResolver, InMemoryPerUserMaxQuotaManager maxQuotaManager, QuotaManager quotaManager) {
            this.mailboxManager = mailboxManager;
            this.storeRightManager = storeRightManager;
            this.messageIdFactory = messageIdFactory;
            this.currentQuotaManager = currentQuotaManager;
            this.defaultUserQuotaRootResolver = defaultUserQuotaRootResolver;
            this.maxQuotaManager = maxQuotaManager;
            this.quotaManager = quotaManager;
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

        public MessageIdManager createMessageIdManager() {
            return new StoreMessageIdManager(
                mailboxManager,
                mailboxManager.getMapperFactory(),
                mailboxManager.getEventBus(),
                messageIdFactory,
                quotaManager,
                defaultUserQuotaRootResolver,
                mailboxManager.getPreDeletionHooks());
        }
    }

    public static class Factory {
        private Optional<GroupMembershipResolver> groupMembershipResolver;
        private Optional<Authenticator> authenticator;
        private Optional<Authorizator> authorizator;
        private Optional<EventBus> eventBus;
        private Optional<Integer> limitAnnotationCount;
        private Optional<Integer> limitAnnotationSize;
        private ImmutableSet.Builder<BiFunction<SessionProvider, InMemoryMailboxSessionMapperFactory, PreDeletionHook>> preDeletionHooksInstanciators;

        public Factory() {
            this.groupMembershipResolver = Optional.empty();
            this.authenticator = Optional.empty();
            this.authorizator = Optional.empty();
            this.eventBus = Optional.empty();
            this.limitAnnotationCount = Optional.empty();
            this.limitAnnotationSize = Optional.empty();
            this.preDeletionHooksInstanciators = ImmutableSet.builder();
        }

        public Factory withAuthenticator(Authenticator authenticator) {
            this.authenticator = Optional.of(authenticator);
            return this;
        }

        public Factory withAuthorizator(Authorizator authorizator) {
            this.authorizator = Optional.of(authorizator);
            return this;
        }

        public Factory withEventBus(EventBus eventBus) {
            this.eventBus = Optional.of(eventBus);
            return this;
        }

        public Factory withGroupmembershipResolver(GroupMembershipResolver groupmembershipResolver) {
            this.groupMembershipResolver = Optional.of(groupmembershipResolver);
            return this;
        }

        public Factory withAnnotationLimits(int limitAnnotationCount, int limitAnnotationSize) {
            this.limitAnnotationCount = Optional.of(limitAnnotationCount);
            this.limitAnnotationSize = Optional.of(limitAnnotationSize);
            return this;
        }

        public Factory withPreDeletionHooks(Collection<PreDeletionHook> preDeletionHooks) {
            this.preDeletionHooksInstanciators.addAll(preDeletionHooks.stream()
                .map(this::toInstanciator)
                .collect(Guavate.toImmutableList()));
            return this;
        }

        public Factory withPreDeletionHook(BiFunction<SessionProvider, InMemoryMailboxSessionMapperFactory, PreDeletionHook> preDeletionHook) {
            this.preDeletionHooksInstanciators.add(preDeletionHook);
            return this;
        }

        private BiFunction<SessionProvider, InMemoryMailboxSessionMapperFactory, PreDeletionHook> toInstanciator(PreDeletionHook preDeletionHook) {
            return (a, b) -> preDeletionHook;
        }

        public Resources create() {
            InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
            EventBus eventBus = this.eventBus.orElseGet(() -> new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory())));
            GroupMembershipResolver groupMembershipResolver = this.groupMembershipResolver.orElse(new SimpleGroupMembershipResolver());
            StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(), groupMembershipResolver, eventBus);
            StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory,
                storeRightManager, limitAnnotationCount.orElse(MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX),
                limitAnnotationSize.orElse(MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE));

            SessionProvider sessionProvider = new SessionProvider(
                authenticator.orElse(new FakeAuthenticator()),
                authorizator.orElse(FakeAuthorizator.defaultReject()));

            InMemoryPerUserMaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
            DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
            InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
            StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
            ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
            QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver, listeningCurrentQuotaUpdater);

            MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());

            InMemoryMailboxManager manager = new InMemoryMailboxManager(
                mailboxSessionMapperFactory,
                sessionProvider,
                new JVMMailboxPathLocker(),
                new MessageParser(),
                new InMemoryMessageId.Factory(),
                eventBus,
                annotationManager,
                storeRightManager,
                quotaComponents,
                index,
                createHooks(sessionProvider, mailboxSessionMapperFactory));

            eventBus.register(listeningCurrentQuotaUpdater);
            eventBus.register(new MailboxAnnotationListener(mailboxSessionMapperFactory, sessionProvider));

            return new Resources(manager, storeRightManager, new InMemoryMessageId.Factory(), currentQuotaManager, quotaRootResolver, maxQuotaManager, quotaManager);
        }

        PreDeletionHooks createHooks(SessionProvider sessionProvider, InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory) {
            return new PreDeletionHooks(preDeletionHooksInstanciators.build()
                .stream()
                .map(biFunction -> biFunction.apply(sessionProvider, mailboxSessionMapperFactory))
                .collect(Guavate.toImmutableSet()));
        }
    }

    private SimpleGroupMembershipResolver groupMembershipResolver;

    @Override
    public InMemoryMailboxManager createMailboxManager(GroupMembershipResolver groupMembershipResolver) {
        return new Factory()
            .withGroupmembershipResolver(groupMembershipResolver)
            .create()
            .mailboxManager;
    }

    public Resources createResources(EventBus eventBus, Authenticator authenticator, Authorizator authorizator) {
        return createResources(eventBus,
            new SimpleGroupMembershipResolver(), authenticator, authorizator, MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE,
            PreDeletionHook.NO_PRE_DELETION_HOOK);
    }

    private Resources createResources(EventBus eventBus, GroupMembershipResolver groupMembershipResolver, Authenticator authenticator, Authorizator authorizator,
                                      int limitAnnotationCount, int limitAnnotationSize,
                                      Set<PreDeletionHook> preDeletionHooks) {
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(),
            groupMembershipResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager, limitAnnotationCount, limitAnnotationSize);

        SessionProvider sessionProvider = new SessionProvider(authenticator, authorizator);

        InMemoryPerUserMaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        DefaultUserQuotaRootResolver quotaRootResolver =  new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver, listeningCurrentQuotaUpdater);

        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());

        InMemoryMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            sessionProvider,
            new JVMMailboxPathLocker(),
            new MessageParser(),
            new InMemoryMessageId.Factory(),
            eventBus,
            annotationManager,
            storeRightManager,
            quotaComponents,
            index,
            new PreDeletionHooks(preDeletionHooks));

        eventBus.register(listeningCurrentQuotaUpdater);
        eventBus.register(new MailboxAnnotationListener(mailboxSessionMapperFactory, sessionProvider));

        return new Resources(manager, storeRightManager, new InMemoryMessageId.Factory(), currentQuotaManager, quotaRootResolver, maxQuotaManager, quotaManager);
    }

    public Resources createResourcesForHooks(SessionProvider sessionProvider, InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory, Set<PreDeletionHook> preDeletionHooks) {
        EventBus eventBus = new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()));
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(),
            groupMembershipResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory,
            storeRightManager, MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);

        InMemoryPerUserMaxQuotaManager maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(sessionProvider, mailboxSessionMapperFactory);
        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver), sessionProvider);
        StoreQuotaManager quotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater listeningCurrentQuotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, eventBus, quotaManager);
        QuotaComponents quotaComponents = new QuotaComponents(maxQuotaManager, quotaManager, quotaRootResolver, listeningCurrentQuotaUpdater);

        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor());

        InMemoryMailboxManager manager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            sessionProvider,
            new JVMMailboxPathLocker(),
            new MessageParser(),
            new InMemoryMessageId.Factory(),
            eventBus,
            annotationManager,
            storeRightManager,
            quotaComponents,
            index,
            new PreDeletionHooks(preDeletionHooks));

        eventBus.register(listeningCurrentQuotaUpdater);
        eventBus.register(new MailboxAnnotationListener(mailboxSessionMapperFactory, sessionProvider));

        return new Resources(manager, storeRightManager, new InMemoryMessageId.Factory(), currentQuotaManager, quotaRootResolver, maxQuotaManager, quotaManager);
    }

    @Override
    public MessageIdManager createMessageIdManager(StoreMailboxManager mailboxManager) {
        return createMessageIdManager(mailboxManager, new InMemoryMessageId.Factory());
    }

    public MessageIdManager createMessageIdManager(StoreMailboxManager mailboxManager, MessageId.Factory factory) {
        QuotaComponents quotaComponents = mailboxManager.getQuotaComponents();
        return new StoreMessageIdManager(
            mailboxManager,
            mailboxManager.getMapperFactory(),
            mailboxManager.getEventBus(),
            factory,
            quotaComponents.getQuotaManager(),
            quotaComponents.getQuotaRootResolver(),
            mailboxManager.getPreDeletionHooks());
    }

    @Override
    public QuotaManager retrieveQuotaManager(StoreMailboxManager mailboxManager) {
        return mailboxManager.getQuotaComponents().getQuotaManager();
    }

    @Override
    public MaxQuotaManager retrieveMaxQuotaManager(StoreMailboxManager mailboxManager) {
        return mailboxManager.getQuotaComponents().getMaxQuotaManager();
    }

    @Override
    public DefaultUserQuotaRootResolver retrieveQuotaRootResolver(StoreMailboxManager mailboxManager) {
        return (DefaultUserQuotaRootResolver) mailboxManager.getQuotaComponents().getQuotaRootResolver();
    }

    @Override
    public GroupMembershipResolver createGroupMembershipResolver() {
        groupMembershipResolver = new SimpleGroupMembershipResolver();
        return groupMembershipResolver;
    }

    @Override
    public void init() {
    }

    @Override
    public void clean() {
    }

}
