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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public abstract class DeleteMessageListenerContract {

    private MailboxSession session;
    private MailboxPath inbox;
    private MessageManager inboxManager;
    private MessageManager otherBoxManager;
    private PostgresMailboxManager mailboxManager;
    private PostgresMessageDAO postgresMessageDAO;
    private PostgresMailboxMessageDAO postgresMailboxMessageDAO;

    private PostgresAttachmentDAO attachmentDAO;
    private BlobStore blobStore;

    abstract PostgresMailboxManager provideMailboxManager();

    abstract PostgresMessageDAO providePostgresMessageDAO();

    abstract PostgresMailboxMessageDAO providePostgresMailboxMessageDAO();

    abstract PostgresAttachmentDAO attachmentDAO();

    abstract BlobStore blobStore();

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
        attachmentDAO = attachmentDAO();
        blobStore = blobStore();
    }

    protected Username getUsername() {
        return Username.of("user" + UUID.randomUUID());
    }

    @Test
    void deleteMailboxShouldDeleteUnreferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);

        AttachmentId attachmentId = appendResult.getMessageAttachments().get(0).getAttachment().getAttachmentId();

        mailboxManager.deleteMailbox(inbox, session);

        assertSoftly(softly -> {
            PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();
            PostgresMailboxId mailboxId = (PostgresMailboxId) appendResult.getId().getMailboxId();

            softly.assertThat(postgresMessageDAO.getBodyBlobId(messageId).blockOptional())
                .isEmpty();

            softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId(mailboxId).block())
                .isEqualTo(0);

            softly.assertThat(attachmentDAO.getAttachment(attachmentId).blockOptional())
                .isEmpty();
        });
    }

    @Test
    void deleteMailboxShouldNotDeleteReferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);
        AttachmentId attachmentId = appendResult.getMessageAttachments().get(0).getAttachment().getAttachmentId();

        mailboxManager.deleteMailbox(inbox, session);

        assertSoftly(softly -> {
            PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

            softly.assertThat(postgresMessageDAO.getBodyBlobId(messageId).blockOptional())
                .isNotEmpty();

            softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId((PostgresMailboxId) otherBoxManager.getId())
                    .block())
                .isEqualTo(1);

            softly.assertThat(attachmentDAO.getAttachment(attachmentId).blockOptional())
                .isNotEmpty();
        });
    }

    @Test
    void deleteMessageInMailboxShouldDeleteUnreferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        AttachmentId attachmentId = appendResult.getMessageAttachments().get(0).getAttachment().getAttachmentId();

        inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

        assertSoftly(softly -> {
            PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

            softly.assertThat(postgresMessageDAO.getBodyBlobId(messageId).blockOptional())
                .isEmpty();

            softly.assertThat(attachmentDAO.getAttachment(attachmentId).blockOptional())
                .isEmpty();
        });
    }

    @Test
    void deleteMessageInMailboxShouldNotDeleteReferencedMessageMetadata() throws Exception {
        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);
        AttachmentId attachmentId = appendResult.getMessageAttachments().get(0).getAttachment().getAttachmentId();

        inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);
        PostgresMessageId messageId = (PostgresMessageId) appendResult.getId().getMessageId();

        assertSoftly(softly -> {
            softly.assertThat(postgresMessageDAO.getBodyBlobId(messageId).blockOptional())
                .isNotEmpty();

            softly.assertThat(postgresMailboxMessageDAO.countTotalMessagesByMailboxId((PostgresMailboxId) otherBoxManager.getId())
                    .block())
                .isEqualTo(1);

            softly.assertThat(attachmentDAO.getAttachment(attachmentId).blockOptional())
                .isNotEmpty();
        });
    }

    @Test
    void deleteMessageListenerShouldDeleteUnreferencedBlob() throws Exception {
        assumeTrue(!(blobStore instanceof DeDuplicationBlobStore));

        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        AttachmentId attachmentId = appendResult.getMessageAttachments().get(0).getAttachment().getAttachmentId();

        BlobId attachmentBlobId = attachmentDAO.getAttachment(attachmentId).block().getRight();
        BlobId messageBodyBlobId = postgresMessageDAO.getBodyBlobId((PostgresMessageId) appendResult.getId().getMessageId()).block();

        inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

        assertSoftly(softly -> {
            softly.assertThatThrownBy(() -> Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), attachmentBlobId)).block())
                .isInstanceOf(ObjectNotFoundException.class);
            softly.assertThatThrownBy(() -> Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), messageBodyBlobId)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        });
    }

    @Test
    void deleteMessageListenerShouldNotDeleteReferencedBlob() throws Exception {
        assumeTrue(!(blobStore instanceof DeDuplicationBlobStore));

        MessageManager.AppendResult appendResult = inboxManager.appendMessage(MessageManager.AppendCommand.builder()
            .build(ClassLoaderUtils.getSystemResourceAsByteArray("eml/emailWithOnlyAttachment.eml")), session);
        BlobId messageBodyBlobId = postgresMessageDAO.getBodyBlobId((PostgresMessageId) appendResult.getId().getMessageId()).block();
        mailboxManager.copyMessages(MessageRange.all(), inboxManager.getId(), otherBoxManager.getId(), session);

        AttachmentId attachmentId = appendResult.getMessageAttachments().get(0).getAttachment().getAttachmentId();
        BlobId attachmentBlobId = attachmentDAO.getAttachment(attachmentId).block().getRight();

        inboxManager.delete(ImmutableList.of(appendResult.getId().getUid()), session);

        assertSoftly(softly -> {
            assertThat(Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), attachmentBlobId)).blockOptional())
                .isNotEmpty();
            assertThat(Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), messageBodyBlobId)).blockOptional())
                .isNotEmpty();
        });
    }
}
