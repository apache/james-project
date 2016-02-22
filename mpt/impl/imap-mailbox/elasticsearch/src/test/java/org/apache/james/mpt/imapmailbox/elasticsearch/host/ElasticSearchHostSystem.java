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

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.elasticsearch.ClientProvider;
import org.apache.james.mailbox.elasticsearch.ElasticSearchIndexer;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.apache.james.mailbox.elasticsearch.IndexCreationFactory;
import org.apache.james.mailbox.elasticsearch.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.elasticsearch.utils.TestingClientProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MockAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mpt.api.ImapFeatures;
import org.apache.james.mpt.api.ImapFeatures.Feature;
import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.MailboxCreationDelegate;

import com.google.common.base.Throwables;
import com.google.inject.Inject;

public class ElasticSearchHostSystem extends JamesImapHostSystem {

    private static final ImapFeatures SUPPORTED_FEATURES = ImapFeatures.of(Feature.NAMESPACE_SUPPORT);
    
    private EmbeddedElasticSearch embeddedElasticSearch;
    private StoreMailboxManager<InMemoryId> mailboxManager;
    private MockAuthenticator userManager;

    @Inject
    public ElasticSearchHostSystem() throws Throwable {
        this.embeddedElasticSearch = new EmbeddedElasticSearch();
        embeddedElasticSearch.before();
        initFields();
    }

    public EmbeddedElasticSearch getEmbeddedElasticSearch() {
        return embeddedElasticSearch;
    }

    public boolean addUser(String user, String password) throws Exception {
        userManager.addUser(user, password);
        return true;
    }

    @Override
    protected void resetData() throws Exception {
        embeddedElasticSearch.after();
        this.embeddedElasticSearch = new EmbeddedElasticSearch();
        embeddedElasticSearch.before();
        initFields();
    }

    private void initFields() {
        ClientProvider clientProvider = NodeMappingFactory.applyMapping(
            IndexCreationFactory.createIndex(new TestingClientProvider(embeddedElasticSearch.getNode()))
        );

        userManager = new MockAuthenticator();
        InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();

        ElasticSearchListeningMessageSearchIndex<InMemoryId> searchIndex = new ElasticSearchListeningMessageSearchIndex<>(
            factory,
            new ElasticSearchIndexer(clientProvider),
            new ElasticSearchSearcher<>(clientProvider, new QueryConverter(new CriterionConverter())),
            new MessageToElasticSearchJson(new DefaultTextExtractor()));

        MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();

        mailboxManager = new StoreMailboxManager<>(factory, userManager, aclResolver, groupMembershipResolver);
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
                        new DefaultQuotaRootResolver(factory));
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
    
}
