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

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.concurrent.Queues;

public class CassandraIndexTableHandler {

    private final CassandraMailboxRecentsDAO mailboxRecentDAO;
    private final CassandraMailboxCounterDAO mailboxCounterDAO;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final CassandraDeletedMessageDAO deletedMessageDAO;

    @Inject
    public CassandraIndexTableHandler(CassandraMailboxRecentsDAO mailboxRecentDAO,
                                      CassandraMailboxCounterDAO mailboxCounterDAO,
                                      CassandraFirstUnseenDAO firstUnseenDAO,
                                      CassandraApplicableFlagDAO applicableFlagDAO,
                                      CassandraDeletedMessageDAO deletedMessageDAO) {
        this.mailboxRecentDAO = mailboxRecentDAO;
        this.mailboxCounterDAO = mailboxCounterDAO;
        this.firstUnseenDAO = firstUnseenDAO;
        this.applicableFlagDAO = applicableFlagDAO;
        this.deletedMessageDAO = deletedMessageDAO;
    }

    public Mono<Void> updateIndexOnDelete(ComposedMessageIdWithMetaData composedMessageIdWithMetaData, CassandraId mailboxId) {
        MessageUid uid = composedMessageIdWithMetaData.getComposedMessageId().getUid();

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                updateFirstUnseenOnDelete(mailboxId, composedMessageIdWithMetaData.getFlags(), composedMessageIdWithMetaData.getComposedMessageId().getUid()),
                mailboxRecentDAO.removeFromRecent(mailboxId, composedMessageIdWithMetaData.getComposedMessageId().getUid()),
                updateDeletedMessageProjectionOnDelete(mailboxId, uid, composedMessageIdWithMetaData.getFlags()),
                decrementCountersOnDelete(mailboxId, composedMessageIdWithMetaData.getFlags()))
            .then();
    }

    public Mono<Void> updateIndexOnDelete(CassandraId mailboxId, Collection<MessageMetaData> metaData) {
        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                Flux.fromIterable(metaData)
                    .flatMap(message -> updateFirstUnseenOnDelete(mailboxId, message.getFlags(), message.getUid()), DEFAULT_CONCURRENCY),
                Flux.fromIterable(metaData)
                    .flatMap(message -> updateRecentOnDelete(mailboxId, message.getUid(), message.getFlags()), DEFAULT_CONCURRENCY),
                updateDeletedMessageProjectionOnDeleteWithMetadata(mailboxId, metaData),
                decrementCountersOnDelete(mailboxId, metaData))
            .then();
    }

    public Mono<Void> updateIndexOnDeleteComposedId(CassandraId mailboxId, Collection<ComposedMessageIdWithMetaData> metaData) {
        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                Flux.fromIterable(metaData)
                    .flatMap(message -> updateFirstUnseenOnDelete(mailboxId, message.getFlags(), message.getComposedMessageId().getUid()), DEFAULT_CONCURRENCY),
                Flux.fromIterable(metaData)
                    .flatMap(message -> updateRecentOnDelete(mailboxId, message.getComposedMessageId().getUid(), message.getFlags()), DEFAULT_CONCURRENCY),
                updateDeletedMessageProjectionOnDelete(mailboxId, metaData),
            decrementCountersOnDeleteFlags(mailboxId, metaData.stream()
                .map(ComposedMessageIdWithMetaData::getFlags)
                .collect(ImmutableList.toImmutableList())))
            .then();
    }

    private Mono<Void> updateRecentOnDelete(CassandraId mailboxId, MessageUid uid, Flags flags) {
        if (flags.contains(Flags.Flag.RECENT)) {
            return mailboxRecentDAO.removeFromRecent(mailboxId, uid);
        }

        return Mono.empty();
    }

    private Mono<Void> updateDeletedMessageProjectionOnDelete(CassandraId mailboxId, MessageUid uid, Flags flags) {
        if (flags.contains(Flags.Flag.DELETED)) {
            return deletedMessageDAO.removeDeleted(mailboxId, uid);
        }

        return Mono.empty();
    }

    private Mono<Void> updateDeletedMessageProjectionOnDelete(CassandraId mailboxId, Collection<ComposedMessageIdWithMetaData> metaDatas) {
        return deletedMessageDAO.removeDeleted(mailboxId, metaDatas.stream().filter(composedId -> composedId.getFlags().contains(Flags.Flag.DELETED))
            .map(composedId -> composedId.getComposedMessageId().getUid())
            .collect(Collectors.toList()));
    }

    private Mono<Void> updateDeletedMessageProjectionOnDeleteWithMetadata(CassandraId mailboxId, Collection<MessageMetaData> metaDatas) {
        return deletedMessageDAO.removeDeleted(mailboxId, metaDatas.stream().filter(metaData -> metaData.getFlags().contains(Flags.Flag.DELETED))
            .map(MessageMetaData::getUid)
            .collect(Collectors.toList()));
    }

    public Mono<Void> updateIndexOnAdd(MailboxMessage message, CassandraId mailboxId) {
        Flags flags = message.createFlags();

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                checkDeletedOnAdd(mailboxId, message.createFlags(), message.getUid()),
                updateFirstUnseenOnAdd(mailboxId, message.createFlags(), message.getUid()),
                addRecentOnSave(mailboxId, message),
                incrementCountersOnSave(mailboxId, flags),
                applicableFlagDAO.updateApplicableFlags(mailboxId, ImmutableSet.copyOf(flags.getUserFlags())))
            .then();
    }

    public Mono<Void> updateIndexOnAdd(Collection<MailboxMessage> messages, CassandraId mailboxId, int reactorConcurrency) {
        ImmutableSet<String> userFlags = messages.stream()
            .flatMap(message -> Stream.of(message.createFlags().getUserFlags()))
            .collect(ImmutableSet.toImmutableSet());
        List<Flags> flags = messages.stream()
            .flatMap(message -> Stream.of(message.createFlags()))
            .collect(ImmutableList.toImmutableList());

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                checkDeletedOnAdd(mailboxId, messages),
                Flux.fromIterable(messages)
                    .flatMap(message -> updateFirstUnseenOnAdd(mailboxId, message.createFlags(), message.getUid()), reactorConcurrency),
                Flux.fromIterable(messages)
                    .flatMap(message -> addRecentOnSave(mailboxId, message), reactorConcurrency),
                incrementCountersOnSave(mailboxId, flags),
                applicableFlagDAO.updateApplicableFlags(mailboxId, userFlags))
            .then();
    }

    public Mono<Void> updateIndexOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        int fairConcurrency = 4;
        return updateIndexOnFlagsUpdate(mailboxId, ImmutableList.of(updatedFlags), fairConcurrency);
    }

    public Mono<Void> updateIndexOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags, int reactorConcurrency) {
        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                manageUnseenMessageCountsOnFlagsUpdate(mailboxId, updatedFlags),
                manageRecentOnFlagsUpdate(mailboxId, updatedFlags, reactorConcurrency),
                updateFirstUnseenOnFlagsUpdate(mailboxId, updatedFlags, reactorConcurrency),
                manageApplicableFlagsOnFlagsUpdate(mailboxId, updatedFlags),
                updateDeletedOnFlagsUpdate(mailboxId, updatedFlags))
            .then();
    }

    private Mono<Void> manageApplicableFlagsOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags) {
        return applicableFlagDAO.updateApplicableFlags(mailboxId,
            updatedFlags.stream()
                .flatMap(UpdatedFlags::userFlagStream)
                .collect(ImmutableSet.toImmutableSet()));
    }

    private Mono<Void> updateDeletedOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags) {
        ImmutableList.Builder<MessageUid> addDeletedUidsBuilder = ImmutableList.builder();
        ImmutableList.Builder<MessageUid> removeDeletedUidsBuilder = ImmutableList.builder();
        updatedFlags.forEach(flag -> {
                if (flag.isModifiedToSet(Flags.Flag.DELETED)) {
                    addDeletedUidsBuilder.add(flag.getUid());
                } else if (flag.isModifiedToUnset(Flags.Flag.DELETED)) {
                    removeDeletedUidsBuilder.add(flag.getUid());
                }
            });

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
            deletedMessageDAO.addDeleted(mailboxId, addDeletedUidsBuilder.build()),
            deletedMessageDAO.removeDeleted(mailboxId, removeDeletedUidsBuilder.build()))
            .then();
    }

    private Mono<Void> decrementCountersOnDelete(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.decrementCount(mailboxId);
        }
        return mailboxCounterDAO.decrementUnseenAndCount(mailboxId);
    }

    private Mono<Void> decrementCountersOnDelete(CassandraId mailboxId, Collection<MessageMetaData> metaData) {
        return decrementCountersOnDeleteFlags(mailboxId, metaData.stream()
            .map(MessageMetaData::getFlags)
            .collect(ImmutableList.toImmutableList()));
    }

    private Mono<Void> decrementCountersOnDeleteFlags(CassandraId mailboxId, Collection<Flags> flags) {
        long unseenCount = flags.stream()
            .filter(flag -> !flag.contains(Flags.Flag.SEEN))
            .count();

        return mailboxCounterDAO.remove(MailboxCounters.builder()
            .mailboxId(mailboxId)
            .count(flags.size())
            .unseen(unseenCount)
            .build());
    }

    private Mono<Void> incrementCountersOnSave(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.incrementCount(mailboxId);
        }
        return mailboxCounterDAO.incrementUnseenAndCount(mailboxId);
    }

    private Mono<Void> incrementCountersOnSave(CassandraId mailboxId, Collection<Flags> flags) {
        long unseenCount = flags.stream()
            .filter(flag -> !flag.contains(Flags.Flag.SEEN))
            .count();

        return mailboxCounterDAO.add(MailboxCounters.builder()
            .mailboxId(mailboxId)
            .count(flags.size())
            .unseen(unseenCount)
            .build());
    }

    private Mono<Void> addRecentOnSave(CassandraId mailboxId, MailboxMessage message) {
        if (message.createFlags().contains(Flags.Flag.RECENT)) {
            return mailboxRecentDAO.addToRecent(mailboxId, message.getUid());
        }
        return Mono.empty();
    }

    private Mono<Void> manageUnseenMessageCountsOnFlagsUpdate(CassandraId mailboxId,  List<UpdatedFlags> updatedFlags) {
        int sum = updatedFlags.stream()
            .mapToInt(flags -> {
                if (flags.isModifiedToUnset(Flags.Flag.SEEN)) {
                    return 1;
                }
                if (flags.isModifiedToSet(Flags.Flag.SEEN)) {
                    return -1;
                }
                return 0;
            })
            .sum();

        if (sum != 0) {
            return mailboxCounterDAO.add(MailboxCounters.builder()
                .mailboxId(mailboxId)
                .count(0)
                .unseen(sum)
                .build());
        }
        return Mono.empty();
    }

    private Mono<Void> manageRecentOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags, int reactorConcurrency) {
        return Flux.fromIterable(updatedFlags)
            .flatMap(flags -> manageRecentOnFlagsUpdate(mailboxId, flags), reactorConcurrency)
            .then();
    }

    private Mono<Void> manageRecentOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        if (updatedFlags.isModifiedToUnset(Flags.Flag.RECENT)) {
            return mailboxRecentDAO.removeFromRecent(mailboxId, updatedFlags.getUid());
        }
        if (updatedFlags.isModifiedToSet(Flags.Flag.RECENT)) {
            return mailboxRecentDAO.addToRecent(mailboxId, updatedFlags.getUid());
        }
        return Mono.empty();
    }

    private Mono<Void> updateFirstUnseenOnAdd(CassandraId mailboxId, Flags flags, MessageUid uid) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return Mono.empty();
        }
        return firstUnseenDAO.addUnread(mailboxId, uid);
    }

    private Mono<Void> checkDeletedOnAdd(CassandraId mailboxId, Flags flags, MessageUid uid) {
        if (flags.contains(Flags.Flag.DELETED)) {
            return deletedMessageDAO.addDeleted(mailboxId, uid);
        }

        return Mono.empty();
    }

    private Mono<Void> checkDeletedOnAdd(CassandraId mailboxId, Collection<MailboxMessage> mailboxMessages) {
        return deletedMessageDAO.addDeleted(mailboxId, mailboxMessages.stream().filter(message -> message.createFlags().contains(Flags.Flag.DELETED))
            .map(MailboxMessage::getUid)
            .collect(Collectors.toList()));
    }

    private Mono<Void> updateFirstUnseenOnDelete(CassandraId mailboxId, Flags flags, MessageUid uid) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return Mono.empty();
        }
        return firstUnseenDAO.removeUnread(mailboxId, uid);
    }

    private Mono<Void> updateFirstUnseenOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags, int reactorConcurrency) {
        return Flux.fromIterable(updatedFlags)
            .flatMap(flags -> updateFirstUnseenOnFlagsUpdate(mailboxId, flags), reactorConcurrency)
            .then();
    }

    private Mono<Void> updateFirstUnseenOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        if (updatedFlags.isModifiedToUnset(Flags.Flag.SEEN)) {
            return firstUnseenDAO.addUnread(mailboxId, updatedFlags.getUid());
        }
        if (updatedFlags.isModifiedToSet(Flags.Flag.SEEN)) {
            return firstUnseenDAO.removeUnread(mailboxId, updatedFlags.getUid());
        }
        return Mono.empty();
    }
}
