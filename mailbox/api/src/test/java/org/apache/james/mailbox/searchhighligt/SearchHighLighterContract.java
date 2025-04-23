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

package org.apache.james.mailbox.searchhighligt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

public interface SearchHighLighterContract {
    Username USERNAME1 = Username.of("username1");

    SearchHighlighter testee();

    MailboxSession session(Username username);

    MessageManager.AppendResult appendMessage(MessageManager.AppendCommand appendCommand, MailboxSession session);

    MailboxId randomMailboxId(Username username);

    void applyRightsCommand(MailboxId mailboxId, Username owner, Username delegated);

    default void verifyMessageWasIndexed(int indexedMessageCount) throws MailboxException {
    }

    @Test
    default void highlightSearchShouldReturnHighLightedSubjectWhenMatched() throws Exception {
        MailboxSession session = session(USERNAME1);

        // Given m1,m2 with m1 has subject containing the searched word (Matthieu)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (Matthieu) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Matthieu")))
            .inMailboxes(List.of(m2.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Matthieu)
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, Thx <mark>Matthieu</mark> for your help");
        });
    }

    @Test
    default void shouldHtmlEscapeTheAmpersandCharacter() throws Exception {
        MailboxSession session = session(USERNAME1);

        // & (ampersand), < (less-than sign), and > (greater-than sign) characters must be HTML escaped
        // following JMAP specs https://jmap.io/spec-mail.html#search-snippets
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, this & character should be escaped.")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word `character` in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("character")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject that has ampersand character escaped.
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, this &amp; <mark>character</mark> should be escaped.");
        });
    }

    @Test
    default void shouldHtmlEscapeTheLessThanCharacter() throws Exception {
        MailboxSession session = session(USERNAME1);

        // & (ampersand), < (less-than sign), and > (greater-than sign) characters must be HTML escaped
        // following JMAP specs https://jmap.io/spec-mail.html#search-snippets
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, this < character should be escaped.")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word `character` in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("character")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject that has ampersand character escaped.
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, this &lt; <mark>character</mark> should be escaped.");
        });
    }

    @Test
    default void shouldHtmlEscapeTheGreaterThanCharacter() throws Exception {
        MailboxSession session = session(USERNAME1);

        // & (ampersand), < (less-than sign), and > (greater-than sign) characters must be HTML escaped
        // following JMAP specs https://jmap.io/spec-mail.html#search-snippets
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, this > character should be escaped.")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word `character` in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("character")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject that has ampersand character escaped.
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, this &gt; <mark>character</mark> should be escaped.");
        });
    }

    @Test
    default void shouldNotHtmlEscapeTheSlashCharacter() throws Exception {
        MailboxSession session = session(USERNAME1);

        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, this / character should not be escaped.")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word `character` in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("character")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject that has ampersand character escaped.
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, this / <mark>character</mark> should not be escaped.");
        });
    }

    @Test
    default void highlightSearchShouldReturnHighlightedBodyWhenMatched() throws Exception {
        MailboxSession session = session(USERNAME1);

        // Given m1,m2 with m1 has body containing the searched word (contentA)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (contentA) in the body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("contentA")))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedBody containing the word (contentA)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedBody()).contains("append <mark>contentA</mark> to inbox");
        });
    }

    @Test
    default void searchBothSubjectAndBodyHighLightShouldReturnEmptyWhenNotMatched() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given m1,m2 with m1,m2 has both body+subject not containing the searched word (contentC)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (contentC) in both subject and body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("contentC"),
                SearchQuery.subject("contentC")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        // Then highlightSearch should return an empty
        assertThat(Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block()).isEmpty();
    }

    @Test
    default void searchBothSubjectAndBodyHighLightShouldReturnEntryWhenMatched() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given m1,m2 with m1 has body + subject containing the searched word (Naruto)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Naruto for your help")
                    .setBody("append Naruto to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (Naruto) in both subject and body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("Naruto"),
                SearchQuery.subject("Naruto")))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedBody/highlightedSubject containing the word (Naruto)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedBody()).contains("append <mark>Naruto</mark> to inbox");
            softly.assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, Thx <mark>Naruto</mark> for your help");
        });
    }


    @Test
    default void highlightSearchShouldReturnMultipleResultsWhenMultipleMatches() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given m1,m2 with m1,m2 has subject containing the searched word (WeeklyReport)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 04/10/2024")
                    .setBody("The weekly report has been in attachment1", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 11/10/2024")
                    .setBody("The weekly report has been in attachment2", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (WeeklyReport) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("WeeklyReport")))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (WeeklyReport)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(2);
        assertThat(searchSnippets.stream().map(SearchSnippet::highlightedSubject))
            .allSatisfy(highlightedSubject -> assertThat(highlightedSubject.get()).contains("Tran Van Tung <mark>WeeklyReport</mark>"));
    }

    @Test
    default void highlightSearchShouldReturnCorrectFormatWhenSearchTwoWords() throws Exception {
        MailboxSession session = session(USERNAME1);
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Naruto Itachi for your help")
                    .setBody("append Naruto Itachi to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word (Naruto and Itachi) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.bodyContains("Naruto Itachi")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Naruto) (and) (Itachi)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertThat(searchSnippets.getFirst().highlightedBody()).contains("append <mark>Naruto</mark> <mark>Itachi</mark> to inbox");
    }

    @Test
    default void highlightSearchShouldReturnEmptyResultsWhenKeywordNoMatch() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given m1 that has both subject + body not containing the searched word (Vietnam)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 04/10/2024")
                    .setBody("The weekly report has been in attachment1", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word (Vietnam) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Vietnam")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        // Then highlightSearch should return an empty list
        assertThat(Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block()).isEmpty();
    }

    @Test
    default void highlightSearchShouldReturnEmptyResultsWhenMailboxIdNoMatch() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given message m1 of mailbox Mailbox.inbox(username1)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Tran Van Tung WeeklyReport 04/10/2024")
                    .setBody("The weekly report has been in attachment1", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word (WeeklyReport) in the subject but in another mailbox
        MailboxId randomMailboxId = randomMailboxId(USERNAME1);

        // Then highlightSearch should return an empty list
        assertThat(Flux.from(testee().highlightSearch(List.of(m1.getMessageId()),
                MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("WeeklyReport")))
                    .inMailboxes(List.of(randomMailboxId)).build(), session))
            .collectList()
            .block()).isEmpty();
    }

    @Test
    default void highlightSearchShouldNotReturnEntryWhenDoesNotAccessible() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given messages of username1
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // When searching for the word (Matthieu) in the subject, but the mailbox is not accessible
        MailboxSession notAccessible = session(Username.of("notAccessible"));
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Matthieu")))
                .inMailboxes(List.of(m1.getMailboxId()))
                .build(), notAccessible))
            .collectList()
            .block();

        // Then highlightSearch should not return username1 entry
        assertThat(searchSnippets).hasSize(0);
    }

    @Test
    default void highlightSearchShouldReturnEntryWhenHasAccessible() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given messages of username1
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        // Set right for delegated1 to access username1 mailbox
        Username delegated1 = Username.of("delegated");
        MailboxSession delegated1Session = session(delegated1);
        applyRightsCommand(m1.getMailboxId(), USERNAME1, delegated1);

        // When searching for the word (Matthieu) in the subject
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("Matthieu")))
                .inMailboxes(List.of(m1.getMailboxId()))
                .build(), delegated1Session))
            .collectList()
            .block();

        // Then highlightSearch should return username1 entry
        assertThat(searchSnippets).hasSize(1);
        assertThat(searchSnippets.getFirst().highlightedSubject()).contains("Hallo, Thx <mark>Matthieu</mark> for your help");
    }

    @Test
    default void highLightSearchShouldSupportConjunctionCriterionInMultiMessage() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given m1,m2
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Naruto for your help")
                    .setBody("append Naruto to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (Naruto) or (Alex) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.OR,
                    List.of(SearchQuery.subject("Naruto"), SearchQuery.subject("Alex")))))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Naruto) or (Alex)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(2);
        assertThat(searchSnippets.stream()
            .map(SearchSnippet::highlightedSubject)
            .toList())
            .containsExactlyInAnyOrder(Optional.of("Hallo, Thx <mark>Naruto</mark> for your help"),
                Optional.of("Hallo, Thx <mark>Alex</mark> for your help"));
    }

    @Test
    default void highLightSearchShouldSupportConjunctionCriterionInSingleMessage() throws Exception {
        MailboxSession session = session(USERNAME1);
        // Given m1,m2
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Naruto for your help - Sasuke for your help")
                    .setBody("append Naruto to inbox", StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (Naruto) or (Sasuke) in the subject
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.OR,
                    List.of(SearchQuery.subject("Naruto"), SearchQuery.subject("Sasuke")))))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedSubject containing the word (Naruto) or (Sasuke)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertThat(searchSnippets.stream()
            .map(SearchSnippet::highlightedSubject)
            .toList())
            .containsExactlyInAnyOrder(Optional.of("Hallo, Thx <mark>Naruto</mark> for your help - <mark>Sasuke</mark> for your help"));
    }

    @Test
    default void highLightSearchShouldReturnEmptyWhenMessageIdsIsEmpty() throws Exception {
        MailboxSession session = session(USERNAME1);
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Naruto Itachi for your help")
                    .setBody("append Naruto Itachi to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(1);

        List<MessageId> messageIdsSearch = List.of();

        assertThat(Flux.from(testee().highlightSearch(messageIdsSearch, MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.bodyContains("Naruto Itachi")))
                .inMailboxes(List.of(m1.getMailboxId()))
                .build(), session))
            .collectList()
            .block()).hasSize(0);
    }

    @Test
    default void shouldHighlightAttachmentTextContentWhenTextBodyDoesNotMatch() throws Exception {
        MailboxSession session = session(USERNAME1);

        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("append contentA to inbox", StandardCharsets.UTF_8)),
            session).getId();

        // m2 has an attachment with text content: "This is a beautiful banana"
        ComposedMessageId m2 = appendMessage(
            MessageManager.AppendCommand.builder()
                .build(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/emailWithTextAttachment.eml")),
            session).getId();

        verifyMessageWasIndexed(2);

        String keywordSearch = "beautiful";
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.OR,
                    List.of(SearchQuery.bodyContains(keywordSearch),
                        SearchQuery.attachmentContains(keywordSearch)))))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();

        assertThat(searchSnippets).hasSize(1);

        assertThat(searchSnippets.getFirst().highlightedBody())
            .isPresent()
            .satisfies(highlightedBody -> assertThat(highlightedBody.get()).contains("This is a <mark>beautiful</mark> banana"));
    }

    @Test
    default void shouldHighLightBodyWhenHTMLBodyMatched() throws Exception {
        Message message1 = Message.Builder.of()
            .setBody(MultipartBuilder.create("report")
                .addBinaryPart("content <b>barcamp</b> <i>HTML</i> xinchao".getBytes(StandardCharsets.UTF_8), "text/html")
                .build())
            .build();
        MailboxSession session = session(USERNAME1);
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(message1), session).getId();

        verifyMessageWasIndexed(1);

        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("barcamp")))
            .inMailboxes(List.of(m1.getMailboxId()))
            .build();

        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedBody()).isPresent();
            softly.assertThat(searchSnippets.getFirst().highlightedBody().get()).contains("<mark>barcamp</mark>");
        });
    }

    @Test
    default void highlightSearchShouldShortenGreaterThanCharacters() throws Exception {
        MailboxSession session = session(USERNAME1);

        // Given m1,m2 with m1 has body containing the searched word (contentA)
        ComposedMessageId m1 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Matthieu for your help")
                    .setBody("Start \n>>>>>>>>>> append contentA to > inbox \n>>>>>> End",
                        StandardCharsets.UTF_8)),
            session).getId();

        ComposedMessageId m2 = appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo("to@james.local")
                    .setSubject("Hallo, Thx Alex for your help")
                    .setBody("append contentB to inbox", StandardCharsets.UTF_8)),
            session).getId();

        verifyMessageWasIndexed(2);

        // When searching for the word (contentA) in the body
        MultimailboxesSearchQuery multiMailboxSearch = MultimailboxesSearchQuery.from(SearchQuery.of(
                SearchQuery.bodyContains("contentA")))
            .inMailboxes(List.of(m1.getMailboxId(), m2.getMailboxId()))
            .build();

        // Then highlightSearch should return the SearchSnippet with the highlightedBody containing the word (contentA)
        List<SearchSnippet> searchSnippets = Flux.from(testee().highlightSearch(List.of(m1.getMessageId(), m2.getMessageId()), multiMailboxSearch, session))
            .collectList()
            .block();
        assertThat(searchSnippets).hasSize(1);
        assertSoftly(softly -> {
            softly.assertThat(searchSnippets.getFirst().messageId()).isEqualTo(m1.getMessageId());
            softly.assertThat(searchSnippets.getFirst().highlightedBody().get()).isEqualTo("Start \n append <mark>contentA</mark> to &gt; inbox \n End");
        });
    }
}
