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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.concurrent.Queues;

public class CassandraIndexTableHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraIndexTableHandler.class);

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
                updateFirstUnseenOnDeleteWithMetadata(mailboxId, metaData),
                updateRecentOnDeleteWithMetadata(mailboxId, metaData),
                updateDeletedMessageProjectionOnDeleteWithMetadata(mailboxId, metaData),
                decrementCountersOnDelete(mailboxId, metaData))
            .then();
    }

    public Mono<Void> updateIndexOnDeleteComposedId(CassandraId mailboxId, Collection<ComposedMessageIdWithMetaData> metaData) {
        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                updateFirstUnseenOnDelete(mailboxId, metaData),
                updateRecentOnDeleteWithComposeId(mailboxId, metaData),
                updateDeletedMessageProjectionOnDelete(mailboxId, metaData),
            decrementCountersOnDeleteFlags(mailboxId, metaData.stream()
                .map(ComposedMessageIdWithMetaData::getFlags)
                .collect(ImmutableList.toImmutableList())))
            .then();
    }

    private Mono<Void> updateRecentOnDeleteWithMetadata(CassandraId mailboxId, Collection<MessageMetaData> metaDatas) {
        return mailboxRecentDAO.removeFromRecent(mailboxId, metaDatas.stream().filter(metaData -> metaData.getFlags().contains(Flags.Flag.RECENT))
            .map(MessageMetaData::getUid)
            .collect(Collectors.toList()));
    }

    private Mono<Void> updateRecentOnDeleteWithComposeId(CassandraId mailboxId, Collection<ComposedMessageIdWithMetaData> composedMessageIdWithMetaDatas) {
        return mailboxRecentDAO.removeFromRecent(mailboxId, composedMessageIdWithMetaDatas.stream().filter(composedId -> composedId.getFlags().contains(Flags.Flag.RECENT))
            .map(composedId -> composedId.getComposedMessageId().getUid())
            .collect(Collectors.toList()));
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

    public Mono<Void> updateIndexOnAdd(Collection<MailboxMessage> messages, CassandraId mailboxId) {
        ImmutableSet<String> userFlags = messages.stream()
            .flatMap(message -> Stream.of(message.createFlags().getUserFlags()))
            .collect(ImmutableSet.toImmutableSet());
        List<Flags> flags = messages.stream()
            .flatMap(message -> Stream.of(message.createFlags()))
            .collect(ImmutableList.toImmutableList());

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                checkDeletedOnAdd(mailboxId, messages),
                updateFirstUnseenOnAdd(mailboxId, messages),
                addRecentOnSave(mailboxId, messages),
                incrementCountersOnSave(mailboxId, flags),
                applicableFlagDAO.updateApplicableFlags(mailboxId, userFlags))
            .then();
    }

    public Mono<Void> updateIndexOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        return updateIndexOnFlagsUpdate(mailboxId, ImmutableList.of(updatedFlags));
    }

    public Mono<Void> updateIndexOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags) {
        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                manageUnseenMessageCountsOnFlagsUpdate(mailboxId, updatedFlags),
                manageRecentOnFlagsUpdate(mailboxId, updatedFlags),
                updateFirstUnseenOnFlagsUpdate(mailboxId, updatedFlags),
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
            return mailboxCounterDAO.decrementCount(mailboxId)
                .onErrorResume(e -> {
                    LOGGER.error("Failed decrementing email count for {} upon delete", mailboxId.serialize());
                    return Mono.empty();
                });
        }
        return mailboxCounterDAO.decrementUnseenAndCount(mailboxId)
            .onErrorResume(e -> {
                LOGGER.error("Failed decrementing email count and seen for {} upon delete", mailboxId.serialize());
                return Mono.empty();
            });
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

        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(mailboxId)
            .count(flags.size())
            .unseen(unseenCount)
            .build();
        return mailboxCounterDAO.remove(counters)
            .onErrorResume(e -> {
                LOGGER.error("Failed decrementing counters {} upon delete", counters);
                return Mono.empty();
            });
    }

    private Mono<Void> incrementCountersOnSave(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.incrementCount(mailboxId)
                .onErrorResume(e -> {
                    LOGGER.error("Failed incrementing email count and seen for {} upon save", mailboxId.serialize());
                    return Mono.empty();
                });
        }
        return mailboxCounterDAO.incrementUnseenAndCount(mailboxId)
            .onErrorResume(e -> {
                LOGGER.error("Failed incrementing email count and seen for {} upon save", mailboxId.serialize());
                return Mono.empty();
            });
    }

    private Mono<Void> incrementCountersOnSave(CassandraId mailboxId, Collection<Flags> flags) {
        long unseenCount = flags.stream()
            .filter(flag -> !flag.contains(Flags.Flag.SEEN))
            .count();

        MailboxCounters counters = MailboxCounters.builder()
            .mailboxId(mailboxId)
            .count(flags.size())
            .unseen(unseenCount)
            .build();
        return mailboxCounterDAO.add(counters)
            .onErrorResume(e -> {
                LOGGER.error("Failed incrementing counters {} upon save", counters);
                return Mono.empty();
            });
    }

    private Mono<Void> addRecentOnSave(CassandraId mailboxId, MailboxMessage message) {
        if (message.createFlags().contains(Flags.Flag.RECENT)) {
            return mailboxRecentDAO.addToRecent(mailboxId, message.getUid());
        }
        return Mono.empty();
    }

    private Mono<Void> addRecentOnSave(CassandraId mailboxId, Collection<MailboxMessage> messages) {
        return mailboxRecentDAO.addToRecent(mailboxId, messages.stream().filter(message -> message.createFlags().contains(Flags.Flag.RECENT))
            .map(MailboxMessage::getUid)
            .collect(Collectors.toList()));
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
            MailboxCounters counters = MailboxCounters.builder()
                .mailboxId(mailboxId)
                .count(0)
                .unseen(sum)
                .build();
            return mailboxCounterDAO.add(counters)
                .onErrorResume(e -> {
                    LOGGER.error("Failed incrementing counters {} upon flags update", counters);
                    return Mono.empty();
                });
        }
        return Mono.empty();
    }

    private Mono<Void> manageRecentOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags) {
        ImmutableList.Builder<MessageUid> addRecentUidsBuilder = ImmutableList.builder();
        ImmutableList.Builder<MessageUid> removeRecentUidsBuilder = ImmutableList.builder();
        updatedFlags.forEach(flag -> {
                if (flag.isModifiedToSet(Flags.Flag.RECENT)) {
                    addRecentUidsBuilder.add(flag.getUid());
                } else if (flag.isModifiedToUnset(Flags.Flag.RECENT)) {
                    removeRecentUidsBuilder.add(flag.getUid());
                }
            });

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                mailboxRecentDAO.removeFromRecent(mailboxId, removeRecentUidsBuilder.build()),
                mailboxRecentDAO.addToRecent(mailboxId, addRecentUidsBuilder.build()))
            .then();
    }

    private Mono<Void> updateFirstUnseenOnAdd(CassandraId mailboxId, Flags flags, MessageUid uid) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return Mono.empty();
        }
        return firstUnseenDAO.addUnread(mailboxId, uid);
    }

    private Mono<Void> updateFirstUnseenOnAdd(CassandraId mailboxId, Collection<MailboxMessage> mailboxMessages) {
        return firstUnseenDAO.addUnread(mailboxId, mailboxMessages.stream().filter(mailboxMessage -> !mailboxMessage.createFlags().contains(Flags.Flag.SEEN))
            .map(MailboxMessage::getUid)
            .collect(Collectors.toList()));
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

    private Mono<Void> updateFirstUnseenOnDeleteWithMetadata(CassandraId mailboxId, Collection<MessageMetaData> metaDatas) {
        return firstUnseenDAO.removeUnread(mailboxId, metaDatas.stream().filter(metaData -> !metaData.getFlags().contains(Flags.Flag.SEEN))
            .map(MessageMetaData::getUid)
            .collect(Collectors.toList()));
    }

    private Mono<Void> updateFirstUnseenOnDelete(CassandraId mailboxId, Collection<ComposedMessageIdWithMetaData> composedMessageIdWithMetaData) {
        return firstUnseenDAO.removeUnread(mailboxId, composedMessageIdWithMetaData.stream().filter(composeId -> !composeId.getFlags().contains(Flags.Flag.SEEN))
            .map(composeId -> composeId.getComposedMessageId().getUid())
            .collect(Collectors.toList()));
    }

    private Mono<Void> updateFirstUnseenOnFlagsUpdate(CassandraId mailboxId, List<UpdatedFlags> updatedFlags) {
        ImmutableList.Builder<MessageUid> addUnreadUidsBuilder = ImmutableList.builder();
        ImmutableList.Builder<MessageUid> removeUnreadUidsBuilder = ImmutableList.builder();
        updatedFlags.forEach(flag -> {
                if (flag.isModifiedToUnset(Flags.Flag.SEEN)) {
                    addUnreadUidsBuilder.add(flag.getUid());
                } else if (flag.isModifiedToSet(Flags.Flag.SEEN)) {
                    removeUnreadUidsBuilder.add(flag.getUid());
                }
            });

        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                firstUnseenDAO.addUnread(mailboxId, addUnreadUidsBuilder.build()),
                firstUnseenDAO.removeUnread(mailboxId, removeUnreadUidsBuilder.build()))
            .then();
    }
}
