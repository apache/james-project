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

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.processor.fetch.FetchProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;

/**
 * TODO: perhaps this should be a POJO
 */
public class DefaultProcessorChain {

    public static ImapProcessor createDefaultChain(ImapProcessor chainEndProcessor,
                  final MailboxManager mailboxManager, SubscriptionManager subscriptionManager,
                  final StatusResponseFactory statusResponseFactory, MailboxTyper mailboxTyper, QuotaManager quotaManager,
                  final QuotaRootResolver quotaRootResolver, long idleKeepAlive, TimeUnit milliseconds, Set<String> disabledCaps,
                  MetricFactory metricFactory) {
        final SystemMessageProcessor systemProcessor = new SystemMessageProcessor(chainEndProcessor, mailboxManager);
        final LogoutProcessor logoutProcessor = new LogoutProcessor(systemProcessor, mailboxManager, statusResponseFactory, metricFactory);

        final CapabilityProcessor capabilityProcessor = new CapabilityProcessor(logoutProcessor, mailboxManager, statusResponseFactory, disabledCaps, metricFactory);
        final CheckProcessor checkProcessor = new CheckProcessor(capabilityProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final LoginProcessor loginProcessor = new LoginProcessor(checkProcessor, mailboxManager, statusResponseFactory, metricFactory);
        // so it can announce the LOGINDISABLED if needed
        capabilityProcessor.addProcessor(loginProcessor);
        
        final RenameProcessor renameProcessor = new RenameProcessor(loginProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final DeleteProcessor deleteProcessor = new DeleteProcessor(renameProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final CreateProcessor createProcessor = new CreateProcessor(deleteProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final CloseProcessor closeProcessor = new CloseProcessor(createProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final UnsubscribeProcessor unsubscribeProcessor = new UnsubscribeProcessor(closeProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        final SubscribeProcessor subscribeProcessor;
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Annotation)) {
            final SetAnnotationProcessor setAnnotationProcessor = new SetAnnotationProcessor(unsubscribeProcessor, mailboxManager, statusResponseFactory, metricFactory);
            capabilityProcessor.addProcessor(setAnnotationProcessor);
            final GetAnnotationProcessor getAnnotationProcessor = new GetAnnotationProcessor(setAnnotationProcessor, mailboxManager, statusResponseFactory, metricFactory);
            capabilityProcessor.addProcessor(getAnnotationProcessor);
            subscribeProcessor = new SubscribeProcessor(getAnnotationProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        } else {
            subscribeProcessor = new SubscribeProcessor(unsubscribeProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        }
        final CopyProcessor copyProcessor = new CopyProcessor(subscribeProcessor, mailboxManager, statusResponseFactory, metricFactory);
        AuthenticateProcessor authenticateProcessor;
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)) {
            final MoveProcessor moveProcessor = new MoveProcessor(copyProcessor, mailboxManager, statusResponseFactory, metricFactory);
            authenticateProcessor = new AuthenticateProcessor(moveProcessor, mailboxManager, statusResponseFactory, metricFactory);
            capabilityProcessor.addProcessor(moveProcessor);
        } else {
            authenticateProcessor = new AuthenticateProcessor(copyProcessor, mailboxManager, statusResponseFactory, metricFactory);
        }
        final ExpungeProcessor expungeProcessor = new ExpungeProcessor(authenticateProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final ExamineProcessor examineProcessor = new ExamineProcessor(expungeProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final AppendProcessor appendProcessor = new AppendProcessor(examineProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final StoreProcessor storeProcessor = new StoreProcessor(appendProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final NoopProcessor noopProcessor = new NoopProcessor(storeProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final IdleProcessor idleProcessor;
        if (idleKeepAlive > 0) {
            idleProcessor = new IdleProcessor(noopProcessor, mailboxManager, statusResponseFactory, idleKeepAlive, milliseconds, Executors.newScheduledThreadPool(IdleProcessor.DEFAULT_SCHEDULED_POOL_CORE_SIZE), metricFactory);
        } else {
            // We don't want to send keep alives so now scheduled executur needed
            idleProcessor = new IdleProcessor(noopProcessor, mailboxManager, statusResponseFactory, idleKeepAlive, milliseconds, null, metricFactory);
        }
        final StatusProcessor statusProcessor = new StatusProcessor(idleProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final LSubProcessor lsubProcessor = new LSubProcessor(statusProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        final XListProcessor xlistProcessor = new XListProcessor(lsubProcessor, mailboxManager, statusResponseFactory, mailboxTyper, metricFactory);
        final ListProcessor listProcessor = new ListProcessor(xlistProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final SearchProcessor searchProcessor = new SearchProcessor(listProcessor, mailboxManager, statusResponseFactory, metricFactory);
        // WITHIN extension
        capabilityProcessor.addProcessor(searchProcessor);

        final SelectProcessor selectProcessor = new SelectProcessor(searchProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final NamespaceProcessor namespaceProcessor = new NamespaceProcessor(selectProcessor, mailboxManager, statusResponseFactory, metricFactory);

        capabilityProcessor.addProcessor(xlistProcessor);

        final ImapProcessor fetchProcessor = new FetchProcessor(namespaceProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final StartTLSProcessor startTLSProcessor = new StartTLSProcessor(fetchProcessor, statusResponseFactory);

        final UnselectProcessor unselectProcessor = new UnselectProcessor(startTLSProcessor, mailboxManager, statusResponseFactory, metricFactory);

        final CompressProcessor compressProcessor = new CompressProcessor(unselectProcessor, statusResponseFactory);
        
        final GetACLProcessor getACLProcessor = new GetACLProcessor(compressProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final SetACLProcessor setACLProcessor = new SetACLProcessor(getACLProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final DeleteACLProcessor deleteACLProcessor = new DeleteACLProcessor(setACLProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final ListRightsProcessor listRightsProcessor = new ListRightsProcessor(deleteACLProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final MyRightsProcessor myRightsProcessor = new MyRightsProcessor(listRightsProcessor, mailboxManager, statusResponseFactory, metricFactory);
        
        final EnableProcessor enableProcessor = new EnableProcessor(myRightsProcessor, mailboxManager, statusResponseFactory, metricFactory);

        final GetQuotaProcessor getQuotaProcessor = new GetQuotaProcessor(enableProcessor, mailboxManager, statusResponseFactory, quotaManager, quotaRootResolver, metricFactory);
        final SetQuotaProcessor setQuotaProcessor = new SetQuotaProcessor(getQuotaProcessor, mailboxManager, statusResponseFactory, metricFactory);
        final GetQuotaRootProcessor getQuotaRootProcessor = new GetQuotaRootProcessor(setQuotaProcessor, mailboxManager, statusResponseFactory, quotaRootResolver, quotaManager, metricFactory);
        // add for QRESYNC
        enableProcessor.addProcessor(selectProcessor);
        
        capabilityProcessor.addProcessor(startTLSProcessor);
        capabilityProcessor.addProcessor(idleProcessor);
        capabilityProcessor.addProcessor(namespaceProcessor);
        // added to announce UIDPLUS support
        capabilityProcessor.addProcessor(expungeProcessor);

        // announce the UNSELECT extension. See RFC3691
        capabilityProcessor.addProcessor(unselectProcessor);

        // announce the COMPRESS extension. Sew RFC4978
        capabilityProcessor.addProcessor(compressProcessor);
        
        // add to announnce AUTH=PLAIN
        capabilityProcessor.addProcessor(authenticateProcessor);

        // add to announnce ENABLE
        capabilityProcessor.addProcessor(enableProcessor);
        
        // Add to announce QRESYNC
        capabilityProcessor.addProcessor(selectProcessor);
        
        capabilityProcessor.addProcessor(getACLProcessor);

        capabilityProcessor.addProcessor(getQuotaRootProcessor);

        return getQuotaRootProcessor;

    }

}
