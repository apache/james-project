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
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticsearchConstants;
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
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;
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
        this.embeddedElasticSearch = new EmbeddedElasticSearch(tempDirectory, MailboxElasticsearchConstants.MAILBOX_INDEX);
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
            IndexCreationFactory.createIndex(new TestingClientProvider(embeddedElasticSearch.getNode()).get(), MailboxElasticsearchConstants.MAILBOX_INDEX),
            MailboxElasticsearchConstants.MAILBOX_INDEX,
            MailboxElasticsearchConstants.MESSAGE_TYPE,
            MailboxMappingFactory.getMappingContent()
        );

        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        ElasticSearchListeningMessageSearchIndex searchIndex = new ElasticSearchListeningMessageSearchIndex(
            factory,
            new ElasticSearchIndexer(client, new DeleteByQueryPerformer(client, Executors.newSingleThreadExecutor(), MailboxElasticsearchConstants.MAILBOX_INDEX, MailboxElasticsearchConstants.MESSAGE_TYPE), MailboxElasticsearchConstants.MAILBOX_INDEX, MailboxElasticsearchConstants.MESSAGE_TYPE),
            new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), new InMemoryId.Factory(), messageIdFactory),
            new MessageToElasticSearchJson(new DefaultTextExtractor(), ZoneId.systemDefault(), IndexAttachments.YES));

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        MessageParser messageParser = new MessageParser();

        mailboxManager = new StoreMailboxManager(factory, authenticator, authorizator, aclResolver, groupMembershipResolver, messageParser,
            messageIdFactory, MailboxConstants.DEFAULT_LIMIT_ANNOTATIONS_ON_MAILBOX, MailboxConstants.DEFAULT_LIMIT_ANNOTATION_SIZE);
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
    public void createMailbox(MailboxPath mailboxPath) throws Exception{
        new MailboxCreationDelegate(mailboxManager).createMailbox(mailboxPath);
    }

    @Override
    public boolean supports(Feature... features) {
        return SUPPORTED_FEATURES.supports(features);
    }

    @Override
    public void setQuotaLimits(long maxMessageQuota, long maxStorageQuota) throws Exception {
        throw new NotImplementedException();
    }

    @Override
    public void grantRights(MailboxPath mailboxPath, String userName, MailboxACL.Rfc4314Rights rights) throws Exception {
        mailboxManager.setRights(mailboxPath,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(userName)
                .rights(rights)
                .asAddition()),
            mailboxManager.createSystemSession(userName));
    }
}