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

package org.apache.james.mailbox.postgres;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.UUID;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.postgres.mail.PostgresMailboxManager;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public abstract class DeleteMessageListenerContract {

    private MailboxSession session;
    private MailboxPath inbox;
    private MessageManager inboxManager;
    private MessageManager otherBoxManager;
    private PostgresMailboxManager mailboxManager;
    private PostgresMessageDAO postgresMessageDAO;
    private PostgresMailboxMessageDAO postgresMailboxMessageDAO;

    abstract PostgresMailboxManager provideMailboxManager();
    abstract PostgresMessageDAO providePostgresMessageDAO();
    abstract PostgresMailboxMessageDAO providePostgresMailboxMessageDAO();

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = provideMailboxManager();
        Username username = getUsername();
        session = mailboxManager.createSystemSession(username);
        inbox = MailboxPath.inbox(session);
        MailboxPath newPath = MailboxPath.forUser(username, "specialMailbox");
        MailboxId inboxId = mailboxManager.createMailbox(inbox, session).get();
        inboxManager = mailboxManager.getMailbox(inboxId, session);
        MailboxId otherId = mailboxManager.createMailbox(newPath, session).get();
        otherBoxManager = mailboxManager.getMailbox(otherId, session);

        postgresMessageDAO = providePostgresMessageDAO();
        postgresMailboxMessageDAO = providePostgresMailboxMessageDAO();
    }

    protected Username getUsername() {
        return Username.of("user" + UUID.randomUUID());
    }

    @Test
    void deleteMailboxShouldDeleteUnreferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

        mailboxManager.deleteMailbox(inbox, session);

        assertSoftly(softly -> {
            PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();
            PostgresMailboxId mailboxId = (PostgresMailboxId) appendResult.getId().getMailboxId();

            softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                .isEmpty();

            softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId(mailboxId).block())
                .isEqualTo(0);
        });
    }

    @Test
    void deleteMailboxShouldNotDeleteReferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);

        mailboxManager.deleteMailbox(inbox, session);

        assertSoftly(softly -> {
            PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

            softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                .isNotEmpty();

            softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId((PostgresMailboxId) otherBoxManager.getId())
                    .block())
                .isEqualTo(1);
        });
    }

    @Test
    void deleteMessageInMailboxShouldDeleteUnreferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

        inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

        assertSoftly(softly -> {
            PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

            softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                .isEmpty();
        });
    }

    @Test
    void deleteMessageInMailboxShouldNotDeleteReferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);

        inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);
        PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

        assertSoftly(softly -> {
            softly.assertThat(postgresMessageDAO.getBlobId(messageId).blockOptional())
                .isNotEmpty();

            softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId((PostgresMailboxId) otherBoxManager.getId())
                    .block())
                .isEqualTo(1);
        });
    }
}
