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

package org.apache.james.mpt.imapmailbox.jpa.host;

import java.io.File;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.jpa.JPAMailboxSessionMapperFactory;
import org.apache.james.mailbox.jpa.JPASubscriptionManager;
import org.apache.james.mailbox.jpa.mail.JPAModSeqProvider;
import org.apache.james.mailbox.jpa.mail.JPAUidProvider;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.apache.james.mailbox.jpa.quota.JPAPerUserMaxQuotaDAO;
import org.apache.james.mailbox.jpa.quota.JPAPerUserMaxQuotaManager;
import org.apache.james.mailbox.jpa.quota.JpaCurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
import org.apache.james.mailbox.store.quota.ListeningCurrentQuotaUpdater;
import org.apache.james.mailbox.store.quota.StoreQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;

import com.google.common.collect.ImmutableList;

public class JPAHostSystem extends JamesImapHostSystem {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(
        ImmutableList.<Class<?>>builder()
            .addAll(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES)
            .addAll(JPAMailboxFixture.QUOTA_PERSISTANCE_CLASSES)
            .build());

    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.USER_FLAGS_SUPPORT,
        Feature.ANNOTATION_SUPPORT,
        Feature.QUOTA_SUPPORT);

    public static JamesImapHostSystem build() throws Exception {
        return new JPAHostSystem();
    }
    
    private JPAPerUserMaxQuotaManager maxQuotaManager;
    private OpenJPAMailboxManager mailboxManager;

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        EntityManagerFactory entityManagerFactory = JPA_TEST_CLUSTER.getEntityManagerFactory();
        JVMMailboxPathLocker locker = new JVMMailboxPathLocker();
        JPAUidProvider uidProvider = new JPAUidProvider(locker, entityManagerFactory);
        JPAModSeqProvider modSeqProvider = new JPAModSeqProvider(locker, entityManagerFactory);
        JPAMailboxSessionMapperFactory mapperFactory = new JPAMailboxSessionMapperFactory(entityManagerFactory, uidProvider, modSeqProvider);

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        StoreRightManager storeRightManager = new StoreRightManager(mapperFactory, aclResolver, groupMembershipResolver, mailboxEventDispatcher);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mapperFactory, storeRightManager);
        mailboxManager = new OpenJPAMailboxManager(mapperFactory, authenticator, authorizator,
            messageParser, new DefaultMessageId.Factory(), delegatingListener,
            mailboxEventDispatcher, annotationManager, storeRightManager);

        DefaultUserQuotaRootResolver quotaRootResolver = new DefaultUserQuotaRootResolver(mapperFactory);
        JpaCurrentQuotaManager currentQuotaManager = new JpaCurrentQuotaManager(entityManagerFactory);
        maxQuotaManager = new JPAPerUserMaxQuotaManager(new JPAPerUserMaxQuotaDAO(entityManagerFactory));
        StoreQuotaManager storeQuotaManager = new StoreQuotaManager(currentQuotaManager, maxQuotaManager);
        ListeningCurrentQuotaUpdater quotaUpdater = new ListeningCurrentQuotaUpdater(currentQuotaManager, quotaRootResolver, mailboxEventDispatcher, storeQuotaManager);

        mailboxManager.setQuotaManager(storeQuotaManager);
        mailboxManager.setQuotaUpdater(quotaUpdater);
        mailboxManager.setQuotaRootResolver(quotaRootResolver);
        mailboxManager.init();

        SubscriptionManager subscriptionManager = new JPASubscriptionManager(mapperFactory);
        
        final ImapProcessor defaultImapProcessorFactory = 
                DefaultImapProcessorFactory.createDefaultProcessor(
                        mailboxManager, 
                        subscriptionManager, 
                        storeQuotaManager,
                        quotaRootResolver,
                        new DefaultMetricFactory());

        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);
    }

    @Override
    public void afterTest() throws Exception {
        resetUserMetaData();
        if (mailboxManager != null) {
            MailboxSession session = mailboxManager.createSystemSession("test");
            mailboxManager.startProcessingRequest(session);
            mailboxManager.deleteEverything(session);
            mailboxManager.endProcessingRequest(session);
            mailboxManager.logout(session, false);
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
    public void setQuotaLimits(QuotaCount maxMessageQuota, QuotaSize maxStorageQuota) throws Exception {
        maxQuotaManager.setGlobalMaxMessage(maxMessageQuota);
        maxQuotaManager.setGlobalMaxStorage(maxStorageQuota);
    }

}
