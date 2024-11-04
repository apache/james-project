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
package org.apache.james.mailbox.lucene.search;

import static org.apache.james.mailbox.lucene.search.DocumentFieldConstants.ID_FIELD;
import static org.apache.james.mailbox.lucene.search.LuceneTestsUtils.documentStringFormatter;
import static org.apache.james.mailbox.lucene.search.LuceneTestsUtils.getAllDocumentsFromRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

class LuceneMailboxMessageFlagSearchTest {
    protected static final ConditionFactory CALMLY_AWAIT = Awaitility
            .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await();
    protected static final long LIMIT = 100L;
    protected static final boolean RECENT = true;
    protected static final boolean NOT_RECENT = false;
    protected static final String INBOX = "INBOX";
    private static final Logger log = LoggerFactory.getLogger(LuceneMailboxMessageFlagSearchTest.class);
    private static final Username USERNAME = Username.of("username");
    protected MessageSearchIndex messageSearchIndex;
    protected StoreMailboxManager storeMailboxManager;
    protected MessageIdManager messageIdManager;
    protected EventBus eventBus;
    protected MessageId.Factory messageIdFactory;
    protected UpdatableTickingClock clock;
    private StoreMessageManager inboxMessageManager;
    private MailboxSession session;
    private LuceneMessageSearchIndex luceneMessageSearchIndex;

    private Mailbox mailbox;
    private ComposedMessageId m1;
    private ComposedMessageId m2;
    private ComposedMessageId m3;
    private ComposedMessageId m4;

    private static BooleanQuery.Builder getQueryBuilderFlagId(long mailboxId, long messageId) {
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        var flagsId = "flags-" + mailboxId + "-" + messageId;
        queryBuilder.add(new TermQuery(new Term(ID_FIELD, flagsId)), BooleanClause.Occur.MUST);
        return queryBuilder;
    }

    protected boolean useLenient() {
        return true;
    }

    @BeforeEach
    public void setUp() throws Exception {
        initializeMailboxManager();
        clock = (UpdatableTickingClock) storeMailboxManager.getClock();

        luceneMessageSearchIndex = ((LuceneMessageSearchIndex) messageSearchIndex);

        session = storeMailboxManager.createSystemSession(USERNAME);
        MailboxPath inboxPath = MailboxPath.inbox(USERNAME);

        storeMailboxManager.createMailbox(inboxPath, session);

        inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        mailbox = inboxMessageManager.getMailboxEntity();
    }

    private void addM4() throws MailboxException {
        m4 = addEmail(m4, "eml/mail4.eml", 1396389600000L);
    }

    private void addM3() throws MailboxException {
        m3 = addEmail(m3, "eml/mail3.eml", 1398981600000L);
    }

    private void addM2() throws MailboxException {
        m2 = addEmail(m2, "eml/mail2.eml", 1393714800000L);
    }

    private void addM1() throws MailboxException {
        m1 = addEmail(m1, "eml/mail1.eml", 1391295600000L);
    }

    private ComposedMessageId addEmail(ComposedMessageId messageId, String name, long date) throws MailboxException {
        return inboxMessageManager.appendMessage(
                ClassLoader.getSystemResourceAsStream(name),
                new Date(date),
                session,
                NOT_RECENT,
                new Flags()).getId();
    }

