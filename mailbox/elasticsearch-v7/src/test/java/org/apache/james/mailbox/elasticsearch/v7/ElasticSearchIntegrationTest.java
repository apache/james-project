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

package org.apache.james.mailbox.elasticsearch.v7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

import org.apache.james.backends.es.v7.DockerElasticSearchExtension;
import org.apache.james.backends.es.v7.ElasticSearchIndexer;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.elasticsearch.v7.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.v7.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.v7.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.v7.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.v7.search.ElasticSearchSearcher;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.search.AbstractMessageSearchIndexTest;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.util.ClassLoaderUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

class ElasticSearchIntegrationTest extends AbstractMessageSearchIndexTest {

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
            .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await();
    static final int SEARCH_SIZE = 1;
    private final QueryConverter queryConverter = new QueryConverter(new CriterionConverter());

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
    protected void awaitMessageCount(List<MailboxId> mailboxIds, SearchQuery query, long messageCount) {
        awaitForElasticSearch(queryConverter.from(mailboxIds, query), messageCount);
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
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
                new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                    new InMemoryId.Factory(), new InMemoryMessageId.Factory(),
                    MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS, routingKeyFactory),
                new MessageToElasticSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
        eventBus = resources.getEventBus();
        messageIdFactory = new InMemoryMessageId.Factory();
    }

    @Override
    protected MessageId initNewBasedMessageId() {
        return InMemoryMessageId.of(100);
    }

    @Override
    protected MessageId initOtherBasedMessageId() {
        return InMemoryMessageId.of(1000);
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
            session).getId();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session)).toStream())
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
            session).getId();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, recipient)), session)).toStream())
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
            session).getId();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("0123456789")), session)).toStream())
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
            session).getId();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("matchMe")), session)).toStream())
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
            session).getId();

        awaitMessageCount(ImmutableList.of(), SearchQuery.matchAll(), 14);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains(reasonableLongTerm)), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void headerSearchShouldIncludeMessageWhenDifferentTypesOnAnIndexedField() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId customDateHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomDateHeader.eml")),
            session).getId();

        ComposedMessageId customStringHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomStringHeader.eml")),
            session).getId();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.headerExists("Custom-header")), session)).toStream())
            .containsExactly(customDateHeaderMessageId.getUid(), customStringHeaderMessageId.getUid());
    }

    @Test
    void messageShouldStillBeIndexedEvenAfterOneFieldFailsIndexation() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomDateHeader.eml")),
            session);

        ComposedMessageId customStringHeaderMessageId = messageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mailCustomStringHeader.eml")),
            session).getId();

        elasticSearch.awaitForElasticSearch();

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.all()), session)).toStream())
            .contains(customStringHeaderMessageId.getUid());
    }

    @Test
    void addressMatchesShouldBeExact() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "bob@other.tld")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Disabled("MAILBOX-403 Relaxed the matching constraints for email addresses in text bodies to reduce ElasticSearch disk space usage")
    @Test
    public void textShouldNotMatchOtherAddressesOfTheSameDomain() {

    }

    @Disabled("MAILBOX-401 '-' causes address matching to fail")
    @Test
    void localPartShouldBeMatchedWhenHyphen() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice-test@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        elasticSearch.awaitForElasticSearch();

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice-test")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Disabled("MAILBOX-401 '-' causes address matching to fail")
    @Test
    void addressShouldBeMatchedWhenHyphen() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice-test@domain.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        elasticSearch.awaitForElasticSearch();

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "alice-test@domain.tld")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    @Disabled("MAILBOX-401 '-' causes address matching to fail")
    @Test
    void domainPartShouldBeMatchedWhenHyphen() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        Message.Builder messageBuilder = Message.Builder
            .of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);

        ComposedMessageId messageId1 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "alice@domain-test.tld"))
                    .build()),
            session).getId();

        ComposedMessageId messageId2 = messageManager.appendMessage(
            MessageManager.AppendCommand.builder().build(
                messageBuilder
                    .addField(new RawField("To", "bob@other.tld"))
                    .build()),
            session).getId();

        elasticSearch.awaitForElasticSearch();

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "domain-test.tld")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }

    private void awaitForElasticSearch(QueryBuilder query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(client.search(
                        new SearchRequest(MailboxElasticSearchConstants.DEFAULT_MAILBOX_INDEX.getValue())
                                .source(new SearchSourceBuilder().query(query)),
                        RequestOptions.DEFAULT)
                        .block()
                        .getHits().getTotalHits().value).isEqualTo(totalHits));
    }
}