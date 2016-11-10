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

package org.apache.james.mpt.imapmailbox.inmemory.host;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.AsynchronousEventDelivery;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

public class InMemoryEventAsynchronousHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    private StoreMailboxManager mailboxManager;
    private FakeAuthenticator userManager;

    public static JamesImapHostSystem build() throws Exception {
        return new InMemoryEventAsynchronousHostSystem();
    }

    private InMemoryEventAsynchronousHostSystem() throws MailboxException {
        initFields();
    }
    
    public boolean addUser(String user, String password) throws Exception {
        userManager.addUser(user, password);
        return true;
    }

    @Override
    protected void resetData() throws Exception {
        initFields();
    }
    
    private void initFields() throws MailboxException {
        userManager = new FakeAuthenticator();
        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        mailboxManager = new StoreMailboxManager(factory, userManager, aclResolver, groupMembershipResolver, messageParser, 
                new DefaultMessageId.Factory(), MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
        QuotaRootResolver quotaRootResolver = new DefaultQuotaRootResolver(factory);

        InMemoryPerUserMaxQuotaManager perUserMaxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        perUserMaxQuotaManager.setDefaultMaxMessage(4096);
        perUserMaxQuotaManager.setDefaultMaxStorage(5L * 1024L * 1024L * 1024L);

        InMemoryCurrentQuotaManager currentQuotaManager = new InMemoryCurrentQuotaManager(
            new CurrentQuotaCalculator(factory, quotaRootResolver),
            mailboxManager);

        StoreQuotaManager quotaManager = new StoreQuotaManager();
        quotaManager.setMaxQuotaManager(perUserMaxQuotaManager);
        quotaManager.setCurrentQuotaManager(currentQuotaManager);

        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater();
        quotaUpdater.setCurrentQuotaManager(currentQuotaManager);
        quotaUpdater.setQuotaRootResolver(quotaRootResolver);

        mailboxManager.setQuotaRootResolver(quotaRootResolver);
        mailboxManager.setQuotaManager(quotaManager);
        mailboxManager.setQuotaUpdater(quotaUpdater);

        mailboxManager.setDelegatingMailboxListener(new DefaultDelegatingMailboxListener(new AsynchronousEventDelivery(10)));

        mailboxManager.init();

        final ImapProcessor defaultImapProcessorFactory = DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager, new StoreSubscriptionManager(factory), quotaManager, quotaRootResolver);
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
    }

    @Override
    public void createMailbox(MailboxPath mailboxPath) throws Exception{
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }
    
    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }
    
}
