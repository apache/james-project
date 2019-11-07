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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
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
import org.apache.lucene.store.RAMDirectory;

import com.github.fge.lambdas.Throwing;

public class LuceneSearchHostSystem extends JamesImapHostSystem {
    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOD_SEQ_SEARCH);

    private InMemoryMailboxManager mailboxManager;
    private LuceneMessageSearchIndex searchIndex;

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        initFields();
    }

    private void initFields() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(authorizator)
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(Throwing.function(preInstanciationStage -> new LuceneMessageSearchIndex(
                preInstanciationStage.getMapperFactory(), new InMemoryId.Factory(), new RAMDirectory(),
                new InMemoryMessageId.Factory(),
                preInstanciationStage.getSessionProvider())))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

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
    protected void await() throws Exception {
        searchIndex.commit();
    }
}