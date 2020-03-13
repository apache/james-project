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

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

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
                deletedMessageDAO.removeDeleted(mailboxId, uid),
                decrementCountersOnDelete(mailboxId, composedMessageIdWithMetaData.getFlags()))
            .then();
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

    public Mono<Void> updateIndexOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        return Flux.mergeDelayError(Queues.XS_BUFFER_SIZE,
                manageUnseenMessageCountsOnFlagsUpdate(mailboxId, updatedFlags),
                manageRecentOnFlagsUpdate(mailboxId, updatedFlags),
                updateFirstUnseenOnFlagsUpdate(mailboxId, updatedFlags),
                applicableFlagDAO.updateApplicableFlags(mailboxId, ImmutableSet.copyOf(updatedFlags.userFlagIterator())),
                updateDeletedOnFlagsUpdate(mailboxId, updatedFlags))
            .then();
    }

    private Mono<Void> updateDeletedOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        if (updatedFlags.isModifiedToSet(Flags.Flag.DELETED)) {
            return deletedMessageDAO.addDeleted(mailboxId, updatedFlags.getUid());
        } else if (updatedFlags.isModifiedToUnset(Flags.Flag.DELETED)) {
            return deletedMessageDAO.removeDeleted(mailboxId, updatedFlags.getUid());
        } else {
            return Mono.empty();
        }
    }

    private Mono<Void> decrementCountersOnDelete(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.decrementCount(mailboxId);
        }
        return mailboxCounterDAO.decrementUnseenAndCount(mailboxId);
    }

    private Mono<Void> incrementCountersOnSave(CassandraId mailboxId, Flags flags) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.incrementCount(mailboxId);
        }
        return mailboxCounterDAO.incrementUnseenAndCount(mailboxId);
    }

    private Mono<Void> addRecentOnSave(CassandraId mailboxId, MailboxMessage message) {
        if (message.createFlags().contains(Flags.Flag.RECENT)) {
            return mailboxRecentDAO.addToRecent(mailboxId, message.getUid());
        }
        return Mono.empty();
    }

    private Mono<Void> manageUnseenMessageCountsOnFlagsUpdate(CassandraId mailboxId, UpdatedFlags updatedFlags) {
        if (updatedFlags.isModifiedToUnset(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.incrementUnseen(mailboxId);
        }
        if (updatedFlags.isModifiedToSet(Flags.Flag.SEEN)) {
            return mailboxCounterDAO.decrementUnseen(mailboxId);
        }
        return Mono.empty();
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

    private Mono<Void> updateFirstUnseenOnDelete(CassandraId mailboxId, Flags flags, MessageUid uid) {
        if (flags.contains(Flags.Flag.SEEN)) {
            return Mono.empty();
        }
        return firstUnseenDAO.removeUnread(mailboxId, uid);
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
