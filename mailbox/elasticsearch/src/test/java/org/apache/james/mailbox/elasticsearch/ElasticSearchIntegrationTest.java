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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.Executors;

import javax.mail.Flags;

import org.apache.james.backends.es.DeleteByQueryPerformer;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.apache.james.mailbox.MessageManager;
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
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.event.DefaultDelegatingMailboxListener;
import org.apache.james.mailbox.store.event.MailboxEventDispatcher;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaRootResolver;
import org.apache.james.mailbox.store.quota.NoQuotaManager;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaContainer;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.elasticsearch.client.Client;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;

public class ElasticSearchIntegrationTest extends AbstractMessageSearchIndexTest {

    private static final int BATCH_SIZE = 1;
    private static final int SEARCH_SIZE = 1;
    private static final boolean IS_RECENT = true;

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch= new EmbeddedElasticSearch(temporaryFolder, MailboxElasticsearchConstants.MAILBOX_INDEX);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @ClassRule
    public static TikaContainer tika = new TikaContainer();
    private TikaTextExtractor textExtractor;

    @Override
    public void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
        super.setUp();
    }

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
            new MessageToElasticSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES));
        storeMailboxManager = new InMemoryMailboxManager(
            mapperFactory,
            new FakeAuthenticator(),
            FakeAuthorizator.defaultReject(),
            new JVMMailboxPathLocker(),
            new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver(),
            new MessageParser(),
            messageIdFactory);
        DefaultDelegatingMailboxListener delegatingListener = new DefaultDelegatingMailboxListener();
        MailboxEventDispatcher mailboxEventDispatcher = new MailboxEventDispatcher(delegatingListener);
        storeMailboxManager.setDelegatingMailboxListener(delegatingListener);

        messageIdManager = new StoreMessageIdManager(
            storeMailboxManager,
            storeMailboxManager.getMapperFactory(),
            mailboxEventDispatcher,
            messageIdFactory,
            new NoQuotaManager(),
            new DefaultQuotaRootResolver(mapperFactory));

        storeMailboxManager.setMessageSearchIndex(messageSearchIndex);
        storeMailboxManager.init();
    }

    @Test
    public void termsBetweenElasticSearchAndLuceneLimitDueTuNonAsciiCharsShouldBeTruncated() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MockMailboxSession session = new MockMailboxSession(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(new ByteArrayInputStream(("To: " + recipient + "\n" +
            "\n" +
            Strings.repeat("0à2345678é", 3200)).getBytes(Charsets.UTF_8)), new Date(), session, IS_RECENT, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void tooLongTermsShouldNotMakeIndexingFail() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MockMailboxSession session = new MockMailboxSession(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(new ByteArrayInputStream(("To: " + recipient + "\n" +
            "\n" +
            Strings.repeat("0123456789", 3300)).getBytes(Charsets.UTF_8)), new Date(), session, IS_RECENT, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void fieldsExceedingLuceneLimitShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MockMailboxSession session = new MockMailboxSession(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(new ByteArrayInputStream(("To: " + recipient + "\n" +
            "\n" +
            Strings.repeat("0123456789 ", 5000)).getBytes(Charsets.UTF_8)), new Date(), session, IS_RECENT, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains("0123456789")), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void fieldsWithTooLongTermShouldStillBeIndexed() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MockMailboxSession session = new MockMailboxSession(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(new ByteArrayInputStream(("To: " + recipient + "\n" +
            "\n" +
            Strings.repeat("0123456789", 5000) + " matchMe").getBytes(Charsets.UTF_8)), new Date(), session, IS_RECENT, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains("matchMe")), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void reasonableLongTermShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MockMailboxSession session = new MockMailboxSession(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        String reasonableLongTerm = "dichlorodiphényltrichloroéthane";
        ComposedMessageId composedMessageId = messageManager.appendMessage(new ByteArrayInputStream(("To: " + recipient + "\n" +
            "\n" +
            reasonableLongTerm).getBytes(Charsets.UTF_8)), new Date(), session, IS_RECENT, new Flags());

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains(reasonableLongTerm)), session))
            .containsExactly(composedMessageId.getUid());
    }
}