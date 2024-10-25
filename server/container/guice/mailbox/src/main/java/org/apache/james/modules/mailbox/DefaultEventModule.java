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

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.EventDelivery;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.modules.EventDeadLettersProbe;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class DefaultEventModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MailboxListenerFactory.class).in(Scopes.SINGLETON);
        bind(MailboxListenersLoaderImpl.class).in(Scopes.SINGLETON);
        bind(InVmEventDelivery.class).in(Scopes.SINGLETON);
        bind(InVMEventBus.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(EventDeadLettersProbe.class);
        bind(MailboxListenersLoader.class).to(MailboxListenersLoaderImpl.class);
        bind(EventDelivery.class).to(InVmEventDelivery.class);
        bind(EventBus.class).to(InVMEventBus.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);

        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class);
    }

    @Provides
    @Singleton
    ListenersConfiguration providesConfiguration(ConfigurationProvider configurationProvider) throws ConfigurationException {
        return ListenersConfiguration.from(configurationProvider.getConfiguration("listeners"));
    }

    @ProvidesIntoSet
    InitializationOperation registerListeners(MailboxListenersLoaderImpl listeners, ListenersConfiguration configuration) {
        return InitilizationOperationBuilder
            .forClass(MailboxListenersLoaderImpl.class)
            .init(() -> listeners.configure(configuration));
    }
}
