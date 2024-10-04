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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.searchhighligt.SearchHighlighterConfiguration;
import org.apache.james.mailbox.searchhighligt.SearchSnippet;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class LuceneMemorySearchHighLightTest {
    private static final Username USERNAME1 = Username.of("username1");
    protected MessageSearchIndex messageSearchIndex;
    protected StoreMailboxManager storeMailboxManager;
    protected MessageIdManager messageIdManager;
    protected EventBus eventBus;
    protected MessageId.Factory messageIdFactory;
    protected UpdatableTickingClock clock;
    private StoreMessageManager inboxMessageManager;
    private MailboxSession session;
    private LuceneMemorySearchHighlighter testee;

    private Mailbox mailbox;

    @BeforeEach
    public void setUp() throws Exception {
        messageIdFactory = new InMemoryMessageId.Factory();
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .listeningSearchIndex(Throwing.function(preInstanciationStage -> new LuceneMessageSearchIndex(
                preInstanciationStage.getMapperFactory(), new InMemoryId.Factory(), new ByteBuffersDirectory(),
                messageIdFactory,
                preInstanciationStage.getSessionProvider())))
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        storeMailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        messageSearchIndex = resources.getSearchIndex();
        eventBus = resources.getEventBus();
        messageIdFactory = new InMemoryMessageId.Factory();

        clock = (UpdatableTickingClock) storeMailboxManager.getClock();
        testee = new LuceneMemorySearchHighlighter(((LuceneMessageSearchIndex) messageSearchIndex),
            SearchHighlighterConfiguration.DEFAULT,
            messageIdFactory, storeMailboxManager);

        session = storeMailboxManager.createSystemSession(USERNAME1);
        MailboxPath inboxPath = MailboxPath.inbox(USERNAME1);
        storeMailboxManager.createMailbox(inboxPath, session);
        inboxMessageManager = (StoreMessageManager) storeMailboxManager.getMailbox(inboxPath, session);
        mailbox = inboxMessageManager.getMailboxEntity();
    }

    @Test
    void highlightSearchShouldReturnHighLightedSubjectWhenMatched() throws Exception {
        // Given m1,m2 with m1 has subject containing the searched word (Matthieu)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (Matthieu) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Matthieu")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session)
            .collectList()
            .block();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Matthieu)
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo! Thx <mark>Matthieu</mark> for your help");
        });
    }

    @Test
    void highlightSearchShouldReturnHighlightedBodyWhenMatched() throws Exception {
        // Given m1,m2 with m1 has body containing the searched word (contentA)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (contentA) in the body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("contentA")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedBody containing the word (contentA)
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session)
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedBody()).contains("append <mark>contentA</mark> to inbox");
        });
    }

    @Test
    void searchBothSubjectAndBodyHighLightShouldReturnEmptyWhenNotMatched() throws Exception {
        // Given m1,m2 with m1,m2 has both body+subject not containing the searched word (contentC)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (contentC) in both subject and body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("contentC"),
                SearchQuery.subject("contentC")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return an empty
        assertThat(testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session)
            .collectList()
            .block()).isEmpty();
    }

    @Test
    void searchBothSubjectAndBodyHighLightShouldReturnEntryWhenMatched() throws Exception {
        // Given m1,m2 with m1 has body + subject containing the searched word (Naruto)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Naruto for your help")
                    .setBody("append Naruto to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (Naruto) in both subject and body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("Naruto"),
                SearchQuery.subject("Naruto")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedBody/highlightedSubject containing the word (Naruto)
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()),multiMailboxSearch, session)
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedBody()).contains("append <mark>Naruto</mark> to inbox");
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo! Thx <mark>Naruto</mark> for your help");
        });
    }

    @Test
    void highlightSearchShouldReturnMultipleResultsWhenMultipleMatches() throws Exception {
        // Given m1,m2 with m1,m2 has subject containing the searched word (WeeklyReport)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 04/10/2024")
                    .setBody("The weekly report has been in attachment1", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 11/10/2024")
                    .setBody("The weekly report has been in attachment2", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (WeeklyReport) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("WeeklyReport")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (WeeklyReport)
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()),multiMailboxSearch, session)
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(2);
        assertThat(searchSnippets.stream().map(SearchSnippet::highlightedSubject))
            .allSatisfy(highlightedSubject -> assertThat(highlightedSubject.get()).contains("Tran Van Tung <mark>WeeklyReport</mark>"));
    }

    @Test
    void highlightSearchShouldReturnCorrectFormatWhenSearchTwoWords() throws Exception {
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Naruto Itachi for your help")
                    .setBody("append Naruto Itachi to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(1);

        // When searching for the word (Naruto and Itachi) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.bodyContains("Naruto Itachi")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Naruto) (and) (Itachi)
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId()),multiMailboxSearch, session)
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertThat(searchSnippets.getFirst().highlightedBody()).contains("append <mark>Naruto</mark> <mark>Itachi</mark> to inbox");
    }

    @Test
    void highlightSearchShouldReturnEmptyResultsWhenKeywordNoMatch() throws Exception {
        // Given m1 that has both subject + body not containing the searched word (Vietnam)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 04/10/2024")
                    .setBody("The weekly report has been in attachment1", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(1);

        // When searching for the word (Vietnam) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Vietnam")))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return an empty list
        assertThat(testee.highlightSearch(List.of(m1.getMessageId()),multiMailboxSearch, session)
            .collectList()
            .block()).isEmpty();
    }

    @Test
    void highlightSearchShouldReturnEmptyResultsWhenMailboxIdNoMatch() throws Exception {
        // Given message m1 of mailbox Mailbox.inbox(username1)
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 04/10/2024")
                    .setBody("The weekly report has been in attachment1", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(1);

        // When searching for the word (WeeklyReport) in the subject but in another mailbox
        MailboxId randomMailboxId = storeMailboxManager.createMailbox(MailboxPath.forUser(USERNAME1, "random1"), session).get();

        // Then highlightSearch should return an empty list
        assertThat(testee.highlightSearch(List.of(m1.getMessageId()),
                MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("WeeklyReport")))
                    .inMailboxes(List.of(randomMailboxId)).build(), session)
            .collectList()
            .block()).isEmpty();
    }

    @Test
    void highlightSearchShouldNotReturnEntryWhenDoesNotAccessible() throws Exception {
        // Given messages of username1
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(1);

        // When searching for the word (Matthieu) in the subject, but the mailbox is not accessible
        MailboxSession notAccessible = storeMailboxManager.createSystemSession(Username.of("notAccessible"));
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId()),MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Matthieu")))
                .inMailboxes(List.of(mailbox.getMailboxId()))
                .build(), notAccessible)
            .collectList()
            .block();

        // Then highlightSearch should not return username1 entry
        assertThat(searchSnippets).hasSize(0);
    }

    @Test
    void highlightSearchShouldReturnEntryWhenHasAccessible() throws Exception {
        // Given messages of username1
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(1);

        // Set right for delegated1 to access username1 mailbox
        Username delegated1 = Username.of("delegated");
        MailboxSession delegated1Session = storeMailboxManager.createSystemSession(delegated1);
        storeMailboxManager.applyRightsCommand(mailbox.generateAssociatedPath(),
            MailboxACL.command().forUser(delegated1).rights(MailboxACL.FULL_RIGHTS).asAddition(),
            session);

        // When searching for the word (Matthieu) in the subject
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId()), MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Matthieu")))
                .inMailboxes(List.of(mailbox.getMailboxId()))
                .build(), delegated1Session)
            .collectList()
            .block();

        // Then highlightSearch should return username1 entry
        assertThat(searchSnippets).hasSize(1);
        assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo! Thx <mark>Matthieu</mark> for your help");
    }

    @Test
    void highLightSearchShouldSupportConjunctionCriterionInMultiMessage() throws Exception {
        // Given m1,m2
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Naruto for your help")
                    .setBody("append Naruto to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (Naruto) or (Alex) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.OR,
                    List.of(SearchQuery.subject("Naruto"), SearchQuery.subject("Alex")))))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Naruto) or (Alex)
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session)
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(2);
        assertThat(searchSnippets.stream()
            .map(SearchSnippet::highlightedSubject)
            .toList())
            .containsExactlyInAnyOrder(Optional.of("Hallo! Thx <mark>Naruto</mark> for your help"),
                Optional.of("Hallo! Thx <mark>Alex</mark> for your help"));
    }

    @Test
    void highLightSearchShouldSupportConjunctionCriterionInSingleMessage() throws Exception {
        // Given m1,m2
        ComposedMessageId m1 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Naruto for your help - Sasuke for your help")
                    .setBody("append Naruto to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = inboxMessageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo! Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // Verify that the messages are indexed
        assertThat(messageSearchIndex.search(session, mailbox, SearchQuery.of()).toStream().count()).isEqualTo(2);

        // When searching for the word (Naruto) or (Sasuke) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.OR,
                    List.of(SearchQuery.subject("Naruto"), SearchQuery.subject("Sasuke")))))
            .inMailboxes(List.of(mailbox.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Naruto) or (Sasuke)
        List<SearchSnippet> searchSnippets = testee.highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session)
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertThat(searchSnippets.stream()
            .map(SearchSnippet::highlightedSubject)
            .toList())
            .containsExactlyInAnyOrder(Optional.of("Hallo! Thx <mark>Naruto</mark> for your help - <mark>Sasuke</mark> for your help"));
    }
}
