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

package org.apache.james.imap.processor;

import java.util.Map;
import java.util.function.Function;

import org.apache.james.events.EventBus;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.message.request.IRAuthenticateRequest;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.imap.processor.base.ImapResponseMessageProcessor;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DefaultProcessor implements ImapProcessor {

    public static ImapProcessor createDefaultProcessor(ImapProcessor chainEndProcessor,
                                                       MailboxManager mailboxManager,
                                                       EventBus eventBus,
                                                       SubscriptionManager subscriptionManager,
                                                       StatusResponseFactory statusResponseFactory,
                                                       MailboxTyper mailboxTyper,
                                                       QuotaManager quotaManager,
                                                       QuotaRootResolver quotaRootResolver,
                                                       MetricFactory metricFactory) {

        ImmutableList.Builder<AbstractProcessor> builder = ImmutableList.builder();
        CapabilityProcessor capabilityProcessor = new CapabilityProcessor(mailboxManager, statusResponseFactory, metricFactory);
        builder.add(new SystemMessageProcessor(mailboxManager));
        builder.add(new LogoutProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(capabilityProcessor);
        builder.add(new CheckProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new LoginProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new RenameProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new DeleteProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new CreateProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new CloseProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new UnsubscribeProcessor(mailboxManager, subscriptionManager, statusResponseFactory, metricFactory));
        builder.add(new SubscribeProcessor(mailboxManager, subscriptionManager, statusResponseFactory, metricFactory));
        builder.add(new CopyProcessor(mailboxManager, statusResponseFactory, metricFactory));
        AuthenticateProcessor authenticateProcessor = new AuthenticateProcessor(mailboxManager, statusResponseFactory, metricFactory);
        builder.add(authenticateProcessor);
        builder.add(new ExpungeProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new ExamineProcessor(mailboxManager, eventBus, statusResponseFactory, metricFactory));
        builder.add(new AppendProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new StoreProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new NoopProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new IdleProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new StatusProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new LSubProcessor(mailboxManager, subscriptionManager, statusResponseFactory, metricFactory));
        builder.add(new XListProcessor(mailboxManager, statusResponseFactory, mailboxTyper, metricFactory));
        builder.add(new ListProcessor<>(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new SearchProcessor(mailboxManager, statusResponseFactory, metricFactory));
        SelectProcessor selectProcessor = new SelectProcessor(mailboxManager, eventBus, statusResponseFactory, metricFactory);
        builder.add(selectProcessor);
        builder.add(new NamespaceProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new FetchProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new StartTLSProcessor(statusResponseFactory));
        builder.add(new UnselectProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new CompressProcessor(statusResponseFactory));
        builder.add(new GetACLProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new SetACLProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new DeleteACLProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new ListRightsProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new MyRightsProcessor(mailboxManager, statusResponseFactory, metricFactory));
        EnableProcessor enableProcessor = new EnableProcessor(mailboxManager, statusResponseFactory, metricFactory, capabilityProcessor);
        builder.add(enableProcessor);
        builder.add(new GetQuotaProcessor(mailboxManager, statusResponseFactory, quotaManager, quotaRootResolver, metricFactory));
        builder.add(new SetQuotaProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new GetQuotaRootProcessor(mailboxManager, statusResponseFactory, quotaRootResolver, quotaManager, metricFactory));
        builder.add(new ImapResponseMessageProcessor());
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)) {
            builder.add(new MoveProcessor(mailboxManager, statusResponseFactory, metricFactory));
        }
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Annotation)) {
            builder.add(new SetAnnotationProcessor(mailboxManager, statusResponseFactory, metricFactory));
            builder.add(new GetAnnotationProcessor(mailboxManager, statusResponseFactory, metricFactory));
        }

        ImmutableList<AbstractProcessor> processors = builder.build();

        processors.stream()
            .filter(CapabilityImplementingProcessor.class::isInstance)
            .map(CapabilityImplementingProcessor.class::cast)
            .forEach(capabilityProcessor::addProcessor);
        // add for QRESYNC
        enableProcessor.addProcessor(selectProcessor);

        ImmutableMap.Builder<Class, ImapProcessor> processorMap = ImmutableMap.<Class, ImapProcessor>builder()
            .putAll(processors.stream()
                .collect(ImmutableMap.toImmutableMap(
                    AbstractProcessor::acceptableClass,
                    Function.identity())))
            .put(IRAuthenticateRequest.class, authenticateProcessor);

        return new DefaultProcessor(processorMap.build(), chainEndProcessor);
    }

    private final Map<Class, ImapProcessor> processorMap;
    private final ImapProcessor chainEndProcessor;

    public DefaultProcessor(Map<Class, ImapProcessor> processorMap, ImapProcessor chainEndProcessor) {
        this.processorMap = processorMap;
        this.chainEndProcessor = chainEndProcessor;
    }

    @Override
    public void process(ImapMessage message, Responder responder, ImapSession session) {
        processorMap.getOrDefault(message.getClass(), chainEndProcessor)
            .process(message, responder, session);
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        processorMap.values()
            .forEach(processor -> processor.configure(imapConfiguration));
    }
}
