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
package org.apache.james.mailbox.store.quota;

import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.events.RegistrationKey;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.events.MailboxEvents.MetaDataHoldingEvent;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class ListeningCurrentQuotaUpdater implements EventListener.ReactiveGroupEventListener, QuotaUpdater {
    public static class ListeningCurrentQuotaUpdaterGroup extends Group {

    }

    public static final Group GROUP = new ListeningCurrentQuotaUpdaterGroup();
    private static final ImmutableSet<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();

    private final CurrentQuotaManager currentQuotaManager;
    private final QuotaRootResolver quotaRootResolver;
    private final EventBus eventBus;
    private final QuotaManager quotaManager;

    @Inject
    public ListeningCurrentQuotaUpdater(CurrentQuotaManager currentQuotaManager, QuotaRootResolver quotaRootResolver, EventBus eventBus, QuotaManager quotaManager) {
        this.currentQuotaManager = currentQuotaManager;
        this.quotaRootResolver = quotaRootResolver;
        this.eventBus = eventBus;
        this.quotaManager = quotaManager;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added || event instanceof Expunged || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Added) {
            Added addedEvent = (Added) event;
            return Mono.from(quotaRootResolver.getQuotaRootReactive(addedEvent.getMailboxPath()))
                .flatMap(quotaRoot -> handleAddedEvent(addedEvent, quotaRoot));
        } else if (event instanceof Expunged) {
            Expunged expungedEvent = (Expunged) event;
            return Mono.from(quotaRootResolver.getQuotaRootReactive(expungedEvent.getMailboxPath()))
                .flatMap(quotaRoot -> handleExpungedEvent(expungedEvent, quotaRoot));
        } else if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletionEvent = (MailboxDeletion) event;
            return handleMailboxDeletionEvent(mailboxDeletionEvent);
        }
        return Mono.empty();
    }

    private Mono<Void> handleExpungedEvent(Expunged expunged, QuotaRoot quotaRoot) {
        return computeQuotaOperation(expunged, quotaRoot)
            .flatMap(quotaOperation ->
                Mono.from(currentQuotaManager.decrease(quotaOperation))
                    .then(dispatchNewQuota(quotaRoot, expunged.getUsername())));
    }

    private Mono<Void> handleAddedEvent(Added added, QuotaRoot quotaRoot) {
        return computeQuotaOperation(added, quotaRoot)
            .flatMap(quotaOperation ->
                Mono.from(currentQuotaManager.increase(quotaOperation))
                    .then(dispatchNewQuota(quotaRoot, added.getUsername())));
    }

    private Mono<Void> dispatchNewQuota(QuotaRoot quotaRoot, Username username) {
        return Mono.from(quotaManager.getQuotasReactive(quotaRoot))
            .flatMap(quotas -> eventBus.dispatch(
                EventFactory.quotaUpdated()
                    .randomEventId()
                    .user(username)
                    .quotaRoot(quotaRoot)
                    .quotaCount(quotas.getMessageQuota())
                    .quotaSize(quotas.getStorageQuota())
                    .instant(Instant.now())
                    .build(),
                NO_REGISTRATION_KEYS));
    }

    private Mono<QuotaOperation> computeQuotaOperation(MetaDataHoldingEvent metaDataHoldingEvent, QuotaRoot quotaRoot) {
        long size = totalSize(metaDataHoldingEvent);
        long count = Integer.toUnsignedLong(metaDataHoldingEvent.getUids().size());

        if (count != 0 && size != 0) {
            return Mono.just(new QuotaOperation(quotaRoot, QuotaCountUsage.count(count), QuotaSizeUsage.size(size)));
        }
        return Mono.empty();
    }

    private long totalSize(MetaDataHoldingEvent metaDataHoldingEvent) {
        return metaDataHoldingEvent.getUids()
            .stream()
            .mapToLong(uid -> metaDataHoldingEvent.getMetaData(uid).getSize())
            .sum();
    }

    private Mono<Void> handleMailboxDeletionEvent(MailboxDeletion mailboxDeletionEvent) {
        boolean mailboxContainedMessages = mailboxDeletionEvent.getDeletedMessageCount().asLong() > 0;
        if (mailboxContainedMessages) {
            return Mono.from(currentQuotaManager.decrease(new QuotaOperation(mailboxDeletionEvent.getQuotaRoot(),
                    mailboxDeletionEvent.getDeletedMessageCount(),
                    mailboxDeletionEvent.getTotalDeletedSize())));
        }
        return Mono.empty();
    }

}