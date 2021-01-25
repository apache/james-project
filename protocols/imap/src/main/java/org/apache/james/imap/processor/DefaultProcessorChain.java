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

import org.apache.james.events.EventBus;
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
                                                   MailboxManager mailboxManager,
                                                   EventBus eventBus,
                                                   SubscriptionManager subscriptionManager,
                                                   StatusResponseFactory statusResponseFactory,
                                                   MailboxTyper mailboxTyper,
                                                   QuotaManager quotaManager,
                                                   QuotaRootResolver quotaRootResolver,
                                                   MetricFactory metricFactory) {

        SystemMessageProcessor systemProcessor = new SystemMessageProcessor(chainEndProcessor, mailboxManager);
        LogoutProcessor logoutProcessor = new LogoutProcessor(systemProcessor, mailboxManager, statusResponseFactory, metricFactory);

        CapabilityProcessor capabilityProcessor = new CapabilityProcessor(logoutProcessor, mailboxManager, statusResponseFactory, metricFactory);
        CheckProcessor checkProcessor = new CheckProcessor(capabilityProcessor, mailboxManager, statusResponseFactory, metricFactory);
        LoginProcessor loginProcessor = new LoginProcessor(checkProcessor, mailboxManager, statusResponseFactory, metricFactory);
        // so it can announce the LOGINDISABLED if needed
        capabilityProcessor.addProcessor(loginProcessor);
        
        RenameProcessor renameProcessor = new RenameProcessor(loginProcessor, mailboxManager, statusResponseFactory, metricFactory);
        DeleteProcessor deleteProcessor = new DeleteProcessor(renameProcessor, mailboxManager, statusResponseFactory, metricFactory);
        CreateProcessor createProcessor = new CreateProcessor(deleteProcessor, mailboxManager, statusResponseFactory, metricFactory);
        CloseProcessor closeProcessor = new CloseProcessor(createProcessor, mailboxManager, statusResponseFactory, metricFactory);
        UnsubscribeProcessor unsubscribeProcessor = new UnsubscribeProcessor(closeProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        SubscribeProcessor subscribeProcessor;
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Annotation)) {
            SetAnnotationProcessor setAnnotationProcessor = new SetAnnotationProcessor(unsubscribeProcessor, mailboxManager, statusResponseFactory, metricFactory);
            capabilityProcessor.addProcessor(setAnnotationProcessor);
            GetAnnotationProcessor getAnnotationProcessor = new GetAnnotationProcessor(setAnnotationProcessor, mailboxManager, statusResponseFactory, metricFactory);
            capabilityProcessor.addProcessor(getAnnotationProcessor);
            subscribeProcessor = new SubscribeProcessor(getAnnotationProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        } else {
            subscribeProcessor = new SubscribeProcessor(unsubscribeProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        }
        CopyProcessor copyProcessor = new CopyProcessor(subscribeProcessor, mailboxManager, statusResponseFactory, metricFactory);
        AuthenticateProcessor authenticateProcessor;
        if (mailboxManager.hasCapability(MailboxManager.MailboxCapabilities.Move)) {
            MoveProcessor moveProcessor = new MoveProcessor(copyProcessor, mailboxManager, statusResponseFactory, metricFactory);
            authenticateProcessor = new AuthenticateProcessor(moveProcessor, mailboxManager, statusResponseFactory, metricFactory);
            capabilityProcessor.addProcessor(moveProcessor);
        } else {
            authenticateProcessor = new AuthenticateProcessor(copyProcessor, mailboxManager, statusResponseFactory, metricFactory);
        }
        ExpungeProcessor expungeProcessor = new ExpungeProcessor(authenticateProcessor, mailboxManager, statusResponseFactory, metricFactory);
        ExamineProcessor examineProcessor = new ExamineProcessor(expungeProcessor, mailboxManager, eventBus, statusResponseFactory, metricFactory);
        AppendProcessor appendProcessor = new AppendProcessor(examineProcessor, mailboxManager, statusResponseFactory, metricFactory);
        StoreProcessor storeProcessor = new StoreProcessor(appendProcessor, mailboxManager, statusResponseFactory, metricFactory);
        NoopProcessor noopProcessor = new NoopProcessor(storeProcessor, mailboxManager, statusResponseFactory, metricFactory);
        IdleProcessor idleProcessor = new IdleProcessor(noopProcessor, mailboxManager, eventBus, statusResponseFactory, metricFactory);
        StatusProcessor statusProcessor = new StatusProcessor(idleProcessor, mailboxManager, statusResponseFactory, metricFactory);
        LSubProcessor lsubProcessor = new LSubProcessor(statusProcessor, mailboxManager, subscriptionManager, statusResponseFactory, metricFactory);
        XListProcessor xlistProcessor = new XListProcessor(lsubProcessor, mailboxManager, statusResponseFactory, mailboxTyper, metricFactory);
        ListProcessor listProcessor = new ListProcessor(xlistProcessor, mailboxManager, statusResponseFactory, metricFactory);
        SearchProcessor searchProcessor = new SearchProcessor(listProcessor, mailboxManager, statusResponseFactory, metricFactory);
        // WITHIN extension
        capabilityProcessor.addProcessor(searchProcessor);

        SelectProcessor selectProcessor = new SelectProcessor(searchProcessor, mailboxManager, eventBus, statusResponseFactory, metricFactory);
        NamespaceProcessor namespaceProcessor = new NamespaceProcessor(selectProcessor, mailboxManager, statusResponseFactory, metricFactory);

        capabilityProcessor.addProcessor(xlistProcessor);

        ImapProcessor fetchProcessor = new FetchProcessor(namespaceProcessor, mailboxManager, statusResponseFactory, metricFactory);
        StartTLSProcessor startTLSProcessor = new StartTLSProcessor(fetchProcessor, statusResponseFactory);

        UnselectProcessor unselectProcessor = new UnselectProcessor(startTLSProcessor, mailboxManager, statusResponseFactory, metricFactory);

        CompressProcessor compressProcessor = new CompressProcessor(unselectProcessor, statusResponseFactory);
        
        GetACLProcessor getACLProcessor = new GetACLProcessor(compressProcessor, mailboxManager, statusResponseFactory, metricFactory);
        SetACLProcessor setACLProcessor = new SetACLProcessor(getACLProcessor, mailboxManager, statusResponseFactory, metricFactory);
        DeleteACLProcessor deleteACLProcessor = new DeleteACLProcessor(setACLProcessor, mailboxManager, statusResponseFactory, metricFactory);
        ListRightsProcessor listRightsProcessor = new ListRightsProcessor(deleteACLProcessor, mailboxManager, statusResponseFactory, metricFactory);
        MyRightsProcessor myRightsProcessor = new MyRightsProcessor(listRightsProcessor, mailboxManager, statusResponseFactory, metricFactory);
        
        EnableProcessor enableProcessor = new EnableProcessor(myRightsProcessor, mailboxManager, statusResponseFactory, metricFactory, capabilityProcessor);

        GetQuotaProcessor getQuotaProcessor = new GetQuotaProcessor(enableProcessor, mailboxManager, statusResponseFactory, quotaManager, quotaRootResolver, metricFactory);
        SetQuotaProcessor setQuotaProcessor = new SetQuotaProcessor(getQuotaProcessor, mailboxManager, statusResponseFactory, metricFactory);
        GetQuotaRootProcessor getQuotaRootProcessor = new GetQuotaRootProcessor(setQuotaProcessor, mailboxManager, statusResponseFactory, quotaRootResolver, quotaManager, metricFactory);
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
