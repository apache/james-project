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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.searchhighligt.SearchHighlighterConfiguration;
import org.apache.james.mailbox.searchhighligt.SearchSnippet;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class LuceneMailboxIdWithLettersTest {
    record LetterId(String value) implements MailboxId {
        @Override
        public String serialize() {
            return value;
        }
    }

    static final LetterId MAILBOX_ID = new LetterId("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    static final MessageUid UID_1 = MessageUid.of(1);
    static final MessageUid UID_2 = MessageUid.of(2);

    Mailbox mailbox = new Mailbox(MailboxPath.forUser(Username.of("bob"), "box"), UidValidity.of(18), MAILBOX_ID);
    LuceneMessageSearchIndex index;
    MailboxSession session;
    TestMessageId.Factory messageIdFactory;
    MessageId messageId1;

    @BeforeEach
    void setUp() throws Exception {
        session = MailboxSessionUtil.create(Username.of("bob"));
        messageIdFactory = new TestMessageId.Factory();
        messageId1 = messageIdFactory.generate();
        index = new LuceneMessageSearchIndex(null, LetterId::new, new ByteBuffersDirectory(), true, messageIdFactory, null, new JsoupTextExtractor());

        for (MessageUid uid : List.of(UID_1, UID_2)) {
            index.add(session, mailbox, new MessageBuilder()
                    .header("Subject", "Quarterly report")
                    .flags(new Flags(Flags.Flag.SEEN))
                    .mailboxId(MAILBOX_ID)
                    .uid(uid)
                    .internalDate(new Date())
                    .body("Hello".getBytes(UTF_8))
                    .size(200)
                    .build(uid.equals(UID_1) ? messageId1 : messageIdFactory.generate()))
                .block();
        }
    }

    @Test
    void searchShouldMatchMessagesOfAMailboxIdWithLowerCaseLetters() throws Exception {
        assertThat(index.doSearch(session, mailbox, SearchQuery.of(SearchQuery.subject("quarterly"))).collectList().block())
            .containsExactly(UID_1, UID_2);
    }

    @Test
    void deleteShouldRemoveMessagesOfAMailboxIdWithLowerCaseLetters() throws Exception {
        index.delete(session, MAILBOX_ID, List.of(UID_1)).block();

        assertThat(index.doSearch(session, mailbox, SearchQuery.of(SearchQuery.all())).collectList().block())
            .containsExactly(UID_2);
    }

    @Test
    void highlightShouldReturnSnippetsOfAMailboxIdWithLowerCaseLetters() {
        StoreMailboxManager mailboxManager = mock(StoreMailboxManager.class);
        when(mailboxManager.getInMailboxIds(any(), any())).thenReturn(Flux.just(MAILBOX_ID));
        LuceneSearchHighlighter highlighter = new LuceneSearchHighlighter(index, SearchHighlighterConfiguration.DEFAULT, messageIdFactory, mailboxManager);

        MultimailboxesSearchQuery query = MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.subject("quarterly")))
            .inMailboxes(MAILBOX_ID)
            .build();

        assertThat(highlighter.highlightSearch(List.of(messageId1), query, session).collectList().block())
            .extracting(SearchSnippet::messageId, SearchSnippet::highlightedSubject)
            .containsExactly(tuple(messageId1, Optional.of("<mark>Quarterly</mark> report")));
    }
}
