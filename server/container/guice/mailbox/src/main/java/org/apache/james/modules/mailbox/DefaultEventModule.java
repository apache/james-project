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

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.EventDeadLetters;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.MemoryEventDeadLetters;
import org.apache.james.mailbox.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.delivery.EventDelivery;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.ConfigurationPerformer;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class DefaultEventModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(ListenerRegistrationPerformer.class);

        bind(MailboxListenerFactory.class).in(Scopes.SINGLETON);
        bind(MailboxListenersLoaderImpl.class).in(Scopes.SINGLETON);
        bind(InVmEventDelivery.class).in(Scopes.SINGLETON);
        bind(InVMEventBus.class).in(Scopes.SINGLETON);
        bind(MemoryEventDeadLetters.class).in(Scopes.SINGLETON);

        bind(EventDeadLetters.class).to(MemoryEventDeadLetters.class);
        bind(MailboxListenersLoader.class).to(MailboxListenersLoaderImpl.class);
        bind(EventDelivery.class).to(InVmEventDelivery.class);
        bind(EventBus.class).to(InVMEventBus.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);

        Multibinder.newSetBinder(binder(), MailboxListener.GroupMailboxListener.class);
    }

    @Provides
    ListenersConfiguration providesConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return ListenersConfiguration.from(configurationProvider.getConfiguration("listeners"));
    }

    @Singleton
    public static class ListenerRegistrationPerformer implements ConfigurationPerformer {
        private final MailboxListenersLoaderImpl listeners;
        private final ListenersConfiguration configuration;

        @Inject
        public ListenerRegistrationPerformer(MailboxListenersLoaderImpl listeners, ListenersConfiguration configuration) {
            this.listeners = listeners;
            this.configuration = configuration;
        }

        @Override
        public void initModule() {
            listeners.configure(configuration);
        }

        @Override
        public List<Class<? extends Startable>> forClasses() {
            return ImmutableList.of(MailboxListenersLoaderImpl.class);
        }
    }
}
