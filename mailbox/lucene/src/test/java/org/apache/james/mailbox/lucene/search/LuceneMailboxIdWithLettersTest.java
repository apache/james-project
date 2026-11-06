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

import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.extractor.JsoupTextExtractor;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() throws Exception {
        session = MailboxSessionUtil.create(Username.of("bob"));
        TestMessageId.Factory messageIdFactory = new TestMessageId.Factory();
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
                    .build(messageIdFactory.generate()))
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
}
