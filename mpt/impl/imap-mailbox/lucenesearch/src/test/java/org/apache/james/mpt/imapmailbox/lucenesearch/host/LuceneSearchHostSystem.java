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

package org.apache.james.mpt.imapmailbox.lucenesearch.host;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.lucene.search.LuceneMessageSearchIndex;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.lucene.store.FSDirectory;

import com.github.fge.lambdas.Throwing;
import com.google.common.io.Files;

public class LuceneSearchHostSystem extends JamesImapHostSystem {
    public static final String META_DATA_DIRECTORY = "target/user-meta-data";
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOD_SEQ_SEARCH);


    private File tempFile;
    private InMemoryMailboxManager mailboxManager;
    private LuceneMessageSearchIndex searchIndex;

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        this.tempFile = Files.createTempDir();
        initFields();
    }

    @Override
    public void afterTest() throws Exception {
        tempFile.deleteOnExit();

        resetUserMetaData();
        MailboxSession session = mailboxManager.createSystemSession("test");
        mailboxManager.startProcessingRequest(session);
        mailboxManager.endProcessingRequest(session);
        mailboxManager.logout(session, false);
    }

    public void resetUserMetaData() throws Exception {
        File dir = new File(META_DATA_DIRECTORY);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
        dir.mkdirs();
    }

    private void initFields() {
        try {
            FSDirectory fsDirectory = FSDirectory.open(tempFile);

            InMemoryIntegrationResources resources = new InMemoryIntegrationResources.Factory()
                .withAuthorizator(authorizator)
                .withAuthenticator(authenticator)
                .withListeningSearchIndex(Throwing.function(preInstanciationStage -> new LuceneMessageSearchIndex(
                    preInstanciationStage.getMapperFactory(), new InMemoryId.Factory(), fsDirectory,
                    new InMemoryMessageId.Factory(),
                    preInstanciationStage.getSessionProvider())))
                .create();

            mailboxManager = resources.getMailboxManager();

            searchIndex = (LuceneMessageSearchIndex) resources.getSearchIndex();
            searchIndex.setEnableSuffixMatch(true);
            SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory());

            ImapProcessor defaultImapProcessorFactory =
                DefaultImapProcessorFactory.createDefaultProcessor(
                    mailboxManager,
                    resources.getMailboxManager().getEventBus(),
                    subscriptionManager,
                    new NoQuotaManager(),
                    resources.getDefaultUserQuotaRootResolver(),
                    new DefaultMetricFactory());

            configure(new DefaultImapDecoderFactory().buildImapDecoder(),
                new DefaultImapEncoderFactory().buildImapEncoder(),
                defaultImapProcessorFactory);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    public void setQuotaLimits(QuotaCount maxMessageQuota, QuotaSize maxStorageQuota) {
        throw new NotImplementedException();
    }

    @Override
    protected void await() throws Exception {
        searchIndex.commit();
    }
}