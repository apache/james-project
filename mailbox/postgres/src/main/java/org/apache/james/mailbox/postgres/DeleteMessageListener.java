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

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.postgres.mail.MessageRepresentation;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DeleteMessageListener implements EventListener.ReactiveGroupEventListener {
    public static class DeleteMessageListenerGroup extends Group {
    }

    public static final int LOW_CONCURRENCY = 4;
    public static final String CONTENT_DELETION = "contentDeletion";

    private final BlobStore blobStore;
    private final PostgresMessageDAO.Factory messageDAOFactory;
    private final PostgresMailboxMessageDAO.Factory mailboxMessageDAOFactory;
    private final PostgresAttachmentDAO.Factory attachmentDAOFactory;
    private final PostgresThreadDAO.Factory threadDAOFactory;
    private final EventBus contentDeletionEventBus;

    @Inject
    public DeleteMessageListener(BlobStore blobStore,
                                 PostgresMailboxMessageDAO.Factory mailboxMessageDAOFactory,
                                 PostgresMessageDAO.Factory messageDAOFactory,
                                 PostgresAttachmentDAO.Factory attachmentDAOFactory,
                                 PostgresThreadDAO.Factory threadDAOFactory,
                                 @Named(CONTENT_DELETION) EventBus contentDeletionEventBus) {
        this.messageDAOFactory = messageDAOFactory;
        this.mailboxMessageDAOFactory = mailboxMessageDAOFactory;
        this.blobStore = blobStore;
        this.attachmentDAOFactory = attachmentDAOFactory;
        this.threadDAOFactory = threadDAOFactory;
        this.contentDeletionEventBus = contentDeletionEventBus;
    }

    @Override
    public Group getDefaultGroup() {
        return new DeleteMessageListenerGroup();
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Expunged || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Expunged) {
            Expunged expunged = (Expunged) event;
            return handleMessageDeletion(expunged);
        }
        if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletion = (MailboxDeletion) event;
            return handleMailboxDeletion(mailboxDeletion);
        }
        return Mono.empty();
    }

    private Mono<Void> handleMailboxDeletion(MailboxDeletion event) {
        PostgresMessageDAO postgresMessageDAO = messageDAOFactory.create(event.getUsername().getDomainPart());
        PostgresMailboxMessageDAO postgresMailboxMessageDAO = mailboxMessageDAOFactory.create(event.getUsername().getDomainPart());
        PostgresAttachmentDAO attachmentDAO = attachmentDAOFactory.create(event.getUsername().getDomainPart());
        PostgresThreadDAO threadDAO = threadDAOFactory.create(event.getUsername().getDomainPart());

        return postgresMailboxMessageDAO.deleteByMailboxId((PostgresMailboxId) event.getMailboxId())
            .flatMap(msgId -> handleMessageDeletion(postgresMessageDAO, postgresMailboxMessageDAO, attachmentDAO, threadDAO, msgId, event.getMailboxId(), event.getMailboxPath().getUser()),
                LOW_CONCURRENCY)
            .then();
    }

    private Mono<Void> handleMessageDeletion(Expunged event) {
        PostgresMessageDAO postgresMessageDAO = messageDAOFactory.create(event.getUsername().getDomainPart());
        PostgresMailboxMessageDAO postgresMailboxMessageDAO = mailboxMessageDAOFactory.create(event.getUsername().getDomainPart());
        PostgresAttachmentDAO attachmentDAO = attachmentDAOFactory.create(event.getUsername().getDomainPart());
        PostgresThreadDAO threadDAO = threadDAOFactory.create(event.getUsername().getDomainPart());

        return Flux.fromIterable(event.getExpunged()
                .values())
            .map(MessageMetaData::getMessageId)
            .map(PostgresMessageId.class::cast)
            .flatMap(msgId -> handleMessageDeletion(postgresMessageDAO, postgresMailboxMessageDAO, attachmentDAO, threadDAO, msgId, event.getMailboxId(), event.getMailboxPath().getUser()), LOW_CONCURRENCY)
            .then();
    }

    private Mono<Void> handleMessageDeletion(PostgresMessageDAO postgresMessageDAO,
                                             PostgresMailboxMessageDAO postgresMailboxMessageDAO,
                                             PostgresAttachmentDAO attachmentDAO,
                                             PostgresThreadDAO threadDAO,
                                             PostgresMessageId messageId,
                                             MailboxId mailboxId,
                                             Username owner) {
        return Mono.just(messageId)
            .filterWhen(msgId -> isUnreferenced(msgId, postgresMailboxMessageDAO))
            .flatMap(msgId -> postgresMessageDAO.retrieveMessage(messageId)
                .flatMap(messageRepresentation -> dispatchMessageContentDeletionEvent(mailboxId, owner, messageRepresentation))
                .then(deleteBodyBlob(msgId, postgresMessageDAO))
                .then(deleteAttachment(msgId, attachmentDAO))
                .then(threadDAO.deleteSome(owner, msgId))
                .then(postgresMessageDAO.deleteByMessageId(msgId)));
    }

    private Mono<Void> dispatchMessageContentDeletionEvent(MailboxId mailboxId, Username owner, MessageRepresentation message) {
        return Mono.fromCallable(() -> IOUtils.toString(message.getHeaderContent().getInputStream(), StandardCharsets.UTF_8))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .flatMap(headerContent -> Mono.from(contentDeletionEventBus.dispatch(messageContentDeletionEvent(mailboxId, owner, message, headerContent),
                ImmutableSet.of())));
    }

    private MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent(MailboxId mailboxId, Username owner, MessageRepresentation message, String headerContent) {
        return EventFactory.messageContentDeleted()
            .randomEventId()
            .user(owner)
            .mailboxId(mailboxId)
            .messageId(message.getMessageId())
            .size(message.getSize())
            .instant(message.getInternalDate().toInstant())
            .hasAttachments(!message.getAttachments().isEmpty())
            .bodyBlobId(message.getBodyBlobId().asString())
            .headerContent(headerContent)
            .build();
    }

    private Mono<Void> deleteBodyBlob(PostgresMessageId id, PostgresMessageDAO postgresMessageDAO) {
        return postgresMessageDAO.getBodyBlobId(id)
            .flatMap(blobId -> Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId))
                .then());
    }

    private Mono<Boolean> isUnreferenced(PostgresMessageId id, PostgresMailboxMessageDAO postgresMailboxMessageDAO) {
        return postgresMailboxMessageDAO.existsByMessageId(id)
            .map(FunctionalUtils.negate());
    }

    private Mono<Void> deleteAttachment(PostgresMessageId messageId, PostgresAttachmentDAO attachmentDAO) {
        return deleteAttachmentBlobs(messageId, attachmentDAO)
            .then(attachmentDAO.deleteByMessageId(messageId));
    }

    private Mono<Void> deleteAttachmentBlobs(PostgresMessageId messageId, PostgresAttachmentDAO attachmentDAO) {
        return attachmentDAO.listBlobsByMessageId(messageId)
            .flatMap(blobId -> Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId)), ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }
}
