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

import javax.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAO;
import org.apache.james.mailbox.postgres.mail.dao.PostgresMessageDAO;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DeleteMessageListener implements EventListener.ReactiveGroupEventListener {
    public static class DeleteMessageListenerGroup extends Group {
    }

    private final PostgresMessageDAO postgresMessageDAO;
    private final PostgresMailboxMessageDAO postgresMailboxMessageDAO;
    private final BlobStore blobStore;

    @Inject
    public DeleteMessageListener(PostgresMessageDAO postgresMessageDAO,
                                 PostgresMailboxMessageDAO postgresMailboxMessageDAO,
                                 BlobStore blobStore) {
        this.postgresMessageDAO = postgresMessageDAO;
        this.postgresMailboxMessageDAO = postgresMailboxMessageDAO;
        this.blobStore = blobStore;
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
            PostgresMailboxId mailboxId = (PostgresMailboxId) mailboxDeletion.getMailboxId();
            return handleMailboxDeletion(mailboxId);
        }
        return Mono.empty();
    }

    private Mono<Void> handleMailboxDeletion(PostgresMailboxId mailboxId) {
        return postgresMailboxMessageDAO.deleteByMailboxId(mailboxId)
            .flatMap(this::handleMessageDeletion)
            .then();
    }

    private Mono<Void> handleMessageDeletion(Expunged expunged) {
        return Flux.fromIterable(expunged.getExpunged()
            .values())
            .map(MessageMetaData::getMessageId)
            .map(PostgresMessageId.class::cast)
            .flatMap(this::handleMessageDeletion)
            .then();
    }

    private Mono<Void> handleMessageDeletion(PostgresMessageId messageId) {
        return Mono.just(messageId)
            .filterWhen(this::isUnreferenced)
            .flatMap(id -> postgresMessageDAO.getBlobId(messageId)
                .flatMap(this::deleteMessageBlobs)
                .then(postgresMessageDAO.deleteByMessageId(messageId)));
    }

    private Mono<Void> deleteMessageBlobs(BlobId blobId) {
        return Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId))
            .then();
    }

    private Mono<Boolean> isUnreferenced(PostgresMessageId id) {
        return postgresMailboxMessageDAO.countByMessageId(id)
            .filter(count -> count == 0)
            .map(count -> true)
            .defaultIfEmpty(false);
    }
}
