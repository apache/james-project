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

package org.apache.james.mailbox.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

class OpenSearchIntegrationTest extends AbstractMessageSearchIndexTest {

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
            .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await();
    static final int SEARCH_SIZE = 1;
    private final QueryConverter queryConverter = new QueryConverter(new CriterionConverter());

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(DockerOpenSearchExtension.CleanupStrategy.NONE);

    static TikaTextExtractor textExtractor;
    static ReactorOpenSearchClient client;
    private ReadAliasName readAliasName;
    private WriteAliasName writeAliasName;
    private IndexName indexName;

    @BeforeAll
    static void setUpAll() throws Exception {
        client = openSearch.getDockerOpenSearch().clientProvider().get();
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
    }

    @AfterAll
    static void tearDown() throws IOException {
        client.close();
    }

    @Override
    protected void awaitMessageCount(List<MailboxId> mailboxIds, SearchQuery query, long messageCount) {
        awaitForOpenSearch(queryConverter.from(mailboxIds, query), messageCount);
    }

    protected OpenSearchMailboxConfiguration openSearchMailboxConfiguration() {
        return OpenSearchMailboxConfiguration.builder()
            .optimiseMoves(false)
            .build();
    }

    @Override
    protected void initializeMailboxManager() {
        messageIdFactory = new InMemoryMessageId.Factory();

        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

        readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        indexName = new IndexName(UUID.randomUUID().toString());
        MailboxIndexCreationUtil.prepareClient(
            client, readAliasName, writeAliasName, indexName,
            openSearch.getDockerOpenSearch().configuration());

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new OpenSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                ImmutableSet.of(),
                new OpenSearchIndexer(client,
                    writeAliasName),
                new OpenSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                    readAliasName, routingKeyFactory),
                new MessageToOpenSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory,
                openSearchMailboxConfiguration(), new RecordingMetricFactory()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
        eventBus = resources.getEventBus();
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
    void theDocumentShouldBeReindexWithNewMailboxWhenMoveMessages() throws Exception {
        // Given mailboxA, mailboxB. Add message in mailboxA
        MailboxPath mailboxA = MailboxPath.forUser(USERNAME, "mailboxA");
        MailboxPath mailboxB = MailboxPath.forUser(USERNAME, "mailboxB");
        MailboxId mailboxAId = storeMailboxManager.createMailbox(mailboxA, session).get();
        MailboxId mailboxBId = storeMailboxManager.createMailbox(mailboxB, session).get();

        ComposedMessageId composedMessageId = storeMailboxManager.getMailbox(mailboxAId, session)
            .appendMessage(MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setTo("benwa@linagora.com")
                        .setBody(Strings.repeat("append to inbox A", 5000), StandardCharsets.UTF_8)),
                session).getId();

        awaitUntilAsserted(mailboxAId, 1);

        // When moving the message from mailboxA to mailboxB
        storeMailboxManager.moveMessages(MessageRange.from(composedMessageId.getUid()), mailboxAId, mailboxBId, session);

        // Then the message is not anymore when searching with mailboxA, but is in mailboxB
        awaitUntilAsserted(mailboxAId, 0);

        // verify the messageDocumentWasUpdated
        MessageUid bMessageUid = Flux.from(storeMailboxManager.getMailbox(mailboxBId, session).search(SearchQuery.matchAll(), session))
            .next().block();

        ObjectNode updatedDocument = client.get(
                 new GetRequest.Builder()
                    .index(indexName.getValue())
                    .id(mailboxBId.serialize() + ":" + bMessageUid.asLong())
                    .routing(mailboxBId.serialize())
                    .build())
            .filter(GetResponse::found)
            .map(GetResponse::source)
            .block();

