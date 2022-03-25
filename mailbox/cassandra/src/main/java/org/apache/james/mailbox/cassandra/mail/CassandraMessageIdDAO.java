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

import static com.datastax.driver.core.querybuilder.QueryBuilder.addAll;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.removeAll;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.Flag.ANSWERED;
import static org.apache.james.mailbox.cassandra.table.Flag.DELETED;
import static org.apache.james.mailbox.cassandra.table.Flag.DRAFT;
import static org.apache.james.mailbox.cassandra.table.Flag.FLAGGED;
import static org.apache.james.mailbox.cassandra.table.Flag.RECENT;
import static org.apache.james.mailbox.cassandra.table.Flag.SEEN;
import static org.apache.james.mailbox.cassandra.table.Flag.USER;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ_LOWERCASE;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.THREAD_ID_LOWERCASE;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.util.streams.Limit;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraMessageIdDAO {
    private static final String IMAP_UID_GTE = IMAP_UID + "_GTE";
    private static final String IMAP_UID_LTE = IMAP_UID + "_LTE";
    public static final String LIMIT = "LIMIT_BIND_MARKER";
    private static final String ADDED_USERS_FLAGS = "added_user_flags";
    private static final String REMOVED_USERS_FLAGS = "removed_user_flags";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectAll;
    private final PreparedStatement selectAllUids;
    private final PreparedStatement selectAllLimited;
    private final PreparedStatement selectUidGte;
    private final PreparedStatement selectUidGteLimited;
    private final PreparedStatement selectUidRange;
    private final PreparedStatement selectUidRangeLimited;
    private final PreparedStatement update;
    private final PreparedStatement listStatement;

    @Inject
    public CassandraMessageIdDAO(Session session, BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
        this.update = prepareUpdate(session);
        this.select = prepareSelect(session);
        this.selectAll = prepareSelectAll(session);
        this.selectAllUids = prepareSelectAllUids(session);
        this.selectAllLimited = prepareSelectAllLimited(session);
        this.selectUidGte = prepareSelectUidGte(session);
        this.selectUidGteLimited = prepareSelectUidGteLimited(session);
        this.selectUidRange = prepareSelectUidRange(session);
        this.selectUidRangeLimited = prepareSelectUidRangeLimited(session);
        this.listStatement = prepareList(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(THREAD_ID, bindMarker(THREAD_ID)))
            .and(set(MESSAGE_ID, bindMarker(MESSAGE_ID)))
            .and(set(MOD_SEQ, bindMarker(MOD_SEQ)))
            .and(set(ANSWERED, bindMarker(ANSWERED)))
            .and(set(DELETED, bindMarker(DELETED)))
            .and(set(DRAFT, bindMarker(DRAFT)))
            .and(set(FLAGGED, bindMarker(FLAGGED)))
            .and(set(RECENT, bindMarker(RECENT)))
            .and(set(SEEN, bindMarker(SEEN)))
            .and(set(USER, bindMarker(USER)))
            .and(addAll(USER_FLAGS, bindMarker(USER_FLAGS)))
            .and(set(INTERNAL_DATE, bindMarker(INTERNAL_DATE)))
            .and(set(BODY_START_OCTET, bindMarker(BODY_START_OCTET)))
            .and(set(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS)))
            .and(set(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
                .with(set(MOD_SEQ, bindMarker(MOD_SEQ)))
                .and(set(ANSWERED, bindMarker(ANSWERED)))
                .and(set(DELETED, bindMarker(DELETED)))
                .and(set(DRAFT, bindMarker(DRAFT)))
                .and(set(FLAGGED, bindMarker(FLAGGED)))
                .and(set(RECENT, bindMarker(RECENT)))
                .and(set(SEEN, bindMarker(SEEN)))
                .and(set(USER, bindMarker(USER)))
                .and(addAll(USER_FLAGS, bindMarker(ADDED_USERS_FLAGS)))
                .and(removeAll(USER_FLAGS, bindMarker(REMOVED_USERS_FLAGS)))
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareSelectAllUids(Session session) {
        return session.prepare(select(IMAP_UID)
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareSelectAllLimited(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .limit(bindMarker(LIMIT)));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareSelectUidGte(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelectUidGteLimited(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(IMAP_UID, bindMarker(IMAP_UID)))
            .limit(bindMarker(LIMIT)));
    }

    private PreparedStatement prepareSelectUidRange(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(IMAP_UID, bindMarker(IMAP_UID_GTE)))
            .and(lte(IMAP_UID, bindMarker(IMAP_UID_LTE))));
    }

    private PreparedStatement prepareSelectUidRangeLimited(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(gte(IMAP_UID, bindMarker(IMAP_UID_GTE)))
            .and(lte(IMAP_UID, bindMarker(IMAP_UID_LTE)))
            .limit(bindMarker(LIMIT)));
    }

    public Mono<Void> delete(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    public Mono<Void> insert(CassandraMessageMetadata metadata) {
        Preconditions.checkState(metadata.isComplete(), "Attempt to write incomplete metadata");

        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatement boundStatement = insert.bind();
        if (flags.getUserFlags().length == 0) {
            boundStatement.unset(USER_FLAGS);
        } else {
            boundStatement.setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()));
        }

        return cassandraAsyncExecutor.executeVoid(boundStatement
                .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
                .setLong(IMAP_UID, composedMessageId.getUid().asLong())
                .setUUID(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
                .setUUID(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
                .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
                .setBool(ANSWERED, flags.contains(Flag.ANSWERED))
                .setBool(DELETED, flags.contains(Flag.DELETED))
                .setBool(DRAFT, flags.contains(Flag.DRAFT))
                .setBool(FLAGGED, flags.contains(Flag.FLAGGED))
                .setBool(RECENT, flags.contains(Flag.RECENT))
                .setBool(SEEN, flags.contains(Flag.SEEN))
                .setBool(USER, flags.contains(Flag.USER))
                .setTimestamp(INTERNAL_DATE, metadata.getInternalDate().get())
                .setInt(BODY_START_OCTET, Math.toIntExact(metadata.getBodyStartOctet().get()))
                .setLong(FULL_CONTENT_OCTETS, metadata.getSize().get())
                .setString(HEADER_CONTENT, metadata.getHeaderContent().get().asString()));
    }

    public Mono<Void> updateMetadata(ComposedMessageId composedMessageId, UpdatedFlags updatedFlags) {
        return cassandraAsyncExecutor.executeVoid(updateBoundStatement(composedMessageId, updatedFlags));
    }

    private BoundStatement updateBoundStatement(ComposedMessageId id, UpdatedFlags updatedFlags) {
        final BoundStatement boundStatement = update.bind()
            .setLong(MOD_SEQ, updatedFlags.getModSeq().asLong())
            .setUUID(MAILBOX_ID, ((CassandraId) id.getMailboxId()).asUuid())
            .setLong(IMAP_UID, id.getUid().asLong());

        if (updatedFlags.isChanged(Flag.ANSWERED)) {
            boundStatement.setBool(ANSWERED, updatedFlags.isModifiedToSet(Flag.ANSWERED));
        } else {
            boundStatement.unset(ANSWERED);
        }
        if (updatedFlags.isChanged(Flag.DRAFT)) {
            boundStatement.setBool(DRAFT, updatedFlags.isModifiedToSet(Flag.DRAFT));
        } else {
            boundStatement.unset(DRAFT);
        }
        if (updatedFlags.isChanged(Flag.FLAGGED)) {
            boundStatement.setBool(FLAGGED, updatedFlags.isModifiedToSet(Flag.FLAGGED));
        } else {
            boundStatement.unset(FLAGGED);
        }
        if (updatedFlags.isChanged(Flag.DELETED)) {
            boundStatement.setBool(DELETED, updatedFlags.isModifiedToSet(Flag.DELETED));
        } else {
            boundStatement.unset(DELETED);
        }
        if (updatedFlags.isChanged(Flag.RECENT)) {
            boundStatement.setBool(RECENT, updatedFlags.getNewFlags().contains(Flag.RECENT));
        } else {
            boundStatement.unset(RECENT);
        }
        if (updatedFlags.isChanged(Flag.SEEN)) {
            boundStatement.setBool(SEEN, updatedFlags.isModifiedToSet(Flag.SEEN));
        } else {
            boundStatement.unset(SEEN);
        }
        if (updatedFlags.isChanged(Flag.USER)) {
            boundStatement.setBool(USER, updatedFlags.isModifiedToSet(Flag.USER));
        } else {
            boundStatement.unset(USER);
        }
        Sets.SetView<String> removedFlags = Sets.difference(
            ImmutableSet.copyOf(updatedFlags.getOldFlags().getUserFlags()),
            ImmutableSet.copyOf(updatedFlags.getNewFlags().getUserFlags()));
        Sets.SetView<String> addedFlags = Sets.difference(
            ImmutableSet.copyOf(updatedFlags.getNewFlags().getUserFlags()),
            ImmutableSet.copyOf(updatedFlags.getOldFlags().getUserFlags()));
        if (addedFlags.isEmpty()) {
            boundStatement.unset(ADDED_USERS_FLAGS);
        } else {
            boundStatement.setSet(ADDED_USERS_FLAGS, addedFlags);
        }
        if (removedFlags.isEmpty()) {
            boundStatement.unset(REMOVED_USERS_FLAGS);
        } else {
            boundStatement.setSet(REMOVED_USERS_FLAGS, removedFlags);
        }
        return boundStatement;
    }

    public Mono<Optional<CassandraMessageMetadata>> retrieve(CassandraId mailboxId, MessageUid uid) {
        return asOptionalOfCassandraMessageId(selectOneRow(mailboxId, uid));
    }

    private Mono<Optional<CassandraMessageMetadata>> asOptionalOfCassandraMessageId(Mono<Row> row) {
        return row
                .map(this::fromRowToComposedMessageIdWithFlags)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Row> selectOneRow(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeSingleRow(select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    public Flux<CassandraMessageMetadata> retrieveMessages(CassandraId mailboxId, MessageRange set, Limit limit) {
        return retrieveRows(mailboxId, set, limit)
            .map(this::fromRowToComposedMessageIdWithFlags)
            .handle(publishIfPresent());
    }

    public Flux<MessageUid> listUids(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeRows(selectAllUids.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()))
            .map(row -> MessageUid.of(row.getLong(IMAP_UID)));
    }

    public Flux<CassandraMessageMetadata> retrieveAllMessages() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
                .setReadTimeoutMillis(Duration.ofDays(1).toMillisPart()))
            .map(this::fromRowToComposedMessageIdWithFlags)
            .handle(publishIfPresent());
    }

    private Flux<Row> retrieveRows(CassandraId mailboxId, MessageRange set, Limit limit) {
        switch (set.getType()) {
        case ALL:
            return selectAll(mailboxId, limit);
        case FROM:
            return selectFrom(mailboxId, set.getUidFrom(), limit);
        case RANGE:
            return selectRange(mailboxId, set.getUidFrom(), set.getUidTo(), limit);
        case ONE:
            return Flux.from(selectOneRow(mailboxId, set.getUidFrom()));
        }
        throw new UnsupportedOperationException();
    }

    private Flux<Row> selectAll(CassandraId mailboxId, Limit limit) {
        return cassandraAsyncExecutor.executeRows(limit.getLimit()
            .map(limitAsInt -> selectAllLimited.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setInt(LIMIT, limitAsInt))
            .orElseGet(() -> selectAll.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())));
    }

    private Flux<Row> selectFrom(CassandraId mailboxId, MessageUid uid, Limit limit) {
        return cassandraAsyncExecutor.executeRows(limit.getLimit()
            .map(limitAsInt -> selectUidGteLimited.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong())
                .setInt(LIMIT, limitAsInt))
            .orElseGet(() -> selectUidGte.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong())));
    }

    private Flux<Row> selectRange(CassandraId mailboxId, MessageUid from, MessageUid to, Limit limit) {
        return cassandraAsyncExecutor.executeRows(limit.getLimit()
            .map(limitAsInt -> selectUidRangeLimited.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID_GTE, from.asLong())
                .setLong(IMAP_UID_LTE, to.asLong())
                .setInt(LIMIT, limitAsInt))
            .orElseGet(() -> selectUidRange.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID_GTE, from.asLong())
                .setLong(IMAP_UID_LTE, to.asLong())));
    }

    private Optional<CassandraMessageMetadata> fromRowToComposedMessageIdWithFlags(Row row) {
        if (row.getUUID(MESSAGE_ID) == null) {
            // Out of order updates with concurrent deletes can result in the row being partially deleted
            // We filter out such records, and cleanup them.
            delete(CassandraId.of(row.getUUID(MAILBOX_ID)),
                MessageUid.of(row.getLong(IMAP_UID)))
                .subscribeOn(Schedulers.elastic())
                .subscribe();
            return Optional.empty();
        }
        final CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUUID(MESSAGE_ID_LOWERCASE));
        return Optional.of(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                    CassandraId.of(row.getUUID(MAILBOX_ID_LOWERCASE)),
                    messageId,
                    MessageUid.of(row.getLong(IMAP_UID))))
                .flags(FlagsExtractor.getFlags(row))
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ_LOWERCASE)))
                .threadId(getThreadIdFromRow(row, messageId))
                .build())
            .bodyStartOctet(row.getInt(BODY_START_OCTET_LOWERCASE))
            .internalDate(row.getTimestamp(INTERNAL_DATE_LOWERCASE))
            .size(row.getLong(FULL_CONTENT_OCTETS_LOWERCASE))
            .headerContent(Optional.ofNullable(row.getString(HEADER_CONTENT_LOWERCASE))
                .map(blobIdFactory::from))
            .build());
    }

    private ThreadId getThreadIdFromRow(Row row, MessageId messageId) {
        UUID threadIdUUID = row.getUUID(THREAD_ID_LOWERCASE);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(threadIdUUID));
    }
}
