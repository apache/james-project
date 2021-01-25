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
package org.apache.james.modules.protocols;

import org.apache.james.events.EventBus;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.imapserver.netty.OioIMAPServerFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
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

public class IMAPServerModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(IMAPServerFactory.class).in(Scopes.SINGLETON);
        bind(OioIMAPServerFactory.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(ImapGuiceProbe.class);
    }

    @Provides
    ImapProcessor provideImapProcessor(
            MailboxManager mailboxManager,
            EventBus eventBus,
            SubscriptionManager subscriptionManager,
            QuotaManager quotaManager,
            QuotaRootResolver quotaRootResolver,
            MetricFactory metricFactory) {
        return DefaultImapProcessorFactory.createXListSupportingProcessor(
                mailboxManager,
                eventBus,
                subscriptionManager,
                null,
                quotaManager,
                quotaRootResolver,
                metricFactory);
    }

    @Provides
    @Singleton
    ImapDecoder provideImapDecoder() {
        return DefaultImapDecoderFactory.createDecoder();
    }

    @Provides
    @Singleton
    ImapEncoder provideImapEncoder() {
        return new DefaultImapEncoderFactory().buildImapEncoder();
    }

    @ProvidesIntoSet
    InitializationOperation configureImap(ConfigurationProvider configurationProvider, IMAPServerFactory imapServerFactory) {
        return InitilizationOperationBuilder
            .forClass(IMAPServerFactory.class)
            .init(() -> {
                imapServerFactory.configure(configurationProvider.getConfiguration("imapserver"));
                imapServerFactory.init();
            });
    }
}