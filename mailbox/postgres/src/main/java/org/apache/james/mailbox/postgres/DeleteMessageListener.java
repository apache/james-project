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

import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.postgres.mail.MessageRepresentation;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DeleteMessageListener implements EventListener.ReactiveGroupEventListener {
    @FunctionalInterface
    public interface DeletionCallback {
        Mono<Void> forMessage(MessageRepresentation messageRepresentation, MailboxId mailboxId, Username owner);
    }

    public static class DeleteMessageListenerGroup extends Group {
    }

    public static final int LOW_CONCURRENCY = 4;

    private final BlobStore blobStore;
    private final Set<DeletionCallback> deletionCallbackList;

    private final PostgresMessageDAO.Factory messageDAOFactory;
    private final PostgresMailboxMessageDAO.Factory mailboxMessageDAOFactory;


    @Inject
    public DeleteMessageListener(BlobStore blobStore,
                                 PostgresMailboxMessageDAO.Factory mailboxMessageDAOFactory,
                                 PostgresMessageDAO.Factory messageDAOFactory,
                                 Set<DeletionCallback> deletionCallbackList) {
        this.messageDAOFactory = messageDAOFactory;
        this.mailboxMessageDAOFactory = mailboxMessageDAOFactory;
        this.blobStore = blobStore;
        this.deletionCallbackList = deletionCallbackList;
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

        return postgresMailboxMessageDAO.deleteByMailboxId((PostgresMailboxId) event.getMailboxId())
            .flatMap(msgId -> handleMessageDeletion(postgresMessageDAO, postgresMailboxMessageDAO, msgId, event.getMailboxId(), event.getMailboxPath().getUser()),
                LOW_CONCURRENCY)
            .then();
    }

    private Mono<Void> handleMessageDeletion(Expunged event) {
        PostgresMessageDAO postgresMessageDAO = messageDAOFactory.create(event.getUsername().getDomainPart());
        PostgresMailboxMessageDAO postgresMailboxMessageDAO = mailboxMessageDAOFactory.create(event.getUsername().getDomainPart());

        return Flux.fromIterable(event.getExpunged()
                .values())
            .map(MessageMetaData::getMessageId)
            .map(PostgresMessageId.class::cast)
            .flatMap(msgId -> handleMessageDeletion(postgresMessageDAO, postgresMailboxMessageDAO, msgId, event.getMailboxId(), event.getMailboxPath().getUser()), LOW_CONCURRENCY)
            .then();
    }

    private Mono<Void> handleMessageDeletion(PostgresMessageDAO postgresMessageDAO,
                                             PostgresMailboxMessageDAO postgresMailboxMessageDAO,
                                             PostgresMessageId messageId,
                                             MailboxId mailboxId, 
                                             Username owner) {
        return Mono.just(messageId)
            .filterWhen(msgId -> isUnreferenced(messageId, postgresMailboxMessageDAO))
            .flatMap(msgId -> postgresMessageDAO.retrieveMessage(messageId)
                .flatMap(executeDeletionCallbacks(mailboxId, owner))
                .then(deleteBodyBlob(msgId, postgresMessageDAO))
                .then(postgresMessageDAO.deleteByMessageId(msgId)));
    }

    private Function<MessageRepresentation, Mono<Void>> executeDeletionCallbacks(MailboxId mailboxId, Username owner) {
        return messageRepresentation -> Flux.fromIterable(deletionCallbackList)
            .concatMap(callback -> callback.forMessage(messageRepresentation, mailboxId, owner))
            .then();
    }

    private Mono<Void> deleteBodyBlob(PostgresMessageId id, PostgresMessageDAO postgresMessageDAO) {
        return postgresMessageDAO.getBodyBlobId(id)
            .flatMap(blobId -> Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId))
                .then());
    }

    private Mono<Boolean> isUnreferenced(PostgresMessageId id, PostgresMailboxMessageDAO postgresMailboxMessageDAO) {
        return postgresMailboxMessageDAO.countByMessageId(id)
            .filter(count -> count == 0)
            .map(count -> true)
            .defaultIfEmpty(false);
    }
}
