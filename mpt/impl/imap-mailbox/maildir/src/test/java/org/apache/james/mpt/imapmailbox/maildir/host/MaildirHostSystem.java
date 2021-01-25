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
package org.apache.james.mpt.imapmailbox.maildir.host;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.events.EventBusTestFixture;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.maildir.MaildirMailboxSessionMapperFactory;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxManagerConfiguration;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;

public class MaildirHostSystem extends JamesImapHostSystem {

    private static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final String MAILDIR_HOME = "target/Maildir";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of();
    
    private StoreMailboxManager mailboxManager;
    
    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        MaildirStore store = new MaildirStore(MAILDIR_HOME + "/%user", locker);
        MaildirMailboxSessionMapperFactory mailboxSessionMapperFactory = new MaildirMailboxSessionMapperFactory(store);
        StoreSubscriptionManager sm = new StoreSubscriptionManager(mailboxSessionMapperFactory);
        
        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters());
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, aclResolver, groupMembershipResolver, eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);
        SessionProviderImpl sessionProvider = new SessionProviderImpl(authenticator, authorizator);
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mailboxSessionMapperFactory);
        MessageSearchIndex index = new SimpleMessageSearchIndex(mailboxSessionMapperFactory, mailboxSessionMapperFactory, new DefaultTextExtractor(), null);

        mailboxManager = new StoreMailboxManager(mailboxSessionMapperFactory, sessionProvider, locker,
            messageParser, new DefaultMessageId.Factory(), annotationManager, eventBus, storeRightManager, quotaComponents,
            index, MailboxManagerConfiguration.DEFAULT, PreDeletionHooks.NO_PRE_DELETION_HOOK);

        ImapProcessor defaultImapProcessorFactory =
                DefaultImapProcessorFactory.createDefaultProcessor(
                        mailboxManager,
                        eventBus,
                        sm, 
                        quotaComponents.getQuotaManager(),
                        quotaComponents.getQuotaRootResolver(),
                        new DefaultMetricFactory());
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
        (new File(MAILDIR_HOME)).mkdirs();
    }


    @Override
    public void afterTest() throws Exception {
        resetUserMetaData();
        try {
            FileUtils.deleteDirectory(new File(MAILDIR_HOME));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void resetUserMetaData() throws Exception {
        File dir = new File(META_DATA_DIRECTORY);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();
    }

    @Override
    protected MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(QuotaCountLimit maxMessageQuota, QuotaSizeLimit maxStorageQuota) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    protected void await() {

    }
}
