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
package org.apache.james.mailbox.elasticsearch.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.backends.es.DockerElasticSearchExtension;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.manager.ManagerTestProvisionner;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.Authorizator;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SessionProviderImpl;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.awaitility.Duration;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.google.common.collect.Lists;

class ElasticSearchListeningMessageSearchIndexTest {
    static final int SIZE = 25;
    static final int BODY_START_OCTET = 100;
    static final TestId MAILBOX_ID = TestId.of(1L);
    static final ModSeq MOD_SEQ = ModSeq.of(42L);
    static final Username USERNAME = Username.of("user");
    static final MessageUid MESSAGE_UID_1 = MessageUid.of(25);
    static final MessageUid MESSAGE_UID_2 = MessageUid.of(26);
    static final MessageUid MESSAGE_UID_3 = MessageUid.of(27);
    static final MessageId MESSAGE_ID_1 = TestMessageId.of(18L);
    static final MessageId MESSAGE_ID_2 = TestMessageId.of(19L);
    static final MessageId MESSAGE_ID_3 = TestMessageId.of(20L);

    static final SimpleMailboxMessage.Builder MESSAGE_BUILDER = SimpleMailboxMessage.builder()
        .mailboxId(MAILBOX_ID)
        .flags(new Flags())
        .bodyStartOctet(BODY_START_OCTET)
        .internalDate(new Date(1433628000000L))
        .size(SIZE)
        .content(new SharedByteArrayInputStream("message".getBytes(StandardCharsets.UTF_8)))
        .propertyBuilder(new PropertyBuilder())
        .modseq(MOD_SEQ);

    static final SimpleMailboxMessage MESSAGE_1 = MESSAGE_BUILDER.messageId(MESSAGE_ID_1)
        .uid(MESSAGE_UID_1)
        .build();

    static final SimpleMailboxMessage MESSAGE_2 = MESSAGE_BUILDER.messageId(MESSAGE_ID_2)
        .uid(MESSAGE_UID_2)
        .build();

    static final MessageAttachment MESSAGE_ATTACHMENT = MessageAttachment.builder()
        .attachment(Attachment.builder()
            .bytes("".getBytes(StandardCharsets.UTF_8))
            .type("type")
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
        public ParsedContent extractContent(InputStream inputStream, String contentType) {
            throw new RuntimeException();
        }
    }

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
    void setup() throws MailboxException, IOException {
        mapperFactory = new InMemoryMailboxSessionMapperFactory();

        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new DefaultTextExtractor(),
            ZoneId.of("UTC"),
            IndexAttachments.YES);

        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        RestHighLevelClient client = MailboxIndexCreationUtil.prepareDefaultClient(
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

        mailbox = mapperFactory.getMailboxMapper(session).create(MailboxPath.forUser(USERNAME, DefaultMailboxes.INBOX), UidValidity.random());
    }

