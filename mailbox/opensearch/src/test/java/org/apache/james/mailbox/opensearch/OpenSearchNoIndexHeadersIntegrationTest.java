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
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.UUID;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.DefaultCriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.common.collect.ImmutableSet;

class OpenSearchNoIndexHeadersIntegrationTest {
    static final int SEARCH_SIZE = 1;
    private static final Username USERNAME = Username.of("user");
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(DockerOpenSearchExtension.CleanupStrategy.NONE);

    TikaTextExtractor textExtractor;
    ReactorOpenSearchClient client;
    private InMemoryMailboxManager storeMailboxManager;
    private MessageSearchIndex messageSearchIndex;
    private IndexName indexName;
    protected MailboxSession session;
    private Mailbox mailbox;
    private MailboxPath inboxPath;


    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
        client = openSearch.getDockerOpenSearch().clientProvider().get();

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();
        ReadAliasName readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        WriteAliasName writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        indexName = new IndexName(UUID.randomUUID().toString());
        MailboxIndexCreationUtil.prepareClient(client, readAliasName, writeAliasName, indexName,
            openSearch.getDockerOpenSearch().configuration(), new DefaultMailboxMappingFactory());

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(preInstanciationStage -> new OpenSearchListeningMessageSearchIndex(
                preInstanciationStage.getMapperFactory(),
                ImmutableSet.of(),
                new OpenSearchIndexer(client, writeAliasName),
                new OpenSearchSearcher(client, new QueryConverter(new DefaultCriterionConverter()), SEARCH_SIZE, readAliasName, routingKeyFactory),
                new MessageToOpenSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.NO, IndexBody.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory,
                OpenSearchMailboxConfiguration.builder().indexHeaders(IndexHeaders.NO).build(), new RecordingMetricFactory(),
                ImmutableSet.of()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageSearchIndex = resources.getSearchIndex();

        session = storeMailboxManager.createSystemSession(USERNAME);
        inboxPath = MailboxPath.inbox(USERNAME);
        storeMailboxManager.createMailbox(inboxPath, session);

        StoreMessageManager inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        mailbox = inboxMessageManager.getMailboxEntity();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    // RFC 9051 section 6.4.4: TEXT matches the header or the body, even when every header is not indexed
    @ParameterizedTest
    @ValueSource(strings = {"Carol", "carol@from.org", "dave@to.org", "erin@cc.org", "frank@bcc.org", "hdronlyword", "<unique-id@example.org>", "unique-id@example.org"})
    void textShouldMatchTheHeadersWhenNoIndexHeaders(String text) throws Exception {
        MessageManager messageManager = storeMailboxManager.getMailbox(inboxPath, session);
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setFrom("Carol <carol@from.org>")
                    .setTo("dave@to.org")
                    .setField(DefaultFieldParser.parse("Cc: erin@cc.org"))
                    .setField(DefaultFieldParser.parse("Bcc: frank@bcc.org"))
                    .setMessageId("<unique-id@example.org>")
                    .setSubject("hdronlyword")
                    .setBody("plain body words", StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 1L);

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.mailContains(text))).collectList().block())
            .containsExactly(composedMessageId.getUid());
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
}
