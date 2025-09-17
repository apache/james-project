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

import static org.apache.james.mailbox.opensearch.OpenSearchIntegrationTest.SEARCH_SIZE;
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
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.DefaultCriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

class OpenSearchOptimizeMoveAndFuzzySearchIntegrationTest  {
    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension();

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final Username USERNAME = Username.of("benwa");
    static ReactorOpenSearchClient client;

    private ReadAliasName readAliasName;
    private WriteAliasName writeAliasName;
    private IndexName indexName;
    private StoreMailboxManager storeMailboxManager;
    private MessageId.Factory messageIdFactory;
    private MailboxPath inboxPath;

    @BeforeAll
    static void setUpAll() {
        client = openSearch.getDockerOpenSearch().clientProvider().get();
    }

    @BeforeEach
    public void setUp() throws Exception {
        initializeMailboxManager();
        inboxPath = MailboxPath.inbox(USERNAME);
        storeMailboxManager.createMailbox(inboxPath, storeMailboxManager.createSystemSession(USERNAME));
    }

    @AfterAll
    static void tearDown() throws IOException {
        client.close();
    }

    private void initializeMailboxManager() {
        messageIdFactory = new InMemoryMessageId.Factory();

        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

        readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        indexName = new IndexName(UUID.randomUUID().toString());
        MailboxIndexCreationUtil.prepareClient(
            client, readAliasName, writeAliasName, indexName,
            openSearch.getDockerOpenSearch().configuration(),
            new DefaultMailboxMappingFactory());
        QueryConverter queryConverter = new QueryConverter(new DefaultCriterionConverter(openSearchMailboxConfiguration()));

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
                new OpenSearchSearcher(client, queryConverter, SEARCH_SIZE,
                    readAliasName, routingKeyFactory),
                new MessageToOpenSearchJson(new DefaultTextExtractor(), ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory,
                openSearchMailboxConfiguration(), new RecordingMetricFactory(),
                ImmutableSet.of()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
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

    private OpenSearchMailboxConfiguration openSearchMailboxConfiguration() {
        return OpenSearchMailboxConfiguration.builder()
            .optimiseMoves(true)
            .textFuzzinessSearch(true)
            .build();
    }

    @Test
    void searchShouldBeLenientOnUserTypo() throws Exception {
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(inboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setSubject("fuzzy subject")
                    .setBody("fuzzy body", StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 1);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("fuzzi")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("fuzzi")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }

    @Test
    void searchShouldBeLenientOnAdjacentCharactersTranspositions() throws Exception {
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        MessageManager messageManager = storeMailboxManager.getMailbox(inboxPath, session);

        String recipient = "benwa@linagora.com";
        ComposedMessageId composedMessageId = messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setSubject("subject")
                    .setBody("body", StandardCharsets.UTF_8)),
            session).getId();

        awaitForOpenSearch(QueryBuilders.matchAll().build().toQuery(), 1);

        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.subject("subjetc")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
        assertThat(Flux.from(messageManager.search(SearchQuery.of(SearchQuery.bodyContains("boyd")), session)).toStream())
            .containsExactly(composedMessageId.getUid());
    }
}