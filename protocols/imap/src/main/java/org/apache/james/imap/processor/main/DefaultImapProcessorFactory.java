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

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;
import org.apache.james.imap.processor.DefaultProcessorChain;
import org.apache.james.imap.processor.base.ImapResponseMessageProcessor;
import org.apache.james.imap.processor.base.UnknownRequestProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;

/**
 * 
 */
public class DefaultImapProcessorFactory {

    public static ImapProcessor createDefaultProcessor(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
            MetricFactory metricFactory) {
        return createXListSupportingProcessor(mailboxManager, subscriptionManager, null, quotaManager, quotaRootResolver, metricFactory);
    }

    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager, SubscriptionManager subscriptionManager,
            MailboxTyper mailboxTyper, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, MetricFactory metricFactory) {
        final StatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        final UnknownRequestProcessor unknownRequestImapProcessor = new UnknownRequestProcessor(statusResponseFactory);
        final ImapProcessor imap4rev1Chain = DefaultProcessorChain.createDefaultChain(unknownRequestImapProcessor, mailboxManager, subscriptionManager, statusResponseFactory, mailboxTyper, quotaManager, quotaRootResolver, metricFactory);
        return new ImapResponseMessageProcessor(imap4rev1Chain);
    }

    private MailboxManager mailboxManager;
    private SubscriptionManager subscriptionManager;
    private MailboxTyper mailboxTyper;
    private QuotaManager quotaManager;
    private QuotaRootResolver quotaRootResolver;
    private MetricFactory metricFactory;

    public final void setMailboxManager(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public final void setSubscriptionManager(SubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    public void setMailboxTyper(MailboxTyper mailboxTyper) {
        this.mailboxTyper = mailboxTyper;
    }

    public void setQuotaManager(QuotaManager quotaManager) {
        this.quotaManager = quotaManager;
    }

    public void setQuotaRootResolver(QuotaRootResolver quotaRootResolver) {
        this.quotaRootResolver = quotaRootResolver;
    }

    public final MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    public final SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    public MailboxTyper getMailboxTyper() {
        return mailboxTyper;
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    public QuotaRootResolver getQuotaRootResolver() {
        return quotaRootResolver;
    }

    public MetricFactory getMetricFactory() {
        return metricFactory;
    }

    public void setMetricFactory(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * Create the {@link ImapProcessor}
     */
    public ImapProcessor buildImapProcessor() {
        return createDefaultProcessor(mailboxManager, subscriptionManager, quotaManager, quotaRootResolver, metricFactory);
    }

}
