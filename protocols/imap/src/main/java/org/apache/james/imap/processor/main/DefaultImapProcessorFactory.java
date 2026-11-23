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

package org.apache.james.imap.processor.main;

import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.EventBus;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.DefaultMailboxTyper;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.imap.processor.DefaultProcessor;
import org.apache.james.imap.processor.base.UnknownRequestProcessor;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.mailbox.MailboxCounterCorrector;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.sasl.BuiltInSaslMechanismFactories;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class DefaultImapProcessorFactory {

    public static ImapProcessor createDefaultProcessor(MailboxManager mailboxManager, EventBus eventBus, SubscriptionManager subscriptionManager, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
            MetricFactory metricFactory) {
        return createXListSupportingProcessor(mailboxManager, eventBus, subscriptionManager, new DefaultMailboxTyper(), quotaManager, quotaRootResolver, metricFactory,
            FetchProcessor.LocalCacheConfiguration.DEFAULT);
    }

    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                               EventBus eventBus, SubscriptionManager subscriptionManager,
                                                               MailboxTyper mailboxTyper, QuotaManager quotaManager,
                                                               QuotaRootResolver quotaRootResolver, MetricFactory metricFactory,
                                                               FetchProcessor.LocalCacheConfiguration localCacheConfiguration) {

        StatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        UnknownRequestProcessor unknownRequestImapProcessor = new UnknownRequestProcessor(statusResponseFactory);

        return DefaultProcessor.createDefaultProcessor(unknownRequestImapProcessor, mailboxManager,
            eventBus, subscriptionManager, statusResponseFactory, mailboxTyper, quotaManager, quotaRootResolver,
            MailboxCounterCorrector.DEFAULT, metricFactory,
            localCacheConfiguration);
    }

    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                               JamesSaslAuthenticator saslAuthenticator,
                                                               EventBus eventBus, SubscriptionManager subscriptionManager,
                                                               MailboxTyper mailboxTyper, QuotaManager quotaManager,
                                                               QuotaRootResolver quotaRootResolver, MetricFactory metricFactory) {
        return createXListSupportingProcessor(mailboxManager, saslAuthenticator, eventBus, subscriptionManager, mailboxTyper,
            quotaManager, quotaRootResolver, metricFactory, PlainSaslMechanism.requiringTls());
    }

    /**
     * Used by the Spring app, whose processor is shared by all its IMAP servers: see {@link #sharedPlainSaslMechanism}.
     *
     * @param imapServersConfiguration the content of imapserver.xml, holding the imapserver entries
     */
    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                               JamesSaslAuthenticator saslAuthenticator,
                                                               EventBus eventBus, SubscriptionManager subscriptionManager,
                                                               MailboxTyper mailboxTyper, QuotaManager quotaManager,
                                                               QuotaRootResolver quotaRootResolver, MetricFactory metricFactory,
                                                               HierarchicalConfiguration<ImmutableNode> imapServersConfiguration) {
        return createXListSupportingProcessor(mailboxManager, saslAuthenticator, eventBus, subscriptionManager, mailboxTyper,
            quotaManager, quotaRootResolver, metricFactory, sharedPlainSaslMechanism(imapServersConfiguration));
    }

    /**
     * The PLAIN mechanism of a processor shared by several IMAP servers: it requires TLS, as by default, unless every
     * IMAP server allows plain authentication without TLS (plainAuthDisallowed or auth.requireSSL set to false).
     */
    public static PlainSaslMechanism sharedPlainSaslMechanism(HierarchicalConfiguration<ImmutableNode> imapServersConfiguration) {
        PlainSaslMechanismFactory factory = new PlainSaslMechanismFactory();
        List<HierarchicalConfiguration<ImmutableNode>> servers = imapServersConfiguration.configurationsAt("imapserver");
        boolean everyServerAllowsCleartext = !servers.isEmpty() && servers.stream()
            .allMatch(server -> factory.create(server).isAvailableOnTransport(false));
        if (everyServerAllowsCleartext) {
            return new PlainSaslMechanism(true, false);
        }
        return PlainSaslMechanism.requiringTls();
    }

    private static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                                JamesSaslAuthenticator saslAuthenticator,
                                                                EventBus eventBus, SubscriptionManager subscriptionManager,
                                                                MailboxTyper mailboxTyper, QuotaManager quotaManager,
                                                                QuotaRootResolver quotaRootResolver, MetricFactory metricFactory,
                                                                PlainSaslMechanism plainSaslMechanism) {
        StatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        UnknownRequestProcessor unknownRequestImapProcessor = new UnknownRequestProcessor(statusResponseFactory);

        return DefaultProcessor.createDefaultProcessor(unknownRequestImapProcessor, mailboxManager,
            eventBus, subscriptionManager, statusResponseFactory, mailboxTyper, quotaManager, quotaRootResolver,
            MailboxCounterCorrector.DEFAULT, metricFactory,
            FetchProcessor.LocalCacheConfiguration.DEFAULT, ImmutableList.of(plainSaslMechanism), saslAuthenticator);
    }

    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                               EventBus eventBus, SubscriptionManager subscriptionManager,
                                                               MailboxTyper mailboxTyper, QuotaManager quotaManager,
                                                               QuotaRootResolver quotaRootResolver, MetricFactory metricFactory,
                                                               FetchProcessor.LocalCacheConfiguration localCacheConfiguration,
                                                               HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {

        StatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        UnknownRequestProcessor unknownRequestImapProcessor = new UnknownRequestProcessor(statusResponseFactory);

        return DefaultProcessor.createDefaultProcessor(unknownRequestImapProcessor, mailboxManager,
            eventBus, subscriptionManager, statusResponseFactory, mailboxTyper, quotaManager, quotaRootResolver,
            MailboxCounterCorrector.DEFAULT, metricFactory,
            localCacheConfiguration, defaultSaslMechanisms(serverConfiguration));
    }

    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                               EventBus eventBus, SubscriptionManager subscriptionManager,
                                                               MailboxTyper mailboxTyper, QuotaManager quotaManager,
                                                               QuotaRootResolver quotaRootResolver, MetricFactory metricFactory,
                                                               FetchProcessor.LocalCacheConfiguration localCacheConfiguration,
                                                               HierarchicalConfiguration<ImmutableNode> serverConfiguration,
                                                               JamesSaslAuthenticator saslAuthenticator) throws ConfigurationException {

        StatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        UnknownRequestProcessor unknownRequestImapProcessor = new UnknownRequestProcessor(statusResponseFactory);

        return DefaultProcessor.createDefaultProcessor(unknownRequestImapProcessor, mailboxManager,
            eventBus, subscriptionManager, statusResponseFactory, mailboxTyper, quotaManager, quotaRootResolver,
            MailboxCounterCorrector.DEFAULT, metricFactory,
            localCacheConfiguration, defaultSaslMechanisms(serverConfiguration), saslAuthenticator);
    }

    private static ImmutableList<SaslMechanism> defaultSaslMechanisms(HierarchicalConfiguration<ImmutableNode> serverConfiguration) throws ConfigurationException {
        ImmutableList<SaslMechanismFactory> factories = BuiltInSaslMechanismFactories.enabledForServer(
            ImmutableList.of(
                new PlainSaslMechanismFactory(),
                new OauthBearerSaslMechanismFactory(),
                new XOauth2SaslMechanismFactory()),
            serverConfiguration);

        try {
            return factories.stream()
                .map(Throwing.function(factory -> factory.create(serverConfiguration)))
                .collect(ImmutableList.toImmutableList());
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ConfigurationException configurationException) {
                throw configurationException;
            }
            throw e;
        }
    }

}
