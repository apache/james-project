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
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.events.RegistrationKey;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class ListeningCurrentQuotaUpdater implements MailboxListener.GroupMailboxListener, QuotaUpdater {
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
    public void event(Event event) throws MailboxException {
            if (event instanceof Added) {
                Added addedEvent = (Added) event;
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(addedEvent.getMailboxId());
                handleAddedEvent(addedEvent, quotaRoot);
            } else if (event instanceof Expunged) {
                Expunged expungedEvent = (Expunged) event;
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(expungedEvent.getMailboxId());
                handleExpungedEvent(expungedEvent, quotaRoot);
            } else if (event instanceof MailboxDeletion) {
                MailboxDeletion mailboxDeletionEvent = (MailboxDeletion) event;
                handleMailboxDeletionEvent(mailboxDeletionEvent);
            }
    }

    private void handleExpungedEvent(Expunged expunged, QuotaRoot quotaRoot) {
        computeQuotaOperation(expunged, quotaRoot).ifPresent(Throwing.<QuotaOperation>consumer(quotaOperation -> {
            Mono.from(currentQuotaManager.decrease(quotaOperation))
                .then(Mono.defer(Throwing.supplier(() -> eventBus.dispatch(
                    EventFactory.quotaUpdated()
                        .randomEventId()
                        .user(expunged.getUsername())
                        .quotaRoot(quotaRoot)
                        .quotaCount(quotaManager.getMessageQuota(quotaRoot))
                        .quotaSize(quotaManager.getStorageQuota(quotaRoot))
                        .instant(Instant.now())
                        .build(),
                    NO_REGISTRATION_KEYS)).sneakyThrow()))
                .block();
        }).sneakyThrow());
    }

    private void handleAddedEvent(Added added, QuotaRoot quotaRoot) {
        computeQuotaOperation(added, quotaRoot).ifPresent(Throwing.<QuotaOperation>consumer(quotaOperation -> {
            Mono.from(currentQuotaManager.increase(quotaOperation))
                .then(Mono.defer(Throwing.supplier(() -> eventBus.dispatch(
                    EventFactory.quotaUpdated()
                        .randomEventId()
                        .user(added.getUsername())
                        .quotaRoot(quotaRoot)
                        .quotaCount(quotaManager.getMessageQuota(quotaRoot))
                        .quotaSize(quotaManager.getStorageQuota(quotaRoot))
                        .instant(Instant.now())
                        .build(),
                    NO_REGISTRATION_KEYS)).sneakyThrow()))
                .block();
        }).sneakyThrow());
    }

    private Optional<QuotaOperation> computeQuotaOperation(MetaDataHoldingEvent metaDataHoldingEvent, QuotaRoot quotaRoot) {
        long size = totalSize(metaDataHoldingEvent);
        long count = Integer.toUnsignedLong(metaDataHoldingEvent.getUids().size());

        if (count != 0 && size != 0) {
            return Optional.of(new QuotaOperation(quotaRoot, QuotaCountUsage.count(count), QuotaSizeUsage.size(size)));
        }
        return Optional.empty();
    }

    private long totalSize(MetaDataHoldingEvent metaDataHoldingEvent) {
        return metaDataHoldingEvent.getUids()
            .stream()
            .mapToLong(uid -> metaDataHoldingEvent.getMetaData(uid).getSize())
            .sum();
    }

    private void handleMailboxDeletionEvent(MailboxDeletion mailboxDeletionEvent) throws MailboxException {
        boolean mailboxContainedMessages = mailboxDeletionEvent.getDeletedMessageCount().asLong() > 0;
        if (mailboxContainedMessages) {
            Mono.from(currentQuotaManager.decrease(new QuotaOperation(mailboxDeletionEvent.getQuotaRoot(),
                    mailboxDeletionEvent.getDeletedMessageCount(),
                    mailboxDeletionEvent.getTotalDeletedSize())))
                .block();
        }
    }

}