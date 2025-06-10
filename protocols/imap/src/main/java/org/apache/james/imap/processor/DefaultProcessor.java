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
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.events.EventBus;
import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.imap.processor.base.ImapResponseMessageProcessor;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.mailbox.MailboxCounterCorrector;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public class DefaultProcessor implements ImapProcessor {

    public static ImapProcessor createDefaultProcessor(ImapProcessor chainEndProcessor,
                                                       MailboxManager mailboxManager,
                                                       EventBus eventBus,
                                                       SubscriptionManager subscriptionManager,
                                                       StatusResponseFactory statusResponseFactory,
                                                       MailboxTyper mailboxTyper,
                                                       QuotaManager quotaManager,
                                                       QuotaRootResolver quotaRootResolver,
                                                       MailboxCounterCorrector mailboxCounterCorrector,
                                                       MetricFactory metricFactory) {
        PathConverter.Factory pathConverterFactory = PathConverter.Factory.DEFAULT;

        ImmutableList.Builder<AbstractProcessor> builder = ImmutableList.builder();
        CapabilityProcessor capabilityProcessor = new CapabilityProcessor(mailboxManager, statusResponseFactory, metricFactory);
        builder.add(new SystemMessageProcessor());
        builder.add(new LogoutProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(capabilityProcessor);
        builder.add(new IdProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new CheckProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new LoginProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new RenameProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new DeleteProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new CreateProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new CloseProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new UnsubscribeProcessor(mailboxManager, subscriptionManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new SubscribeProcessor(mailboxManager, subscriptionManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new CopyProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new AuthenticateProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new ExpungeProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new ReplaceProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new ExamineProcessor(mailboxManager, eventBus, statusResponseFactory, metricFactory, pathConverterFactory, mailboxCounterCorrector));
        builder.add(new AppendProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new StoreProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new NoopProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new IdleProcessor(mailboxManager, statusResponseFactory, metricFactory));
        StatusProcessor statusProcessor = new StatusProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory);
        builder.add(statusProcessor);
        builder.add(new LSubProcessor(mailboxManager, subscriptionManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new XListProcessor(mailboxManager, statusResponseFactory, mailboxTyper, metricFactory, subscriptionManager, pathConverterFactory));
        builder.add(new ListProcessor<>(mailboxManager, statusResponseFactory, metricFactory, subscriptionManager, statusProcessor, mailboxTyper, pathConverterFactory));
        builder.add(new SearchProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new SelectProcessor(mailboxManager, eventBus, statusResponseFactory, metricFactory, pathConverterFactory, mailboxCounterCorrector));
        builder.add(new NamespaceProcessor(mailboxManager, statusResponseFactory, metricFactory, new NamespaceSupplier.Default()));
        builder.add(new FetchProcessor(mailboxManager, statusResponseFactory, metricFactory, FetchProcessor.LocalCacheConfiguration.DEFAULT));
        builder.add(new StartTLSProcessor(statusResponseFactory));
        builder.add(new UnselectProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new CompressProcessor(statusResponseFactory));
        builder.add(new GetACLProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new SetACLProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new DeleteACLProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new ListRightsProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        builder.add(new MyRightsProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        EnableProcessor enableProcessor = new EnableProcessor(mailboxManager, statusResponseFactory, metricFactory, capabilityProcessor);
        builder.add(enableProcessor);
        builder.add(new GetQuotaProcessor(mailboxManager, statusResponseFactory, quotaManager, quotaRootResolver, metricFactory));
        builder.add(new SetQuotaProcessor(mailboxManager, statusResponseFactory, metricFactory));
        builder.add(new GetQuotaRootProcessor(mailboxManager, statusResponseFactory, quotaRootResolver, quotaManager, metricFactory, pathConverterFactory));
        builder.add(new ImapResponseMessageProcessor());
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)) {
            builder.add(new MoveProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        }
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Annotation)) {
            builder.add(new SetMetadataProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
            builder.add(new GetMetadataProcessor(mailboxManager, statusResponseFactory, metricFactory, pathConverterFactory));
        }

        ImmutableList<AbstractProcessor> processors = builder.build();

        processors.stream()
            .filter(CapabilityImplementingProcessor.class::isInstance)
            .map(CapabilityImplementingProcessor.class::cast)
            .forEach(capabilityProcessor::addProcessor);
        processors.stream()
            .filter(PermitEnableCapabilityProcessor.class::isInstance)
            .map(PermitEnableCapabilityProcessor.class::cast)
            .forEach(enableProcessor::addProcessor);

        ImmutableMap<Class, ImapProcessor> processorMap = processors.stream()
            .map(AbstractProcessor.class::cast)
            .flatMap(DefaultProcessor::asPairStream)
            .collect(ImmutableMap.toImmutableMap(
                Pair::getLeft,
                Pair::getRight));

        return new DefaultProcessor(processorMap, chainEndProcessor);
    }

    private static Stream<Pair<Class, AbstractProcessor>> asPairStream(AbstractProcessor p) {
        return p.acceptableClasses()
            .stream().map(clazz -> Pair.of(clazz, p));
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
    public Mono<Void> processReactive(ImapMessage message, Responder responder, ImapSession session) {
        return processorMap.getOrDefault(message.getClass(), chainEndProcessor)
            .processReactive(message, responder, session);
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        processorMap.values()
            .forEach(processor -> processor.configure(imapConfiguration));
    }
}
