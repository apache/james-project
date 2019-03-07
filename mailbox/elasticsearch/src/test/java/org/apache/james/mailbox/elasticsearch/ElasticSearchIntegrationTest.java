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

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.EmbeddedElasticSearch;
import org.apache.james.backends.es.utils.TestingClientProvider;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.events.InVMEventBus;
import org.apache.james.mailbox.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.manager.ManagerTestResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.JVMMailboxPathLocker;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxAnnotationManager;
import org.apache.james.mailbox.store.StoreMessageIdManager;
import org.apache.james.mailbox.store.StoreRightManager;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.QuotaComponents;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaContainerSingletonRule;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.elasticsearch.client.Client;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Strings;

public class ElasticSearchIntegrationTest extends AbstractMessageSearchIndexTest {

    private static final int BATCH_SIZE = 1;
    private static final int SEARCH_SIZE = 1;

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private EmbeddedElasticSearch embeddedElasticSearch = new EmbeddedElasticSearch(temporaryFolder);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(embeddedElasticSearch);

    @ClassRule
    public static TikaContainerSingletonRule tika = TikaContainerSingletonRule.rule;
    private TikaTextExtractor textExtractor;

    @Override
    public void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new NoopMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
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
    protected void initializeMailboxManager() {
        Client client = MailboxIndexCreationUtil.prepareDefaultClient(
            new TestingClientProvider(embeddedElasticSearch.getNode()).get(),
                ElasticSearchConfiguration.DEFAULT_CONFIGURATION);

        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestResources.USER, ManagerTestResources.USER_PASS);
        fakeAuthenticator.addUser(ManagerTestResources.OTHER_USER, ManagerTestResources.OTHER_USER_PASS);
        InMemoryMailboxSessionMapperFactory mailboxSessionMapperFactory = new InMemoryMailboxSessionMapperFactory();
        InVMEventBus eventBus = new InVMEventBus(new InVmEventDelivery(new NoopMetricFactory()));
        StoreRightManager storeRightManager = new StoreRightManager(mailboxSessionMapperFactory, new UnionMailboxACLResolver(),
            new SimpleGroupMembershipResolver(), eventBus);
        StoreMailboxAnnotationManager annotationManager = new StoreMailboxAnnotationManager(mailboxSessionMapperFactory, storeRightManager);
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        SessionProvider sessionProvider = new SessionProvider(fakeAuthenticator, FakeAuthorizator.defaultReject());
        QuotaComponents quotaComponents = QuotaComponents.disabled(sessionProvider, mailboxSessionMapperFactory);

        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        ElasticSearchListeningMessageSearchIndex elasticSearchListeningMessageSearchIndex = new ElasticSearchListeningMessageSearchIndex(
            mailboxSessionMapperFactory,
            new ElasticSearchIndexer(client,
                Executors.newSingleThreadExecutor(threadFactory),
                MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS,
                MailboxElasticSearchConstants.MESSAGE_TYPE,
                BATCH_SIZE),
            new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                new InMemoryId.Factory(), messageIdFactory,
                MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS,
                MailboxElasticSearchConstants.MESSAGE_TYPE),
            new MessageToElasticSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES),
            new SessionProvider(new FakeAuthenticator(), FakeAuthorizator.defaultReject()));

        storeMailboxManager = new InMemoryMailboxManager(
            mailboxSessionMapperFactory,
            sessionProvider,
            new JVMMailboxPathLocker(),
            new MessageParser(),
            messageIdFactory,
            eventBus,
            annotationManager,
            storeRightManager,
            quotaComponents,
            elasticSearchListeningMessageSearchIndex,
            PreDeletionHooks.NO_PRE_DELETION_HOOK);

        messageIdManager = new StoreMessageIdManager(
            storeMailboxManager,
            storeMailboxManager.getMapperFactory(),
            eventBus,
            storeMailboxManager.getMessageIdFactory(),
            quotaComponents.getQuotaManager(),
            quotaComponents.getQuotaRootResolver(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK);

        eventBus.register(elasticSearchListeningMessageSearchIndex);
        this.messageSearchIndex = elasticSearchListeningMessageSearchIndex;
    }

    @Test
    public void termsBetweenElasticSearchAndLuceneLimitDueTuNonAsciiCharsShouldBeTruncated() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0à2345678é", 3200), StandardCharsets.UTF_8)),
            session);

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void tooLongTermsShouldNotMakeIndexingFail() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0123456789", 3300), StandardCharsets.UTF_8)),
            session);

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void fieldsExceedingLuceneLimitShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0123456789 ", 5000), StandardCharsets.UTF_8)),
            session);

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains("0123456789")), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void fieldsWithTooLongTermShouldStillBeIndexed() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0123456789 ", 5000) + " matchMe", StandardCharsets.UTF_8)),
            session);

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains("matchMe")), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    public void reasonableLongTermShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        String reasonableLongTerm = "dichlorodiphényltrichloroéthane";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(reasonableLongTerm, StandardCharsets.UTF_8)),
            session);

        embeddedElasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains(reasonableLongTerm)), session))
            .containsExactly(composedMessageId.getUid());
    }
}