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

/**
 * TODO: perhaps this should be a POJO
 */
public class DefaultProcessorChain {

    public static ImapProcessor createDefaultChain(ImapProcessor chainEndProcessor,
                  final MailboxManager mailboxManager, SubscriptionManager subscriptionManager,
                  final StatusResponseFactory statusResponseFactory, MailboxTyper mailboxTyper, QuotaManager quotaManager,
                  final QuotaRootResolver quotaRootResolver, long idleKeepAlive, TimeUnit milliseconds, Set<String> disabledCaps) {
        final SystemMessageProcessor systemProcessor = new SystemMessageProcessor(chainEndProcessor, mailboxManager);
        final LogoutProcessor logoutProcessor = new LogoutProcessor(systemProcessor, mailboxManager, statusResponseFactory);

        final CapabilityProcessor capabilityProcessor = new CapabilityProcessor(logoutProcessor, mailboxManager, statusResponseFactory, disabledCaps);
        final CheckProcessor checkProcessor = new CheckProcessor(capabilityProcessor, mailboxManager, statusResponseFactory);
        final LoginProcessor loginProcessor = new LoginProcessor(checkProcessor, mailboxManager, statusResponseFactory);
        // so it can announce the LOGINDISABLED if needed
        capabilityProcessor.addProcessor(loginProcessor);
        
        final RenameProcessor renameProcessor = new RenameProcessor(loginProcessor, mailboxManager, statusResponseFactory);
        final DeleteProcessor deleteProcessor = new DeleteProcessor(renameProcessor, mailboxManager, statusResponseFactory);
        final CreateProcessor createProcessor = new CreateProcessor(deleteProcessor, mailboxManager, statusResponseFactory);
        final CloseProcessor closeProcessor = new CloseProcessor(createProcessor, mailboxManager, statusResponseFactory);
        final UnsubscribeProcessor unsubscribeProcessor = new UnsubscribeProcessor(closeProcessor, mailboxManager, subscriptionManager, statusResponseFactory);
        final SubscribeProcessor subscribeProcessor;
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Annotation)) {
            final SetAnnotationProcessor setAnnotationProcessor = new SetAnnotationProcessor(unsubscribeProcessor, mailboxManager, statusResponseFactory);
            capabilityProcessor.addProcessor(setAnnotationProcessor);
            subscribeProcessor = new SubscribeProcessor(setAnnotationProcessor, mailboxManager, subscriptionManager, statusResponseFactory);
        } else {
            subscribeProcessor = new SubscribeProcessor(unsubscribeProcessor, mailboxManager, subscriptionManager, statusResponseFactory);
        }
        final CopyProcessor copyProcessor = new CopyProcessor(subscribeProcessor, mailboxManager, statusResponseFactory);
        AuthenticateProcessor authenticateProcessor;
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)) {
            final MoveProcessor moveProcessor = new MoveProcessor(copyProcessor, mailboxManager, statusResponseFactory);
            authenticateProcessor = new AuthenticateProcessor(moveProcessor, mailboxManager, statusResponseFactory);
            capabilityProcessor.addProcessor(moveProcessor);
        } else {
            authenticateProcessor = new AuthenticateProcessor(copyProcessor, mailboxManager, statusResponseFactory);
        }
        final ExpungeProcessor expungeProcessor = new ExpungeProcessor(authenticateProcessor, mailboxManager, statusResponseFactory);
        final ExamineProcessor examineProcessor = new ExamineProcessor(expungeProcessor, mailboxManager, statusResponseFactory);
        final AppendProcessor appendProcessor = new AppendProcessor(examineProcessor, mailboxManager, statusResponseFactory);
        final StoreProcessor storeProcessor = new StoreProcessor(appendProcessor, mailboxManager, statusResponseFactory);
        final NoopProcessor noopProcessor = new NoopProcessor(storeProcessor, mailboxManager, statusResponseFactory);
        final IdleProcessor idleProcessor;
        if (idleKeepAlive > 0) {
            idleProcessor = new IdleProcessor(noopProcessor, mailboxManager, statusResponseFactory, idleKeepAlive, milliseconds, Executors.newScheduledThreadPool(IdleProcessor.DEFAULT_SCHEDULED_POOL_CORE_SIZE));
        } else {
            // We don't want to send keep alives so now scheduled executur needed
            idleProcessor = new IdleProcessor(noopProcessor, mailboxManager, statusResponseFactory, idleKeepAlive, milliseconds, null);
        }
        final StatusProcessor statusProcessor = new StatusProcessor(idleProcessor, mailboxManager, statusResponseFactory);
        final LSubProcessor lsubProcessor = new LSubProcessor(statusProcessor, mailboxManager, subscriptionManager, statusResponseFactory);
        final XListProcessor xlistProcessor = new XListProcessor(lsubProcessor, mailboxManager, statusResponseFactory, mailboxTyper);
        final ListProcessor listProcessor = new ListProcessor(xlistProcessor, mailboxManager, statusResponseFactory);
        final SearchProcessor searchProcessor = new SearchProcessor(listProcessor, mailboxManager, statusResponseFactory);
        // WITHIN extension
        capabilityProcessor.addProcessor(searchProcessor);

        final SelectProcessor selectProcessor = new SelectProcessor(searchProcessor, mailboxManager, statusResponseFactory);
        final NamespaceProcessor namespaceProcessor = new NamespaceProcessor(selectProcessor, mailboxManager, statusResponseFactory);

        capabilityProcessor.addProcessor(xlistProcessor);

        final ImapProcessor fetchProcessor = new FetchProcessor(namespaceProcessor, mailboxManager, statusResponseFactory);
        final StartTLSProcessor startTLSProcessor = new StartTLSProcessor(fetchProcessor, statusResponseFactory);

        final UnselectProcessor unselectProcessor = new UnselectProcessor(startTLSProcessor, mailboxManager, statusResponseFactory);

        final CompressProcessor compressProcessor = new CompressProcessor(unselectProcessor, statusResponseFactory);
        
        final GetACLProcessor getACLProcessor = new GetACLProcessor(compressProcessor, mailboxManager, statusResponseFactory);
        final SetACLProcessor setACLProcessor = new SetACLProcessor(getACLProcessor, mailboxManager, statusResponseFactory);
        final DeleteACLProcessor deleteACLProcessor = new DeleteACLProcessor(setACLProcessor, mailboxManager, statusResponseFactory);
        final ListRightsProcessor listRightsProcessor = new ListRightsProcessor(deleteACLProcessor, mailboxManager, statusResponseFactory);
        final MyRightsProcessor myRightsProcessor = new MyRightsProcessor(listRightsProcessor, mailboxManager, statusResponseFactory);
        
        final EnableProcessor enableProcessor = new EnableProcessor(myRightsProcessor, mailboxManager, statusResponseFactory);

        final GetQuotaProcessor getQuotaProcessor = new GetQuotaProcessor(enableProcessor, mailboxManager, statusResponseFactory, quotaManager, quotaRootResolver);
        final SetQuotaProcessor setQuotaProcessor = new SetQuotaProcessor(getQuotaProcessor, mailboxManager, statusResponseFactory);
        final GetQuotaRootProcessor getQuotaRootProcessor = new GetQuotaRootProcessor(setQuotaProcessor, mailboxManager, statusResponseFactory, quotaRootResolver, quotaManager);
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
