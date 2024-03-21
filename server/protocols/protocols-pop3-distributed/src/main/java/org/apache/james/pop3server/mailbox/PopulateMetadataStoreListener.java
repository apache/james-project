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

package org.apache.james.pop3server.mailbox;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener.ReactiveGroupEventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MessageMetaData;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PopulateMetadataStoreListener implements ReactiveGroupEventListener {
    public static class PopulateMetadataStoreListenerGroup extends Group {

    }

    static final Group GROUP = new PopulateMetadataStoreListenerGroup();
    private static final int CONCURRENCY = 5;

    private final Pop3MetadataStore metadataStore;

    @Inject
    public PopulateMetadataStoreListener(Pop3MetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added
            || event instanceof Expunged
            || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Added) {
            return handleAdded((Added) event);
        }
        if (event instanceof Expunged) {
            return handleExpunged((Expunged) event);
        }
        if (event instanceof MailboxDeletion) {
            return handleMailboxDeletion((MailboxDeletion) event);
        }
        return Mono.empty();
    }

    private Publisher<Void> handleMailboxDeletion(MailboxDeletion mailboxDeletion) {
        return metadataStore.clear(mailboxDeletion.getMailboxId());
    }

    private Publisher<Void> handleExpunged(Expunged expunged) {
        return Flux.fromStream(expunged.getUids().stream()
            .map(expunged::getMetaData))
            .concatMap(message -> metadataStore.remove(expunged.getMailboxId(), message.getMessageId()))
            .then();
    }

    private Mono<Void> handleAdded(Added added) {
        return Flux.fromStream(added.getUids().stream()
            .map(added::getMetaData))
            .flatMap(messageMetaData -> handleAdded(added, messageMetaData), CONCURRENCY)
            .then();
    }

    private Mono<Void> handleAdded(Added added, MessageMetaData messageMetaData) {
        return Mono.from(metadataStore.add(added.getMailboxId(), new Pop3MetadataStore.StatMetadata(messageMetaData.getMessageId(), messageMetaData.getSize())));
    }
}
