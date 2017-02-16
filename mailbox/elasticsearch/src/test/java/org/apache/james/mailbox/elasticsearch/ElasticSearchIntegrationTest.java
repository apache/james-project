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

package org.apache.james.mailbox.elasticsearch;

import java.time.ZoneId;
import java.util.concurrent.Executors;

import org.apache.james.backends.es.DeleteByQueryPerformer;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.InMemoryMessageIdManager;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.elasticsearch.client.Client;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class ElasticSearchIntegrationTest extends AbstractMessageSearchIndexTest {

    private static final int BATCH_SIZE = 1;
    private static final int SEARCH_SIZE = 1;

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch= new EmbeddedElasticSearch(temporaryFolder, MailboxElasticsearchConstants.MAILBOX_INDEX);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @Override
    protected void await() {
        embeddedElasticSearch.awaitForElasticSearch();
    }

    @Override
    protected void initializeMailboxManager() throws Exception {
        Client client = NodeMappingFactory.applyMapping(
            IndexCreationFactory.createIndex(
                new TestingClientProvider(embeddedElasticSearch.getNode()).get(),
                MailboxElasticsearchConstants.MAILBOX_INDEX),
            MailboxElasticsearchConstants.MAILBOX_INDEX,
            MailboxElasticsearchConstants.MESSAGE_TYPE,
            MailboxMappingFactory.getMappingContent());

        MailboxSessionMapperFactory mapperFactory = new InMemoryMailboxSessionMapperFactory();
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        messageSearchIndex = new ElasticSearchListeningMessageSearchIndex(mapperFactory,
            new ElasticSearchIndexer(client,
                new DeleteByQueryPerformer(client,
                    Executors.newSingleThreadExecutor(),
                    BATCH_SIZE,
                    MailboxElasticsearchConstants.MAILBOX_INDEX,
                    MailboxElasticsearchConstants.MESSAGE_TYPE),
                MailboxElasticsearchConstants.MAILBOX_INDEX,
                MailboxElasticsearchConstants.MESSAGE_TYPE),
            new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE, new InMemoryId.Factory(), messageIdFactory),
            new MessageToElasticSearchJson(new DefaultTextExtractor(), ZoneId.of("Europe/Paris"), IndexAttachments.YES));
        storeMailboxManager = new InMemoryMailboxManager(
            mapperFactory,
            new FakeAuthenticator(),
            FakeAuthorizator.defaultReject(),
            new JVMMailboxPathLocker(),
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver(),
            new MessageParser(),
            messageIdFactory);
        messageIdManager = new InMemoryMessageIdManager(storeMailboxManager);
        storeMailboxManager.setMessageSearchIndex(messageSearchIndex);
        storeMailboxManager.init();
    }
}