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
package org.apache.james.mailbox.opensearch.events;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.backends.opensearch.DockerOpenSearchExtension;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.manager.ManagerTestProvisionner;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.opensearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.opensearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.opensearch.MailboxOpenSearchConstants;
import org.apache.james.mailbox.opensearch.json.MessageToOpenSearchJson;
import org.apache.james.mailbox.opensearch.query.CriterionConverter;
import org.apache.james.mailbox.opensearch.query.QueryConverter;
import org.apache.james.mailbox.opensearch.search.OpenSearchSearcher;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndexContract;
import org.apache.james.utils.UpdatableTickingClock;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;

import reactor.core.publisher.Mono;

class OpenSearchListeningMessageSearchIndexTest {

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
        .content(new ByteContent("message".getBytes(StandardCharsets.UTF_8)))
        .properties(new PropertyBuilder())
        .modseq(MOD_SEQ);

    static final SimpleMailboxMessage MESSAGE_1 = MESSAGE_BUILDER.messageId(MESSAGE_ID_1)
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
        .uid(MESSAGE_UID_1)
        .build();

    static final SimpleMailboxMessage MESSAGE_2 = MESSAGE_BUILDER.messageId(MESSAGE_ID_2)
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_2))
        .uid(MESSAGE_UID_2)
        .build();

    static final MessageAttachmentMetadata MESSAGE_ATTACHMENT = MessageAttachmentMetadata.builder()
        .attachment(AttachmentMetadata.builder()
            .messageId(MESSAGE_ID_3)
            .attachmentId(AttachmentId.from("1"))
            .type("type")
            .size(523)
            .build())
        .name("name")
        .isInline(false)
        .build();

    static final SimpleMailboxMessage MESSAGE_WITH_ATTACHMENT = MESSAGE_BUILDER.messageId(MESSAGE_ID_3)
        .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_3))
        .uid(MESSAGE_UID_3)
        .addAttachments(ImmutableList.of(MESSAGE_ATTACHMENT))
        .build();

    static class FailingTextExtractor implements TextExtractor {
        @Override
        public ParsedContent extractContent(InputStream inputStream, ContentType contentType) {
            throw new RuntimeException();
        }
    }

    ReactorOpenSearchClient client;
    OpenSearchListeningMessageSearchIndex testee;
    MailboxSession session;
    Mailbox mailbox;
    MailboxSessionMapperFactory mapperFactory;
    OpenSearchIndexer openSearchIndexer;
    OpenSearchSearcher openSearchSearcher;
    SessionProviderImpl sessionProvider;
    UpdatableTickingClock clock;

    @RegisterExtension
    DockerOpenSearchExtension openSearch = new DockerOpenSearchExtension();

    @BeforeEach
    void setup() throws Exception {
        clock = new UpdatableTickingClock(Instant.now());
        mapperFactory = new InMemoryMailboxSessionMapperFactory(clock);

        MessageToOpenSearchJson messageToOpenSearchJson = new MessageToOpenSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("UTC"),
            IndexAttachments.YES,
            IndexHeaders.YES);

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        client = MailboxIndexCreationUtil.prepareDefaultClient(
            openSearch.getDockerOpenSearch().clientProvider().get(),
            openSearch.getDockerOpenSearch().configuration());

        openSearchSearcher = new OpenSearchSearcher(client,
            new QueryConverter(new CriterionConverter()),
            OpenSearchSearcher.DEFAULT_SEARCH_SIZE,
            MailboxOpenSearchConstants.DEFAULT_MAILBOX_READ_ALIAS,
            new MailboxIdRoutingKeyFactory());

        FakeAuthenticator fakeAuthenticator = new FakeAuthenticator();
        fakeAuthenticator.addUser(ManagerTestProvisionner.USER, ManagerTestProvisionner.USER_PASS);
        Authorizator authorizator = FakeAuthorizator.defaultReject();
        sessionProvider = new SessionProviderImpl(fakeAuthenticator, authorizator);

        openSearchIndexer = new OpenSearchIndexer(client, MailboxOpenSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS);
        
        testee = new OpenSearchListeningMessageSearchIndex(mapperFactory,
            ImmutableSet.of(), openSearchIndexer, openSearchSearcher,
            messageToOpenSearchJson, sessionProvider, new MailboxIdRoutingKeyFactory(), messageIdFactory);
        session = sessionProvider.createSystemSession(USERNAME);

        mailbox = mapperFactory.getMailboxMapper(session).create(MailboxPath.forUser(USERNAME, DefaultMailboxes.INBOX), UidValidity.generate()).block();
    }

    @Test
    void deserializeOpenSearchListeningMessageSearchIndexGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex$OpenSearchListeningMessageSearchIndexGroup"))
            .isEqualTo(new OpenSearchListeningMessageSearchIndex.OpenSearchListeningMessageSearchIndexGroup());
    }
    
    @Test
    void addShouldIndexMessageWithoutAttachment() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void addShouldIndexMessageWithAttachment() throws Exception {
        testee.add(session, mailbox, MESSAGE_WITH_ATTACHMENT).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_WITH_ATTACHMENT.getUid());
    }

    @Test
    void addShouldBeIndempotent() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_1).block();

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void addShouldIndexMultipleMessages() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();

        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 2L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid(), MESSAGE_2.getUid());
    }

    @Test
    void addShouldIndexEmailBodyWhenNotIndexableAttachment() throws Exception {
        MessageToOpenSearchJson messageToOpenSearchJson = new MessageToOpenSearchJson(
            new FailingTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES,
            IndexHeaders.YES);

        testee = new OpenSearchListeningMessageSearchIndex(mapperFactory,
            ImmutableSet.of(), openSearchIndexer, openSearchSearcher,
            messageToOpenSearchJson, sessionProvider, new MailboxIdRoutingKeyFactory(), new InMemoryMessageId.Factory());

        testee.add(session, mailbox, MESSAGE_WITH_ATTACHMENT).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_WITH_ATTACHMENT.getUid());
    }

    @Test
    void addShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        openSearch.getDockerOpenSearch().pause();
        Thread.sleep(Durations.FIVE_SECONDS.toMillis()); // Docker pause is asynchronous and we found no way to poll for it

        assertThatThrownBy(() -> testee.add(session, mailbox, MESSAGE_1).block())
            .hasCauseInstanceOf(IOException.class);

        openSearch.getDockerOpenSearch().unpause();
    }

    @Test
    void addAOutdatedMessageInOutBoxShouldNotIndex() throws Exception {
        // given
        MailboxPath outboxPath = MailboxPath.forUser(USERNAME, DefaultMailboxes.OUTBOX);
        Mailbox outbox = mapperFactory.getMailboxMapper(session).create(outboxPath, UidValidity.generate()).block();
        assert outbox != null;
        Mailbox inbox = mailbox;
        SimpleMailboxMessage outDatedMessage = SimpleMailboxMessage.builder()
            .mailboxId(inbox.getMailboxId())
            .flags(new Flags())
            .bodyStartOctet(BODY_START_OCTET)
            .internalDate(new Date(1433628000000L))
            .size(SIZE)
            .content(new ByteContent("message".getBytes(StandardCharsets.UTF_8)))
            .properties(new PropertyBuilder())
            .modseq(MOD_SEQ)
            .messageId(MESSAGE_ID_1)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID_1))
            .uid(MESSAGE_UID_1)
            .build();
        mapperFactory.getMessageMapper(session).add(inbox, outDatedMessage);

        // when
        MessageMetaData outdatedMessageMetaData = new MessageMetaData(MESSAGE_UID_1,
            outDatedMessage.getModSeq(),
            outDatedMessage.metaData().getFlags(),
            SIZE,
            outDatedMessage.getInternalDate(),
            outDatedMessage.getSaveDate(),
            MESSAGE_ID_1,
            outDatedMessage.getThreadId());

        MailboxEvents.Added addedOutDatedEvent = new MailboxEvents.Added(MailboxSession.SessionId.of(42),
            BOB,
            outboxPath,
            outbox.getMailboxId(),
            ImmutableSortedMap.of(MESSAGE_UID_1, outdatedMessageMetaData),
            Event.EventId.random(),
            !IS_DELIVERY, IS_APPENDED);

        Mono.from(testee.reactiveEvent(addedOutDatedEvent)).block();

        // then
        Thread.sleep(1000);
        assertThat(testee.search(session, outbox, SearchQuery.of(SearchQuery.all())).collectList().block())
            .isEmpty();
    }

    @Test
    void deleteShouldRemoveIndex() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 0L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void deleteShouldOnlyRemoveIndexesPassedAsArguments() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 2L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_2.getUid());
    }

    @Test
    void deleteShouldRemoveMultipleIndexes() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 2L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1, MESSAGE_UID_2)).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 0L);

        SearchQuery query = SearchQuery.of(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query).toStream())
            .isEmpty();
    }

    @Test
    void deleteShouldBeIdempotent() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 0L);

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
        openSearch.getDockerOpenSearch().pause();
        Thread.sleep(Durations.FIVE_SECONDS.toMillis()); // Docker pause is asynchronous and we found no way to poll for it

        assertThatThrownBy(() -> testee.delete(session, mailbox.getMailboxId(), Lists.newArrayList(MESSAGE_UID_1)).block())
            .hasCauseInstanceOf(IOException.class);

        openSearch.getDockerOpenSearch().unpause();
    }

    @Test
    void updateShouldUpdateIndex() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        awaitForOpenSearch(QueryBuilders.term().field("isAnswered").value(FieldValue.of(true)).build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void updateShouldThrowOnUnknownMessageUid() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

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
    void updateShouldBeIdempotent() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 1L);

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        testee.update(session, mailbox.getMailboxId(), Lists.newArrayList(updatedFlags)).block();
        awaitForOpenSearch(QueryBuilders.term().field("isAnswered").value(FieldValue.of(true)).build()._toQuery(), 1L);

        SearchQuery query = SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query).toStream())
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void updateShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        openSearch.getDockerOpenSearch().pause();
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

        openSearch.getDockerOpenSearch().unpause();
    }

    @Test
    void deleteAllShouldRemoveAllIndexes() throws Exception {
        testee.add(session, mailbox, MESSAGE_1).block();
        testee.add(session, mailbox, MESSAGE_2).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 2L);

        testee.deleteAll(session, mailbox.getMailboxId()).block();
        awaitForOpenSearch(QueryBuilders.matchAll().build()._toQuery(), 0L);

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
            testee.add(session, mailbox, MESSAGE_1).block();

            assertThat(testee.retrieveIndexedFlags(mailbox, MESSAGE_UID_4).blockOptional())
                .isEmpty();
        }
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