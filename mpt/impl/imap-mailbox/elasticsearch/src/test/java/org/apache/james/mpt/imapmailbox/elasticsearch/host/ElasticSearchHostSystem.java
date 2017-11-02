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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.james.backends.es.DeleteByQueryPerformer;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxMappingFactory;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.elasticsearch.client.Client;

import com.google.common.base.Throwables;

public class ElasticSearchHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);

    private EmbeddedElasticSearch embeddedElasticSearch;
    private Path tempDirectory;
    private StoreMailboxManager mailboxManager;


    @Override
    public void beforeTest() throws Exception {
        super.beforeTest();
        this.tempDirectory = Files.createTempDirectory("elasticsearch");
        this.embeddedElasticSearch = new EmbeddedElasticSearch(tempDirectory, MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX);
        embeddedElasticSearch.before();
        initFields();
    }

    @Override
    public void afterTest() throws Exception {
        embeddedElasticSearch.after();
        FileUtils.deleteDirectory(tempDirectory.toFile());
    }

    private void initFields() {
        Client client = NodeMappingFactory.applyMapping(
            new IndexCreationFactory()
                .useIndex(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX)
                .addAlias(MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS)
                .addAlias(MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS)
                .createIndexAndAliases(new TestingClientProvider(embeddedElasticSearch.getNode()).get()),
            MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX,
            MailboxElasticSearchConstants.MESSAGE_TYPE,
            MailboxMappingFactory.getMappingContent());

        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        ElasticSearchListeningMessageSearchIndex searchIndex = new ElasticSearchListeningMessageSearchIndex(
            factory,
            new ElasticSearchIndexer(client,
                new DeleteByQueryPerformer(client, Executors.newSingleThreadExecutor(), MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS, MailboxElasticSearchConstants.MESSAGE_TYPE),
                MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS,
                MailboxElasticSearchConstants.MESSAGE_TYPE),
            new ElasticSearchSearcher(client,
                new QueryConverter(new CriterionConverter()), new InMemoryId.Factory(), messageIdFactory,
                MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS, MailboxElasticSearchConstants.MESSAGE_TYPE),
            new MessageToElasticSearchJson(new DefaultTextExtractor(), ZoneId.systemDefault(), IndexAttachments.YES));

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        StoreRightManager storeRightManager = new StoreRightManager(factory, aclResolver, groupMembershipResolver);

        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        mailboxManager = new StoreMailboxManager(factory, authenticator, authorizator, new JVMMailboxPathLocker(),
            messageParser, messageIdFactory, mailboxEventDispatcher, delegatingListener, storeRightManager);
        mailboxManager.setMessageSearchIndex(searchIndex);

        try {
            mailboxManager.init();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }

        final ImapProcessor defaultImapProcessorFactory =
            DefaultImapProcessorFactory.createDefaultProcessor(mailboxManager,
                new StoreSubscriptionManager(factory),
                new NoQuotaManager(),
                new DefaultQuotaRootResolver(factory),
                new DefaultMetricFactory());
        configure(new DefaultImapDecoderFactory().buildImapDecoder(),
            new DefaultImapEncoderFactory().buildImapEncoder(),
            defaultImapProcessorFactory);

        embeddedElasticSearch.awaitForElasticSearch();
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
    public void setQuotaLimits(long maxMessageQuota, long maxStorageQuota) throws Exception {
        throw new NotImplementedException();
    }
}