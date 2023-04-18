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

package org.apache.james.jmap.memory.projections;

import java.time.ZonedDateTime;
import java.util.Comparator;

import javax.inject.Inject;

import org.apache.james.jmap.api.projections.EmailQueryView;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.util.streams.Limit;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryEmailQueryView implements EmailQueryView {
    private final Table<MailboxId, MessageId, Entry> entries;

    @Inject
    public MemoryEmailQueryView() {
        entries = Tables.synchronizedTable(HashBasedTable.create());
    }

    @Override
    public Flux<MessageId> listMailboxContentSortedBySentAt(MailboxId mailboxId, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return Flux.fromIterable(entries.row(mailboxId).values())
            .sort(Comparator.comparing(Entry::getSentAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceSentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return Flux.fromIterable(entries.row(mailboxId).values())
            .filter(e -> e.getSentAt().isAfter(since) || e.getSentAt().isEqual(since))
            .sort(Comparator.comparing(Entry::getSentAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceAfterSortedBySentAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        Preconditions.checkArgument(!limit.isUnlimited(), "Limit should be defined");

        return Flux.fromIterable(entries.row(mailboxId).values())
            .filter(e -> e.getReceivedAt().isAfter(since) || e.getReceivedAt().isEqual(since))
            .sort(Comparator.comparing(Entry::getSentAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMailboxContentSortedByReceivedAt(MailboxId mailboxId, Limit limit) {
        return Flux.fromIterable(entries.row(mailboxId).values())
            .sort(Comparator.comparing(Entry::getReceivedAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMailboxContentSinceAfterSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime since, Limit limit) {
        return Flux.fromIterable(entries.row(mailboxId).values())
            .filter(e -> e.getReceivedAt().isAfter(since) || e.getReceivedAt().isEqual(since))
            .sort(Comparator.comparing(Entry::getReceivedAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Flux<MessageId> listMailboxContentBeforeSortedByReceivedAt(MailboxId mailboxId, ZonedDateTime before, Limit limit) {
        return Flux.fromIterable(entries.row(mailboxId).values())
            .filter(e -> e.getReceivedAt().isBefore(before) || e.getReceivedAt().isEqual(before))
            .sort(Comparator.comparing(Entry::getReceivedAt).reversed())
            .map(Entry::getMessageId)
            .take(limit.getLimit().get());
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId, MessageId messageId) {
        return Mono.fromRunnable(() -> entries.remove(mailboxId, messageId));
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        return Mono.fromRunnable(() -> entries.row(mailboxId).clear());
    }

    @Override
    public Mono<Void> save(MailboxId mailboxId, ZonedDateTime sentAt, ZonedDateTime receivedAt, MessageId messageId) {
        return Mono.fromRunnable(() -> entries.put(mailboxId, messageId, new Entry(mailboxId, messageId, sentAt, receivedAt)));
    }
}
