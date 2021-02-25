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
package org.apache.james.mailbox.elasticsearch.v7.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.es.v7.DockerElasticSearchExtension;
import org.apache.james.backends.es.v7.ElasticSearchIndexer;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.core.Username;
import org.apache.james.events.Group;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.elasticsearch.v7.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.v7.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.v7.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.elasticsearch.v7.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.v7.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.v7.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.v7.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.v7.search.ElasticSearchSearcher;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.manager.ManagerTestProvisionner;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndexContract;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

class ElasticSearchListeningMessageSearchIndexTest {

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
            .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await();
    static final int SIZE = 25;
    static final int BODY_START_OCTET = 100;
    static final TestId MAILBOX_ID = TestId.of(1L);
    static final ModSeq MOD_SEQ = ModSeq.of(42L);
    static final Username USERNAME = Username.of("user");
    static final MessageUid MESSAGE_UID_1 = MessageUid.of(25);
    static final MessageUid MESSAGE_UID_2 = MessageUid.of(26);
    static final MessageUid MESSAGE_UID_3 = MessageUid.of(27);
    static final MessageUid MESSAGE_UID_4 = MessageUid.of(28);
    static final MessageId MESSAGE_ID_1 = TestMessageId.of(18L);
    static final MessageId MESSAGE_ID_2 = TestMessageId.of(19L);
    static final MessageId MESSAGE_ID_3 = TestMessageId.of(20L);
    static final MessageId MESSAGE_ID_4 = TestMessageId.of(21L);

    static final SimpleMailboxMessage.Builder MESSAGE_BUILDER = SimpleMailboxMessage.builder()
        .mailboxId(MAILBOX_ID)
        .flags(new Flags())
        .bodyStartOctet(BODY_START_OCTET)
        .internalDate(new Date(1433628000000L))
        .size(SIZE)
        .content(new SharedByteArrayInputStream("message".getBytes(StandardCharsets.UTF_8)))
        .properties(new PropertyBuilder())
        .modseq(MOD_SEQ);

    static final SimpleMailboxMessage MESSAGE_1 = MESSAGE_BUILDER.messageId(MESSAGE_ID_1)
        .uid(MESSAGE_UID_1)
        .build();

    static final SimpleMailboxMessage MESSAGE_2 = MESSAGE_BUILDER.messageId(MESSAGE_ID_2)
        .uid(MESSAGE_UID_2)
        .build();

    static final MessageAttachmentMetadata MESSAGE_ATTACHMENT = MessageAttachmentMetadata.builder()
        .attachment(AttachmentMetadata.builder()
            .attachmentId(AttachmentId.from("1"))
            .type("type")
            .size(523)
            .build())
        .name("name")
        .isInline(false)
        .build();

    static final SimpleMailboxMessage MESSAGE_WITH_ATTACHMENT = MESSAGE_BUILDER.messageId(MESSAGE_ID_3)
        .uid(MESSAGE_UID_3)
        .addAttachments(ImmutableList.of(MESSAGE_ATTACHMENT))
        .build();

    static class FailingTextExtractor implements TextExtractor {
        @Override
        public ParsedContent extractContent(InputStream inputStream, ContentType contentType) {
            throw new RuntimeException();
        }
    }

    ReactorElasticSearchClient client;
    ElasticSearchListeningMessageSearchIndex testee;
    MailboxSession session;
    Mailbox mailbox;
    MailboxSessionMapperFactory mapperFactory;
    ElasticSearchIndexer elasticSearchIndexer;
    ElasticSearchSearcher elasticSearchSearcher;
    SessionProviderImpl sessionProvider;

    @RegisterExtension
    DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    @BeforeEach
    void setup() throws Exception {
        mapperFactory = new InMemoryMailboxSessionMapperFactory();

        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("UTC"),
            IndexAttachments.YES);

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        client = MailboxIndexCreationUtil.prepareDefaultClient(
            elasticSearch.getDockerElasticSearch().clientProvider().get(),
            elasticSearch.getDockerElasticSearch().configuration());