    @Test
    void deserializeElasticSearchListeningMessageSearchIndexGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex$ElasticSearchListeningMessageSearchIndexGroup"))
            .isEqualTo(new ElasticSearchListeningMessageSearchIndex.ElasticSearchListeningMessageSearchIndexGroup());
    }
    
    @Test
    void addShouldIndexMessageWithoutAttachment() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_1.getUid());
    }


    @Test
    void addShouldIndexMessageWithAttachment() throws Exception {
        testee.add(session, mailbox, MESSAGE_WITH_ATTACHMENT);
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_WITH_ATTACHMENT.getUid());
    }

    @Test
    void addShouldBeIndempotent() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        testee.add(session, mailbox, MESSAGE_1);

        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void addShouldIndexMultipleMessages() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        testee.add(session, mailbox, MESSAGE_2);

        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_1.getUid(), MESSAGE_2.getUid());
    }

    @Test
    void addShouldIndexEmailBodyWhenNotIndexableAttachment() throws Exception {
        MessageToElasticSearchJson messageToElasticSearchJson = new MessageToElasticSearchJson(
            new FailingTextExtractor(),
            ZoneId.of("Europe/Paris"),
            IndexAttachments.YES);

        testee = new ElasticSearchListeningMessageSearchIndex(mapperFactory, elasticSearchIndexer, elasticSearchSearcher,
            messageToElasticSearchJson, sessionProvider, new MailboxIdRoutingKeyFactory());

        testee.add(session, mailbox, MESSAGE_WITH_ATTACHMENT);
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_WITH_ATTACHMENT.getUid());
    }

    @Test
    void addShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        elasticSearch.getDockerElasticSearch().pause();
        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS()); // Docker pause is asynchronous and we found no way to poll for it

        assertThatThrownBy(() -> testee.add(session, mailbox, MESSAGE_1))
            .isInstanceOf(IOException.class);

        elasticSearch.getDockerElasticSearch().unpause();
    }

    @Test
    void deleteShouldRemoveIndex() throws IOException {
        testee.add(session, mailbox, MESSAGE_1);
        elasticSearch.awaitForElasticSearch();

        testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .isEmpty();
    }

    @Test
    void deleteShouldOnlyRemoveIndexesPassedAsArguments() throws IOException {
        testee.add(session, mailbox, MESSAGE_1);
        testee.add(session, mailbox, MESSAGE_2);

        elasticSearch.awaitForElasticSearch();

        testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_2.getUid());
    }

    @Test
    void deleteShouldRemoveMultipleIndexes() throws IOException {
        testee.add(session, mailbox, MESSAGE_1);
        testee.add(session, mailbox, MESSAGE_2);

        elasticSearch.awaitForElasticSearch();

        testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1, MESSAGE_UID_2));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .isEmpty();
    }

    @Test
    void deleteShouldBeIdempotent() throws IOException {
        testee.add(session, mailbox, MESSAGE_1);
        elasticSearch.awaitForElasticSearch();

        testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1));
        testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .isEmpty();
    }

    @Test
    void deleteShouldNotThrowOnUnknownMessageUid() {
        assertThatCode(() -> testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1)))
            .doesNotThrowAnyException();
    }

    @Test
    void deleteShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        elasticSearch.getDockerElasticSearch().pause();
        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS()); // Docker pause is asynchronous and we found no way to poll for it

        assertThatThrownBy(() -> testee.delete(session, mailbox, Lists.newArrayList(MESSAGE_UID_1)))
            .isInstanceOf(IOException.class);

        elasticSearch.getDockerElasticSearch().unpause();
    }

    @Test
    void updateShouldUpdateIndex() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        elasticSearch.awaitForElasticSearch();

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox, Lists.newArrayList(updatedFlags));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void updateShouldNotUpdateNorThrowOnUnknownMessageUid() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        elasticSearch.awaitForElasticSearch();

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_2)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox, Lists.newArrayList(updatedFlags));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query))
            .isEmpty();
    }

    @Test
    void updateShouldBeIdempotent() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        elasticSearch.awaitForElasticSearch();

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        testee.update(session, mailbox, Lists.newArrayList(updatedFlags));
        testee.update(session, mailbox, Lists.newArrayList(updatedFlags));
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.flagIsSet(Flags.Flag.ANSWERED));
        assertThat(testee.search(session, mailbox, query))
            .containsExactly(MESSAGE_1.getUid());
    }

    @Test
    void updateShouldPropagateExceptionWhenExceptionOccurs() throws Exception {
        elasticSearch.getDockerElasticSearch().pause();
        Thread.sleep(Duration.FIVE_SECONDS.getValueInMS()); // Docker pause is asynchronous and we found no way to poll for it

        Flags newFlags = new Flags(Flags.Flag.ANSWERED);
        UpdatedFlags updatedFlags = UpdatedFlags.builder()
            .uid(MESSAGE_UID_1)
            .modSeq(MOD_SEQ)
            .oldFlags(new Flags())
            .newFlags(newFlags)
            .build();

        assertThatThrownBy(() -> testee.update(session, mailbox, Lists.newArrayList(updatedFlags)))
            .isInstanceOf(IOException.class);

        elasticSearch.getDockerElasticSearch().unpause();
    }

    @Test
    void deleteAllShouldRemoveAllIndexes() throws Exception {
        testee.add(session, mailbox, MESSAGE_1);
        testee.add(session, mailbox, MESSAGE_2);

        elasticSearch.awaitForElasticSearch();

        testee.deleteAll(session, mailbox.getMailboxId());
        elasticSearch.awaitForElasticSearch();

        SearchQuery query = new SearchQuery(SearchQuery.all());
        assertThat(testee.search(session, mailbox, query))
            .isEmpty();
    }

    @Test
    void deleteAllShouldNotThrowWhenEmptyIndex() {
        assertThatCode(() -> testee.deleteAll(session, mailbox.getMailboxId()))
            .doesNotThrowAnyException();
    }

}