        assertThat(updatedDocument).isNotNull();
        assertSoftly(softly -> {
            softly.assertThat(updatedDocument.get("mailboxId").asText()).isEqualTo(mailboxBId.serialize());
            softly.assertThat(updatedDocument.get("uid").asLong()).isEqualTo(bMessageUid.asLong());
        });
    }

    @Test
    void theMessageShouldBeIndexedWhenMoveMessagesButIndexedDocumentNotFound() throws Exception {
        // Given mailboxA, mailboxB. Add message in mailboxA
        MailboxPath mailboxA = MailboxPath.forUser(USERNAME, "mailboxA");
        MailboxPath mailboxB = MailboxPath.forUser(USERNAME, "mailboxB");
        MailboxId mailboxAId = storeMailboxManager.createMailbox(mailboxA, session).get();
        MailboxId mailboxBId = storeMailboxManager.createMailbox(mailboxB, session).get();

        ComposedMessageId composedMessageId = storeMailboxManager.getMailbox(mailboxAId, session)
            .appendMessage(MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setTo("benwa@linagora.com")
                        .setBody(Strings.repeat("append to inbox A", 5000), StandardCharsets.UTF_8)),
                session).getId();

        awaitUntilAsserted(mailboxAId, 1);

        // Try to delete the document manually to simulate a not found document.
        client.deleteByQuery(new DeleteByQueryRequest.Builder()
                .index(indexName.getValue())
                .query(new MatchAllQuery.Builder().build()._toQuery())
                .build())
            .block();
        awaitUntilAsserted(mailboxAId, 0);

        // When moving the message from mailboxA to mailboxB
        storeMailboxManager.moveMessages(MessageRange.from(composedMessageId.getUid()), mailboxAId, mailboxBId, session);

        // Then the message should be indexed in mailboxB
        awaitUntilAsserted(mailboxBId, 1);
    }

    @Test
    void termsBetweenOpenSearchAndLuceneLimitDueTuNonAsciiCharsShouldBeTruncated() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, INBOX);
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody(Strings.repeat("0à2345678é", 3200), StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 14);

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

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(indexName.getValue())
                        .query(QueryBuilders.matchAll().build()._toQuery())
                        .build())
                .block()
                .hits().total().value()).isEqualTo(14));

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

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 14);

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

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 14);

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

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 15);

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

        openSearch.awaitForOpenSearch();

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

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 15);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "bob@other.tld")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Test
    void addressMatchesShouldMatchDomainPart() throws Exception {
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

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 15);
        Thread.sleep(500);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "other")), session)).toStream())
            .containsOnly(messageId2.getUid());
    }

    @Disabled("MAILBOX-403 Relaxed the matching constraints for email addresses in text bodies to reduce OpenSearch disk space usage")
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

        openSearch.awaitForOpenSearch();

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

        openSearch.awaitForOpenSearch();

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

        openSearch.awaitForOpenSearch();

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.address(SearchQuery.AddressType.To, "domain-test.tld")), session)).toStream())
            .containsOnly(messageId1.getUid());
    }
    
    @Test
    void shouldSortOnBaseSubject() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USERNAME, "def");
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        storeMailboxManager.createMailbox(mailboxPath, session);
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        ComposedMessageId messageId1 = messageManager.appendMessage(messageWithSubject("abc"), session).getId();
        ComposedMessageId messageId2 = messageManager.appendMessage(messageWithSubject("Re: abd"), session).getId();
        ComposedMessageId messageId3 = messageManager.appendMessage(messageWithSubject("Fwd: abe"), session).getId();
        ComposedMessageId messageId4 = messageManager.appendMessage(messageWithSubject("bbc"), session).getId();
        ComposedMessageId messageId5 = messageManager.appendMessage(messageWithSubject("bBc"), session).getId();
        ComposedMessageId messageId6 = messageManager.appendMessage(messageWithSubject("def"), session).getId();
        ComposedMessageId messageId7 = messageManager.appendMessage(messageWithSubject("ABC"), session).getId();

        openSearch.awaitForOpenSearch();

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 20);

        assertThat(Flux.from(
            messageManager.search(SearchQuery.allSortedWith(new SearchQuery.Sort(SearchQuery.Sort.SortClause.BaseSubject)), session)).toStream())
            .containsExactly(messageId1.getUid(),
                messageId7.getUid(),
                messageId2.getUid(),
                messageId3.getUid(),
                messageId4.getUid(),
                messageId5.getUid(),
                messageId6.getUid());
    }

    private static MessageManager.AppendCommand messageWithSubject(String subject) throws IOException {
        return MessageManager.AppendCommand.builder().build(
            Message.Builder
                .of()
                .setBody("testmail", StandardCharsets.UTF_8)
                .addField(new RawField("Subject", subject)));
    }

    private void awaitForOpenSearch(Query query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
                .untilAsserted(() -> assertThat(client.search(
                        new SearchRequest.Builder()
                            .index(indexName.getValue())
                            .query(query)
                            .build())
                        .block()
                        .hits().total().value()).isEqualTo(totalHits));
    }

    private void awaitUntilAsserted(MailboxId mailboxId, long expectedCountResult) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(messageSearchIndex.search(session, List.of(mailboxId), SearchQuery.matchAll(), 100L).toStream().count())
                .isEqualTo(expectedCountResult));
    }
}