        elasticSearchSearcher = new ElasticSearchSearcher(client,
            new QueryConverter(new CriterionConverter()),
            ElasticSearchSearcher.DEFAULT_SEARCH_SIZE,
            new InMemoryId.Factory(),
            messageIdFactory,
            MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS,
            new MailboxIdRoutingKeyFactory());

        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestProvisionner.USER, ManagerTestProvisionner.USER_PASS);
        Authorizator authorizator = FakeAuthorizator.defaultReject();
        sessionProvider = new SessionProviderImpl(fakeAuthenticator, authorizator);

        elasticSearchIndexer = new ElasticSearchIndexer(client, MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS);
        
        testee = new ElasticSearchListeningMessageSearchIndex(mapperFactory, elasticSearchIndexer, elasticSearchSearcher,
            messageToElasticSearchJson, sessionProvider, new MailboxIdRoutingKeyFactory());
        session = sessionProvider.createSystemSession(USERNAME);

        mailbox = mapperFactory.getMailboxMapper(session).create(MailboxPath.forUser(USERNAME, DefaultMailboxes.INBOX), UidValidity.generate()).block();
    }

    @Test
    void deserializeElasticSearchListeningMessageSearchIndexGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.elasticsearch.v7.events.ElasticSearchListeningMessageSearchIndex$ElasticSearchListeningMessageSearchIndexGroup"))
            .isEqualTo(new ElasticSearchListeningMessageSearchIndex.ElasticSearchListeningMessageSearchIndexGroup());
    }
    
    @Test
    void addShouldIndexMessageWithoutAttachment() {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void addShouldIndexMessageWithAttachment() {
        testee.add(session, mailbox, MESSAGE_WITH_ATTACHMENT).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_WITH_ATTACHMENT.getUid());
    }

    @Test
    void addShouldBeIndempotent() {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_1).block();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void addShouldIndexMultipleMessages() {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();

        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 2L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid(), MESSAGE_2.getUid());
    }

    @Test
    void addShouldIndexEmailBodyWhenNotIndexableAttachment() {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new FailingTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);

        testee = new ElasticSearchListeningMessageSearchIndex(mapperFactory, elasticSearchIndexer, elasticSearchSearcher,
            messageToElasticSearchJson, sessionProvider, new MailboxIdRoutingKeyFactory());

        testee.add(session, mailbox, MESSAGE_WITH_ATTACHMENT).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_WITH_ATTACHMENT.getUid());
    }

    @Test
    void addShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        elasticSearch.getDockerElasticSearch().pause();
        Thread.sleep(Durations.FIVE_SECONDS.toMillis()); // Docker pause is asynchronous and we found no way to poll for it

        assertThatThrownBy(() -> testee.add(session, mailbox, MESSAGE_1).block())
            .hasCauseInstanceOf(IOException.class);

        elasticSearch.getDockerElasticSearch().unpause();
    }

    @Test
    void deleteShouldRemoveIndex() {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 0L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void deleteShouldOnlyRemoveIndexesPassedAsArguments() {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 2L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_2.getUid());
    }

    @Test
    void deleteShouldRemoveMultipleIndexes() {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 2L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1, MESSAGE_UID_2)).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 0L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void deleteShouldBeIdempotent() {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 0L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void deleteShouldNotThrowOnUnknownMessageUid() {
        assertThatCode(() -> testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void deleteShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        elasticSearch.getDockerElasticSearch().pause();
        Thread.sleep(Durations.FIVE_SECONDS.toMillis()); // Docker pause is asynchronous and we found no way to poll for it

        assertThatThrownBy(() -> testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block())
            .hasCauseInstanceOf(IOException.class);

        elasticSearch.getDockerElasticSearch().unpause();
    }

    @Test
    void updateShouldUpdateIndex() {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        awaitForElasticSearch(QueryBuilders.termQuery("isAnswered", true), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void updateShouldNotUpdateNorThrowOnUnknownMessageUid() {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_2)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void updateShouldBeIdempotent() {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 1L);

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        awaitForElasticSearch(QueryBuilders.termQuery("isAnswered", true), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void updateShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        elasticSearch.getDockerElasticSearch().pause();
        Thread.sleep(Durations.FIVE_SECONDS.toMillis()); // Docker pause is asynchronous and we found no way to poll for it

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        assertThatThrownBy(() -> testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block())
            .hasCauseInstanceOf(IOException.class);

        elasticSearch.getDockerElasticSearch().unpause();
    }

    @Test
    void deleteAllShouldRemoveAllIndexes() {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 2L);

        testee.deleteAll(session, mailbox.getMailboxId()).block();
        awaitForElasticSearch(QueryBuilders.matchAllQuery(), 0L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void deleteAllShouldNotThrowWhenEmptyIndex() {
        assertThatCode(() -> testee.deleteAll(session, mailbox.getMailboxId()).block())
            .doesNotThrowAnyException();
    }

    @Nested
    class RetrieveIndexedFlags implements ListeningMessageSearchIndexContract {
        @Override
        public ListeningMessageSearchIndex testee() {
            return testee;
        }

        @Override
        public MailboxSession session() {
            return session;
        }

        @Override
        public Mailbox mailbox() {
            return mailbox;
        }

        @Test
        void retrieveIndexedFlagsShouldReturnEmptyWhenNotFound() {
            assertThat(testee.retrieveIndexedFlags(mailbox, MESSAGE_UID_4).blockOptional())
                .isEmpty();
        }
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