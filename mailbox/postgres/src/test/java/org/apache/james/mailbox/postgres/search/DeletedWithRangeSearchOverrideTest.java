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

package org.apache.james.mailbox.postgres.search;

import static javax.mail.Flags.Flag.DELETED;
import static javax.mail.Flags.Flag.SEEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DeletedWithRangeSearchOverrideTest {
    private static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(Username.of("benwa"));
    private static final Mailbox MAILBOX = new Mailbox(MailboxPath.inbox(MAILBOX_SESSION), UidValidity.of(12), PostgresMailboxId.generate());
    private static final String BLOB_ID = "abc";
    private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    private static final String MESSAGE_CONTENT = "Simple message content";
    private static final byte[] MESSAGE_CONTENT_BYTES = MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET);
    private static final ByteContent CONTENT_STREAM = new ByteContent(MESSAGE_CONTENT_BYTES);
    private final static long SIZE = MESSAGE_CONTENT_BYTES.length;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMailboxMessageDAO postgresMailboxMessageDAO;
    private PostgresMessageDAO postgresMessageDAO;
    private DeletedWithRangeSearchOverride testee;

    @BeforeEach
    void setUp() {
        postgresMessageDAO = new PostgresMessageDAO(postgresExtension.getPostgresExecutor());
        postgresMailboxMessageDAO = new PostgresMailboxMessageDAO(postgresExtension.getPostgresExecutor());
        testee = new DeletedWithRangeSearchOverride(postgresMailboxMessageDAO);
    }

    @Test
    void deletedWithRangeQueryShouldBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsSet(DELETED))
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.of(4), MessageUid.of(45))))
                .build(),
            MAILBOX_SESSION))
            .isTrue();
    }

    @Test
    void deletedQueryShouldNotBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsSet(DELETED))
                .build(),
            MAILBOX_SESSION))
            .isFalse();
    }

    @Test
    void sizeQueryShouldNotBeApplicable() {
        assertThat(testee.applicable(
            SearchQuery.builder()
                .andCriteria(SearchQuery.sizeEquals(12))
                .build(),
            MAILBOX_SESSION))
            .isFalse();
    }

    @Test
    void searchShouldReturnEmptyByDefault() {
        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsUnSet(SEEN))
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(MessageUid.MIN_VALUE, MessageUid.of(45))))
                .build()).collectList().block())
            .isEmpty();
    }

    @Test
    void searchShouldReturnMailboxEntries() {
        MessageUid messageUid = MessageUid.of(1);
        PostgresMessageId messageId = new PostgresMessageId.Factory().generate();
        MailboxMessage message1 = SimpleMailboxMessage.builder()
            .messageId(messageId)
            .threadId(ThreadId.fromBaseMessageId(messageId))
            .uid(messageUid)
            .content(CONTENT_STREAM)
            .size(SIZE)
            .internalDate(new Date())
            .bodyStartOctet(18)
            .flags(new Flags(DELETED))
            .properties(new PropertyBuilder())
            .mailboxId(MAILBOX.getMailboxId())
            .modseq(ModSeq.of(1))
            .build();
        postgresMessageDAO.insert(message1, BLOB_ID).block();
        postgresMailboxMessageDAO.insert(message1).block();

        MessageUid messageUid2 = MessageUid.of(2);
        PostgresMessageId messageId2 = new PostgresMessageId.Factory().generate();
        MailboxMessage message2 = SimpleMailboxMessage.builder()
            .messageId(messageId2)
            .threadId(ThreadId.fromBaseMessageId(messageId2))
            .uid(messageUid2)
            .content(CONTENT_STREAM)
            .size(SIZE)
            .internalDate(new Date())
            .bodyStartOctet(18)
            .flags(new Flags(DELETED))
            .properties(new PropertyBuilder())
            .mailboxId(MAILBOX.getMailboxId())
            .modseq(ModSeq.of(1))
            .build();
        postgresMessageDAO.insert(message2, BLOB_ID).block();
        postgresMailboxMessageDAO.insert(message2).block();

        MessageUid messageUid3 = MessageUid.of(3);
        PostgresMessageId messageId3 = new PostgresMessageId.Factory().generate();
        MailboxMessage message3 = SimpleMailboxMessage.builder()
            .messageId(messageId3)
            .threadId(ThreadId.fromBaseMessageId(messageId3))
            .uid(messageUid3)
            .content(CONTENT_STREAM)
            .size(SIZE)
            .internalDate(new Date())
            .bodyStartOctet(18)
            .flags(new Flags(DELETED))
            .properties(new PropertyBuilder())
            .mailboxId(MAILBOX.getMailboxId())
            .modseq(ModSeq.of(1))
            .build();
        postgresMessageDAO.insert(message3, BLOB_ID).block();
        postgresMailboxMessageDAO.insert(message3).block();

        MessageUid messageUid4 = MessageUid.of(4);
        PostgresMessageId messageId4 = new PostgresMessageId.Factory().generate();
        MailboxMessage message4 = SimpleMailboxMessage.builder()
            .messageId(messageId4)
            .threadId(ThreadId.fromBaseMessageId(messageId4))
            .uid(messageUid4)
            .content(CONTENT_STREAM)
            .size(SIZE)
            .internalDate(new Date())
            .bodyStartOctet(18)
            .flags(new Flags(DELETED))
            .properties(new PropertyBuilder())
            .mailboxId(MAILBOX.getMailboxId())
            .modseq(ModSeq.of(1))
            .build();
        postgresMessageDAO.insert(message4, BLOB_ID).block();
        postgresMailboxMessageDAO.insert(message4).block();

        MessageUid messageUid5 = MessageUid.of(5);
        PostgresMessageId messageId5 = new PostgresMessageId.Factory().generate();
        MailboxMessage message5 = SimpleMailboxMessage.builder()
            .messageId(messageId5)
            .threadId(ThreadId.fromBaseMessageId(messageId5))
            .uid(messageUid5)
            .content(CONTENT_STREAM)
            .size(SIZE)
            .internalDate(new Date())
            .bodyStartOctet(18)
            .flags(new Flags(DELETED))
            .properties(new PropertyBuilder())
            .mailboxId(MAILBOX.getMailboxId())
            .modseq(ModSeq.of(1))
            .build();
        postgresMessageDAO.insert(message5, BLOB_ID).block();
        postgresMailboxMessageDAO.insert(message5).block();

        assertThat(testee.search(MAILBOX_SESSION, MAILBOX,
            SearchQuery.builder()
                .andCriteria(SearchQuery.flagIsSet(DELETED))
                .andCriteria(SearchQuery.uid(new SearchQuery.UidRange(messageUid2, messageUid4)))
                .build()).collectList().block())
            .containsOnly(messageUid2, messageUid3, messageUid4);
    }
}