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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import org.apache.james.backends.es.DockerElasticSearchExtension;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

class ElasticSearchIntegrationTest extends AbstractMessageSearchIndexTest {

    static final int BATCH_SIZE = 1;
    static final int SEARCH_SIZE = 1;

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    TikaTextExtractor textExtractor;
    ReactorElasticSearchClient client;

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Override
    protected void await() {
        elasticSearch.awaitForElasticSearch();
    }

    @Override
    protected void initializeMailboxManager() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));

        client = MailboxIndexCreationUtil.prepareDefaultClient(
            elasticSearch.getDockerElasticSearch().clientProvider().get(),
            elasticSearch.getDockerElasticSearch().configuration());

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new ElasticSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                new ElasticSearchIndexer(client,
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS,
                    BATCH_SIZE),
                new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                    new InMemoryId.Factory(), messageIdFactory,
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS, routingKeyFactory),
                new MessageToElasticSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
    }

    @Test
    void termsBetweenElasticSearchAndLuceneLimitDueTuNonAsciiCharsShouldBeTruncated() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0à2345678é", 3200), StandardCharsets.UTF_8)),
            session);

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void tooLongTermsShouldNotMakeIndexingFail() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0123456789", 3300), StandardCharsets.UTF_8)),
            session);

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void fieldsExceedingLuceneLimitShouldNotBeIgnored() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0123456789 ", 5000), StandardCharsets.UTF_8)),
            session);

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains("0123456789")), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void fieldsWithTooLongTermShouldStillBeIndexed() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0123456789 ", 5000) + " matchMe", StandardCharsets.UTF_8)),
            session);

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains("matchMe")), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void reasonableLongTermShouldNotBeIgnored() throws Exception {
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

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.bodyContains(reasonableLongTerm)), session))
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void headerSearchShouldIncludeMessageWhenDifferentTypesOnAnIndexedField() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId customDateHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoader.getSystemResourceAsStream("eml/mailCustomDateHeader.eml")),
            session);

        elasticSearch.awaitForElasticSearch();

        ComposedMessageId customStringHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoader.getSystemResourceAsStream("eml/mailCustomStringHeader.eml")),
            session);

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.headerExists("Custom-header")), session))
            .containsExactly(customDateHeaderMessageId.getUid(), customStringHeaderMessageId.getUid());
    }

    @Test
    void messageShouldStillBeIndexedEvenAfterOneFieldFailsIndexation() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoader.getSystemResourceAsStream("eml/mailCustomDateHeader.eml")),
            session);

        elasticSearch.awaitForElasticSearch();

        ComposedMessageId customStringHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoader.getSystemResourceAsStream("eml/mailCustomStringHeader.eml")),
            session);

        elasticSearch.awaitForElasticSearch();

        assertThat(messageManager.search(new SearchQuery(SearchQuery.all()), session))
            .contains(customStringHeaderMessageId.getUid());
    }
}