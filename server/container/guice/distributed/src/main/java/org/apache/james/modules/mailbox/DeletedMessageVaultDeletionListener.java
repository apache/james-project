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

package org.apache.james.modules.mailbox;

import java.sql.Date;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.cassandra.DeleteMessageListener;
import org.apache.james.mailbox.events.MailboxEvents.MessageContentDeletionEvent;
import org.apache.james.vault.metadata.DeletedMessageVaultDeletionCallback;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class DeletedMessageVaultDeletionListener implements EventListener.ReactiveGroupEventListener {
    public static class DeletedMessageVaultListenerGroup extends Group {

    }

    private static final Group DELETED_MESSAGE_VAULT_DELETION_GROUP = new DeletedMessageVaultListenerGroup();

    private final DeletedMessageVaultDeletionCallback deletedMessageVaultDeletionCallback;
    private final BlobId.Factory blobIdFactory;

    @Inject
    public DeletedMessageVaultDeletionListener(DeletedMessageVaultDeletionCallback deletedMessageVaultDeletionCallback,
                                               BlobId.Factory blobIdFactory) {
        this.deletedMessageVaultDeletionCallback = deletedMessageVaultDeletionCallback;
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public Group getDefaultGroup() {
        return DELETED_MESSAGE_VAULT_DELETION_GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MessageContentDeletionEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MessageContentDeletionEvent contentDeletionEvent) {
            return deletedMessageVaultDeletionCallback.forMessage(asDeletedMessageCopyCommand(contentDeletionEvent));
        }

        return Mono.empty();
    }

    private DeleteMessageListener.DeletedMessageCopyCommand asDeletedMessageCopyCommand(MessageContentDeletionEvent messageContentDeletionEvent) {
        return new DeleteMessageListener.DeletedMessageCopyCommand(messageContentDeletionEvent.getMessageId(),
            messageContentDeletionEvent.getMailboxId(),
            messageContentDeletionEvent.getUsername(),
            Date.from(messageContentDeletionEvent.getInternalDate()),
            messageContentDeletionEvent.getSize(),
            messageContentDeletionEvent.hasAttachments(),
            blobIdFactory.parse(messageContentDeletionEvent.getHeaderBlobId()),
            blobIdFactory.parse(messageContentDeletionEvent.getBodyBlobId()));
    }
}
