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

import org.apache.james.events.EventBus;
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

public class DefaultImapProcessorFactory {

    public static ImapProcessor createDefaultProcessor(MailboxManager mailboxManager, EventBus eventBus, SubscriptionManager subscriptionManager, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
            MetricFactory metricFactory) {
        return createXListSupportingProcessor(mailboxManager, eventBus, subscriptionManager, null, quotaManager, quotaRootResolver, metricFactory);
    }

    public static ImapProcessor createXListSupportingProcessor(MailboxManager mailboxManager,
                                                               EventBus eventBus, SubscriptionManager subscriptionManager,
                                                               MailboxTyper mailboxTyper, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver, MetricFactory metricFactory) {

        StatusResponseFactory statusResponseFactory = new UnpooledStatusResponseFactory();
        UnknownRequestProcessor unknownRequestImapProcessor = new UnknownRequestProcessor(statusResponseFactory);

        ImapProcessor imap4rev1Chain = DefaultProcessorChain.createDefaultChain(unknownRequestImapProcessor, mailboxManager,
            eventBus, subscriptionManager, statusResponseFactory, mailboxTyper, quotaManager, quotaRootResolver, metricFactory);

        return new ImapResponseMessageProcessor(imap4rev1Chain);
    }

}
