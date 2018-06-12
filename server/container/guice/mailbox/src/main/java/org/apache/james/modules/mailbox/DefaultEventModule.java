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

package org.apache.james.modules.mailbox;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.store.event.AsynchronousEventDelivery;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.DelegatingMailboxListener;
import org.apache.james.mailbox.store.event.EventDelivery;
import org.apache.james.mailbox.store.event.MailboxAnnotationListener;
import org.apache.james.mailbox.store.event.MailboxListenerRegistry;
import org.apache.james.mailbox.store.event.MixedEventDelivery;
import org.apache.james.mailbox.store.event.SynchronousEventDelivery;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.ConfigurationPerformer;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class DefaultEventModule extends AbstractModule {

    private static final int DEFAULT_POOL_SIZE = 8;

    @Override
    protected void configure() {
        bind(DefaultDelegatingMailboxListener.class).in(Scopes.SINGLETON);
        bind(DelegatingMailboxListener.class).to(DefaultDelegatingMailboxListener.class);

        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(ListenerRegistrationPerformer.class);

        bind(ListeningCurrentQuotaUpdater.class).in(Scopes.SINGLETON);
        bind(MailboxAnnotationListener.class).in(Scopes.SINGLETON);

        bind(MailboxListenerFactory.class).in(Scopes.SINGLETON);
        bind(MailboxListenersLoaderImpl.class).in(Scopes.SINGLETON);
        bind(MailboxListenerRegistry.class).in(Scopes.SINGLETON);
        bind(MailboxListenersLoader.class).to(MailboxListenersLoaderImpl.class);
        Multibinder.newSetBinder(binder(), MailboxListener.class);
    }

    @Provides
    @Singleton
    EventDelivery provideEventDelivery(ConfigurationProvider configurationProvider, MetricFactory metricFactory) {
        int poolSize = retrievePoolSize(configurationProvider);

        SynchronousEventDelivery synchronousEventDelivery = new SynchronousEventDelivery(metricFactory);
        return new MixedEventDelivery(
            new AsynchronousEventDelivery(poolSize, synchronousEventDelivery),
            synchronousEventDelivery);
    }

    private int retrievePoolSize(ConfigurationProvider configurationProvider) {
        try {
            return Optional.ofNullable(configurationProvider.getConfiguration("listeners")
                .getInteger("poolSize", null))
                .orElse(DEFAULT_POOL_SIZE);
        } catch (ConfigurationException e) {
            return DEFAULT_POOL_SIZE;
        }
    }

    @Singleton
    public static class ListenerRegistrationPerformer implements ConfigurationPerformer {
        private final ConfigurationProvider configurationProvider;
        private final MailboxListenersLoaderImpl listeners;

        @Inject
        public ListenerRegistrationPerformer(ConfigurationProvider configurationProvider, MailboxListenersLoaderImpl listeners) {
            this.configurationProvider = configurationProvider;
            this.listeners = listeners;
        }

        @Override
        public void initModule() {
            try {
                listeners.configure(configurationProvider.getConfiguration("listeners"));
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of();
        }
    }
}