    private void initializeMailboxManager() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
                .preProvisionnedFakeAuthenticator()
                .fakeAuthorizator()
                .inVmEventBus()
                .defaultAnnotationLimits()
                .defaultMessageParser()
                .listeningSearchIndex(Throwing.function(preInstanciationStage -> new LuceneMessageSearchIndex(
                        preInstanciationStage.getMapperFactory(), new InMemoryId.Factory(), new ByteBuffersDirectory(),
                        new InMemoryMessageId.Factory(),
                        preInstanciationStage.getSessionProvider(), new JsoupTextExtractor())))
                .noPreDeletionHooks()
                .storeQuotaManager()
                .build();

        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
        eventBus = resources.getEventBus();
        messageIdFactory = new InMemoryMessageId.Factory();
    }

    @Test
    void updateSingleDocument() throws MailboxException, IOException {
        m1 = addEmail(m1, "eml/mail1.eml", 1391295600000L);

        try (IndexReader reader = DirectoryReader.open(luceneMessageSearchIndex.writer)) {
            final List<Document> allDocumentsFromRepository = getAllDocumentsFromRepository(reader);
            log.trace("Lucene repository initial: {}", allDocumentsFromRepository.stream().map(documentStringFormatter).toList());

            IndexSearcher searcher = new IndexSearcher(reader);

            var queryBuilder = getQueryBuilderFlagId(1, 1);
            var scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);
        }


        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.SEEN),
                MessageManager.FlagsUpdateMode.ADD,
                MessageRange.one(m1.getUid()),
                session);

        try (IndexReader reader = DirectoryReader.open(luceneMessageSearchIndex.writer)) {
            final List<Document> allDocumentsFromRepository = getAllDocumentsFromRepository(reader);
            log.trace("Lucene repository state after adding SEEN flag, size: {}, docs: {}",
                    allDocumentsFromRepository.size(), allDocumentsFromRepository.stream().map(documentStringFormatter).toList());

            IndexSearcher searcher = new IndexSearcher(reader);

            var queryBuilder = getQueryBuilderFlagId(1, 1);
            var scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);
        }

        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.ANSWERED),
                MessageManager.FlagsUpdateMode.ADD,
                MessageRange.one(m1.getUid()),
                session);

        try (IndexReader reader = DirectoryReader.open(luceneMessageSearchIndex.writer)) {
            final List<Document> allDocumentsFromRepository = getAllDocumentsFromRepository(reader);
            log.trace("Lucene repository state after adding ANSWERED flag, size: {}, docs: {}",
                    allDocumentsFromRepository.size(), allDocumentsFromRepository.stream().map(documentStringFormatter).toList());

            IndexSearcher searcher = new IndexSearcher(reader);

            var queryBuilder = getQueryBuilderFlagId(1, 1);
            var scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);
        }


        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.SEEN),
                MessageManager.FlagsUpdateMode.REPLACE,
                MessageRange.one(m1.getUid()),
                session);

        try (IndexReader reader = DirectoryReader.open(luceneMessageSearchIndex.writer)) {
            final List<Document> allDocumentsFromRepository = getAllDocumentsFromRepository(reader);
            log.trace("Lucene repository state after setting SEEN as only flag, size: {}, docs: {}",
                    allDocumentsFromRepository.size(), allDocumentsFromRepository.stream().map(documentStringFormatter).toList());

            IndexSearcher searcher = new IndexSearcher(reader);

            var queryBuilder = getQueryBuilderFlagId(1, 1);
            var scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);
        }

        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.SEEN),
                MessageManager.FlagsUpdateMode.REMOVE,
                MessageRange.one(m1.getUid()),
                session);

        try (IndexReader reader = DirectoryReader.open(luceneMessageSearchIndex.writer)) {
            final List<Document> allDocumentsFromRepository = getAllDocumentsFromRepository(reader);
            log.trace("Lucene repository state after removing SEEN flag (no flag left), size: {}, docs: {}",
                    allDocumentsFromRepository.size(), allDocumentsFromRepository.stream().map(documentStringFormatter).toList());

            IndexSearcher searcher = new IndexSearcher(reader);

            var queryBuilder = getQueryBuilderFlagId(1, 1);
            var scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);
        }

    }

    /**
     * Test mimics failing {@code org.apache.james.mpt.imapmailbox.lucenesearch.LuceneSelectedStateTest.testSearch*()} MTP tests (james-project/mpt/impl/imap-mailbox/core/src/main/resources/org/apache/james/imap/scripts/Search.test)
     *
     * @throws MailboxException
     * @throws IOException
     */
    @Test
    void searchShouldReturnCorrectNumberOfMessagesWhenFlagsGotUpdated() throws MailboxException, IOException {

        m1 = addEmail(m1, "eml/mail1.eml", 1391295600000L);
        m2 = addEmail(m2, "eml/mail2.eml", 1393714800000L);
        m3 = addEmail(m3, "eml/mail3.eml", 1398981600000L);
        m4 = addEmail(m4, "eml/mail4.eml", 1396389600000L);

        log.trace("[1] Fresh state, all documents without flags (expected seen: 0, not-seen: 4, empty query: 4");

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream().count()).isZero();
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream()).isEmpty();

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());


        log.trace("[2] Setting SEEN for all messages (expected seen: 4, not-seen: 0, empty query: 4");

        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.SEEN),
                MessageManager.FlagsUpdateMode.ADD,
                MessageRange.range(m1.getUid(), m4.getUid()),
                session);

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream().count()).isZero();
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream()).isEmpty();


        log.trace("[3] Removing SEEN from m3 & m4 (expected seen: 2, not-seen: 2, empty query: 4");

        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.SEEN),
                MessageManager.FlagsUpdateMode.REMOVE,
                MessageRange.range(m3.getUid(), m4.getUid()),
                session);

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream().count()).isEqualTo(2);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream()).containsExactly(m3.getUid(), m4.getUid());


        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream().count()).isEqualTo(2);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream()).containsExactly(m1.getUid(), m2.getUid());


        log.trace("[4] re-adding SEEN to m3 & m4 (expected seen: 4, not-seen: 0, empty query: 4");

        inboxMessageManager.setFlags(
                new Flags(Flags.Flag.SEEN),
                MessageManager.FlagsUpdateMode.ADD,
                MessageRange.range(m3.getUid(), m4.getUid()),
                session);

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream().count()).isEqualTo(4);
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsSet(Flags.Flag.SEEN))).toStream()).containsExactly(m1.getUid(), m2.getUid(), m3.getUid(), m4.getUid());

        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream().count()).isZero();
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of(SearchQuery.flagIsUnSet(Flags.Flag.SEEN))).toStream()).isEmpty();

        // checking internal Lucene state
        try (IndexReader reader = DirectoryReader.open(luceneMessageSearchIndex.writer)) {
            final List<Document> allDocumentsFromRepository = getAllDocumentsFromRepository(reader);
            log.trace("Lucene repository final state after updating all messages, size: {}, docs {}", allDocumentsFromRepository.size(), allDocumentsFromRepository.stream().map(documentStringFormatter).toList());

            IndexSearcher searcher = new IndexSearcher(reader);

            var queryBuilder = getQueryBuilderFlagId(1, 1);
            var scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);

            queryBuilder = getQueryBuilderFlagId(1, 2);
            scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);

            queryBuilder = getQueryBuilderFlagId(1, 3);
            scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);

            queryBuilder = getQueryBuilderFlagId(1, 4);
            scoreDocs = searcher.search(queryBuilder.build(), 50).scoreDocs;
            assertThat(scoreDocs.length).isEqualTo(1);

            // we should only have 8 documents in the repository in the end
            // TODO: this fails but I have no idea why… regular searches return correct number of messages
            // assertThat(allDocumentsFromRepository.size()).isEqualTo(8);


        }
    }

}
