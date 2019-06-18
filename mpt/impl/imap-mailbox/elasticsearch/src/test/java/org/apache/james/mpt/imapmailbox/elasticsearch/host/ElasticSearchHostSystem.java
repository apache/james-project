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

package org.apache.james.mpt.imapmailbox.elasticsearch.host;

import java.time.ZoneId;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.es.DockerElasticSearch;
import org.apache.james.backends.es.DockerElasticSearchSingleton;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticSearchHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT,
        Feature.MOD_SEQ_SEARCH);

    private DockerElasticSearch dockerElasticSearch;
    private StoreMailboxManager mailboxManager;

    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        this.dockerElasticSearch = DockerElasticSearchSingleton.INSTANCE;
        dockerElasticSearch.start();
        initFields();
    }

    @Override
    public void afterTest() {
        dockerElasticSearch.cleanUpData();
    }

    private void initFields() throws Exception {
        RestHighLevelClient client = MailboxIndexCreationUtil.prepareDefaultClient(
            dockerElasticSearch.clientProvider().get(),
            ElasticSearchConfiguration.builder()
                .addHost(dockerElasticSearch.getHttpHost())
                .build());

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .authenticator(authenticator)
            .authorizator(authorizator)
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new ElasticSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                new ElasticSearchIndexer(client,
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
                new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), ElasticSearchSearcher.DEFAULT_SEARCH_SIZE,
                    new InMemoryId.Factory(), messageIdFactory,
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS),
                new MessageToElasticSearchJson(new DefaultTextExtractor(), ZoneId.of("Europe/Paris"), IndexAttachments.YES),
                preInstanciationStage.getSessionProvider()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();

        ImapProcessor defaultImapProcessorFactory =
            DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager,
                resources.getMailboxManager().getEventBus(),
                new StoreSubscriptionManager(mailboxManager.getMapperFactory()),
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
    public void setQuotaLimits(QuotaCount maxMessageQuota, QuotaSize maxStorageQuota) {
        throw new NotImplementedException();
    }

    @Override
    protected void await() {
        dockerElasticSearch.awaitForElasticSearch();
    }
}