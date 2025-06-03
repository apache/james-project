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

import static org.apache.james.mailbox.opensearch.search.OpenSearchSearcherTest.SEARCH_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
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
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.DefaultMailboxMappingFactory;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.DefaultCriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.searchhighligt.SearchHighLighterContract;
import org.apache.james.mailbox.searchhighligt.SearchHighlighter;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;

public class OpenSearchSearchHighlighterTest implements SearchHighLighterContract {
    private MessageSearchIndex messageSearchIndex;
    private StoreMailboxManager storeMailboxManager;
    private StoreMessageManager inboxMessageManager;
    private OpenSearchSearchHighlighter testee;

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    @RegisterExtension
    static DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension(DockerOpenSearchExtension.CleanupStrategy.NONE);
    static ReactorOpenSearchClient client;
    static TikaTextExtractor textExtractor;

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

    @BeforeEach
    public void setUp() throws Exception {
        WriteAliasName writeAliasName = new WriteAliasName(UUID.randomUUID().toString());
        ReadAliasName readAliasName = new ReadAliasName(UUID.randomUUID().toString());
        IndexName indexName = new IndexName(UUID.randomUUID().toString());
        MailboxIndexCreationUtil.prepareClient(
            client, readAliasName, writeAliasName, indexName,
            openSearch.getDockerOpenSearch().configuration(), new DefaultMailboxMappingFactory());

        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();
        OpenSearchMailboxConfiguration openSearchMailboxConfiguration = OpenSearchMailboxConfiguration.builder()
            .optimiseMoves(false)
            .textFuzzinessSearch(false)
            .build();
        final MessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        OpenSearchSearcher openSearchSearcher = new OpenSearchSearcher(client, new QueryConverter(new DefaultCriterionConverter(openSearchMailboxConfiguration)), SEARCH_SIZE,
            readAliasName, routingKeyFactory);

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
                openSearchSearcher,
                new MessageToOpenSearchJson(textExtractor, ZoneId.of("Europe/Paris"), IndexAttachments.YES, IndexHeaders.YES),
                preInstanciationStage.getSessionProvider(), routingKeyFactory, messageIdFactory,
                openSearchMailboxConfiguration, new RecordingMetricFactory(),
                ImmutableSet.of()))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        storeMailboxManager = resources.getMailboxManager();
        messageSearchIndex = resources.getSearchIndex();
        MailboxSession session = storeMailboxManager.createSystemSession(USERNAME1);
        MailboxPath inboxPath = MailboxPath.inbox(USERNAME1);
        storeMailboxManager.createMailbox(inboxPath, session);
        inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);

        testee = new OpenSearchSearchHighlighter(openSearchSearcher, storeMailboxManager, messageIdFactory);
    }

    @Override
    public SearchHighlighter testee() {
        return testee;
    }

    @Override
    public MailboxSession session(Username username) {
        return storeMailboxManager.createSystemSession(username);
    }

    @Override
    public MessageManager.AppendResult appendMessage(MessageManager.AppendCommand appendCommand, MailboxSession session) {
        return Throwing.supplier(() -> inboxMessageManager.appendMessage(appendCommand, session)).get();
    }

    @Override
    public MailboxId randomMailboxId(Username username) {
        String random = new String(new byte[8]);
        return Throwing.supplier(() -> storeMailboxManager.createMailbox(MailboxPath.forUser(USERNAME1, random), session(username)).get()).get();
    }

    @Override
    public void applyRightsCommand(MailboxId mailboxId, Username owner, Username delegated) {
        Mailbox mailbox = inboxMessageManager.getMailboxEntity();
        Throwing.runnable(() -> storeMailboxManager.applyRightsCommand(mailbox.generateAssociatedPath(),
            MailboxACL.command().forUser(delegated).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            session(owner))).run();
    }

    @Override
    public void verifyMessageWasIndexed(int indexedMessageCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(messageSearchIndex.search(session(USERNAME1), inboxMessageManager.getMailboxEntity(), SearchQuery.of()).toStream().count())
                .isEqualTo(indexedMessageCount));
    }

}
