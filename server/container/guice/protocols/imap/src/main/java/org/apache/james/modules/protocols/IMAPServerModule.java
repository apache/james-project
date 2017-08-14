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

import java.util.List;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.modules.Names;
import org.apache.james.utils.ConfigurationPerformer;
import org.apache.james.utils.ConfigurationProvider;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;

public class IMAPServerModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), ConfigurationPerformer.class).addBinding().to(IMAPModuleConfigurationPerformer.class);
    }

    @Provides
    ImapProcessor provideImapProcessor(
            @Named(Names.MAILBOXMANAGER_NAME)MailboxManager mailboxManager,
            SubscriptionManager subscriptionManager,
            QuotaManager quotaManager,
            QuotaRootResolver quotaRootResolver,
            MetricFactory metricFactory) {
        return DefaultImapProcessorFactory.createXListSupportingProcessor(
                mailboxManager,
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

    @Singleton
    public static class IMAPModuleConfigurationPerformer implements ConfigurationPerformer {

        private final ConfigurationProvider configurationProvider;
        private final IMAPServerFactory imapServerFactory;

        @Inject
        public IMAPModuleConfigurationPerformer(ConfigurationProvider configurationProvider, IMAPServerFactory imapServerFactory) {
            this.configurationProvider = configurationProvider;
            this.imapServerFactory = imapServerFactory;
        }

        @Override
        public void initModule()  {
            try {
                imapServerFactory.configure(configurationProvider.getConfiguration("imapserver"));
                imapServerFactory.init();
            } catch (Exception e) {
                Throwables.propagate(e);
            }
        }

        @Override
        public List<Class<? extends Configurable>> forClasses() {
            return ImmutableList.of(IMAPServerFactory.class);
        }
    }
}