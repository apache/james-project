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

package org.apache.james.mailbox.opensearch.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.WriteAliasName;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.MailboxOpenSearchConstants;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.mime4j.dom.Message;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class OpenSearchSearcherTest {

    static final int SEARCH_SIZE = 1;
    private static final Username USERNAME = Username.of("user");
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(
        new DockerOpenSearchExtension.DeleteAllIndexDocumentsCleanupStrategy(new WriteAliasName("mailboxWriteAlias")));

    TikaTextExtractor textExtractor;
    ReactorOpenSearchClient client;
    private InMemoryMailboxManager storeMailboxManager;

    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(),
            new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));

        client = MailboxIndexCreationUtil.prepareDefaultClient(
            openSearch.getDockerOpenSearch().clientProvider().get(),
            openSearch.getDockerOpenSearch().configuration());

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();
        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

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
                    MailboxOpenSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS),
                new OpenSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                    MailboxOpenSearchConstants.DEFAULT_MAILBOX_READ_ALIAS, routingKeyFactory),
                new MessageToOpenSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void searchingInALargeNumberOfMailboxesShouldReturnAllMailboxesMessagesUid() throws Exception {
        MailboxSession session = MailboxSessionUtil.create(USERNAME);
        int numberOfMailboxes = 700;
        List<MailboxPath> mailboxPaths = IntStream
            .range(0, numberOfMailboxes)
            .mapToObj(index -> MailboxPath.forUser(USERNAME, "mailbox" + index))
            .collect(ImmutableList.toImmutableList());

        List<MailboxId> mailboxIds = mailboxPaths.stream()
            .map(Throwing.<MailboxPath, MailboxId>function(mailboxPath -> storeMailboxManager.createMailbox(mailboxPath, session).get()).sneakyThrow())
            .collect(ImmutableList.toImmutableList());

        List<ComposedMessageId> composedMessageIds = mailboxPaths.stream()
            .map(Throwing.<MailboxPath, ComposedMessageId>function(mailboxPath -> addMessage(session, mailboxPath)).sneakyThrow())
            .collect(ImmutableList.toImmutableList());

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), composedMessageIds.size());

        MultimailboxesSearchQuery multimailboxesSearchQuery = MultimailboxesSearchQuery
            .from(SearchQuery.of(SearchQuery.all()))
            .inMailboxes(mailboxIds)
            .build();
        List<MessageId> expectedMessageIds = composedMessageIds
            .stream()
            .map(ComposedMessageId::getMessageId)
            .collect(ImmutableList.toImmutableList());
        assertThat(storeMailboxManager.search(multimailboxesSearchQuery, session, numberOfMailboxes + 1)
            .collectList().block())
            .containsExactlyInAnyOrderElementsOf(expectedMessageIds);
    }

    private ComposedMessageId addMessage(MailboxSession session, MailboxPath mailboxPath) throws Exception {
        MessageManager messageManager = storeMailboxManager.getMailbox(mailboxPath, session);

        String recipient = "user@example.com";
        return messageManager.appendMessage(MessageManager.AppendCommand.from(
            Message.Builder.of()
                .setTo(recipient)
                .setBody("Hello", StandardCharsets.UTF_8)),
            session)
            .getId();
    }

    private void awaitForOpenSearch(Query query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(MailboxOpenSearchConstants.DEFAULT_MAILBOX_INDEX.getValue())
                        .query(query)
                        .build())
                .block()
                .hits().total().value()).isEqualTo(totalHits));
    }
}