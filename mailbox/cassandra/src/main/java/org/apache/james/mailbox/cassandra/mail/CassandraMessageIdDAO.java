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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.append;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.remove;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.setColumn;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.SAVE_DATE;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.BODY_START_OCTET;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.FULL_CONTENT_OCTETS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.HEADER_CONTENT;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageV3Table.INTERNAL_DATE;
import static org.apache.james.mailbox.cassandra.table.Flag.ANSWERED;
import static org.apache.james.mailbox.cassandra.table.Flag.DELETED;
import static org.apache.james.mailbox.cassandra.table.Flag.DRAFT;
import static org.apache.james.mailbox.cassandra.table.Flag.FLAGGED;
import static org.apache.james.mailbox.cassandra.table.Flag.RECENT;
import static org.apache.james.mailbox.cassandra.table.Flag.SEEN;
import static org.apache.james.mailbox.cassandra.table.Flag.USER;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CassandraMessageIdDAO {
    private static class MemoizedSupplier<T> {
        private final AtomicReference<T> value = new AtomicReference<>();

        T get(Supplier<T> initializer) {
            T result = value.get();
            if (result == null) {
                T initialValue = initializer.get();
                value.set(initialValue);
                return initialValue;
            }
            return result;
        }
    }

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
    private final PreparedStatement selectUidOnlyRange;
    private final PreparedStatement selectMetadataRange;
    private final PreparedStatement selectNotDeletedRange;
    private final PreparedStatement selectUidRangeLimited;
    private final PreparedStatement update;
    private final PreparedStatement listStatement;
    private final ProtocolVersion protocolVersion;

    @Inject
    public CassandraMessageIdDAO(CqlSession session, BlobId.Factory blobIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.protocolVersion = session.getContext().getProtocolVersion();

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
        this.selectUidOnlyRange = prepareSelectUidOnlyRange(session);
        this.selectUidRangeLimited = prepareSelectUidRangeLimited(session);
        this.listStatement = prepareList(session);
        this.selectMetadataRange = prepareSelectMetadataRange(session);
        this.selectNotDeletedRange = prepareSelectNotDeletedRange(session);
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(update(TABLE_NAME)
            .set(setColumn(THREAD_ID, bindMarker(THREAD_ID)),
                setColumn(MESSAGE_ID, bindMarker(MESSAGE_ID)),
                setColumn(MOD_SEQ, bindMarker(MOD_SEQ)),
                setColumn(ANSWERED, bindMarker(ANSWERED)),
                setColumn(DELETED, bindMarker(DELETED)),
                setColumn(DRAFT, bindMarker(DRAFT)),
                setColumn(FLAGGED, bindMarker(FLAGGED)),
                setColumn(RECENT, bindMarker(RECENT)),
                setColumn(SEEN, bindMarker(SEEN)),
                setColumn(USER, bindMarker(USER)),
                setColumn(INTERNAL_DATE, bindMarker(INTERNAL_DATE)),
                setColumn(SAVE_DATE, bindMarker(SAVE_DATE)),
                setColumn(BODY_START_OCTET, bindMarker(BODY_START_OCTET)),
                setColumn(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS)),
                setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)),
                append(USER_FLAGS, bindMarker(USER_FLAGS)))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());
    }

    private PreparedStatement prepareUpdate(CqlSession session) {
        return session.prepare(update(TABLE_NAME)
            .set(setColumn(MOD_SEQ, bindMarker(MOD_SEQ)),
                setColumn(ANSWERED, bindMarker(ANSWERED)),
                setColumn(DELETED, bindMarker(DELETED)),
                setColumn(DRAFT, bindMarker(DRAFT)),
                setColumn(FLAGGED, bindMarker(FLAGGED)),
                setColumn(RECENT, bindMarker(RECENT)),
                setColumn(SEEN, bindMarker(SEEN)),
                setColumn(USER, bindMarker(USER)),
                append(USER_FLAGS, bindMarker(ADDED_USERS_FLAGS)),
                remove(USER_FLAGS, bindMarker(REMOVED_USERS_FLAGS)))
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
            .build());
    }

    private PreparedStatement prepareSelectAll(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareSelectAllUids(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .column(IMAP_UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareSelectAllLimited(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .limit(bindMarker(LIMIT))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareList(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all().build());
    }

    private PreparedStatement prepareSelectUidGte(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID)))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareSelectUidGteLimited(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID)))
            .limit(bindMarker(LIMIT))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareSelectUidRange(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID_GTE)),
                column(IMAP_UID).isLessThanOrEqualTo(bindMarker(IMAP_UID_LTE)))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareSelectUidOnlyRange(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .column(IMAP_UID)
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID_GTE)),
                column(IMAP_UID).isLessThanOrEqualTo(bindMarker(IMAP_UID_LTE)))
            .orderBy(IMAP_UID, ClusteringOrder.ASC)
            .build());
    }

    private PreparedStatement prepareSelectMetadataRange(CqlSession session) {
        return session.prepare(
            QueryBuilder.selectFrom(TABLE_NAME)
                .columns(IMAP_UID,
                    MESSAGE_ID,
                    THREAD_ID,
                    ANSWERED,
                    DELETED,
                    DRAFT,
                    RECENT,
                    SEEN,
                    FLAGGED,
                    USER,
                    USER_FLAGS,
                    MOD_SEQ)
                .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                    column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID_GTE)),
                    column(IMAP_UID).isLessThanOrEqualTo(bindMarker(IMAP_UID_LTE)))
                .build());
    }

    private PreparedStatement prepareSelectNotDeletedRange(CqlSession session) {
        return session.prepare(
            QueryBuilder.selectFrom(TABLE_NAME)
                .columns(IMAP_UID,
                    DELETED)
                .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                    column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID_GTE)),
                    column(IMAP_UID).isLessThanOrEqualTo(bindMarker(IMAP_UID_LTE)))
                .build());
    }

    private PreparedStatement prepareSelectUidRangeLimited(CqlSession session) {
        return session.prepare(QueryBuilder.selectFrom(TABLE_NAME)
            .all()
            .where(column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isGreaterThanOrEqualTo(bindMarker(IMAP_UID_GTE)),
                column(IMAP_UID).isLessThanOrEqualTo(bindMarker(IMAP_UID_LTE)))
            .limit(bindMarker(LIMIT))
            .build());
    }

    public Mono<Void> delete(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
            .setLong(IMAP_UID, uid.asLong()));
    }

    public Mono<Void> insert(CassandraMessageMetadata metadata) {
        Preconditions.checkState(metadata.isComplete(), "Attempt to write incomplete metadata");

        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatementBuilder statementBuilder = insert.boundStatementBuilder();
        if (flags.getUserFlags().length == 0) {
            statementBuilder.unset(USER_FLAGS);
        } else {
            statementBuilder.setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class);
        }
        return cassandraAsyncExecutor.executeVoid(statementBuilder
            .set(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid(), TypeCodecs.TIMEUUID)
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setUuid(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
            .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setBoolean(ANSWERED, flags.contains(Flag.ANSWERED))
            .setBoolean(DELETED, flags.contains(Flag.DELETED))
            .setBoolean(DRAFT, flags.contains(Flag.DRAFT))
            .setBoolean(FLAGGED, flags.contains(Flag.FLAGGED))
            .setBoolean(RECENT, flags.contains(Flag.RECENT))
            .setBoolean(SEEN, flags.contains(Flag.SEEN))
            .setBoolean(USER, flags.contains(Flag.USER))
            .setInstant(INTERNAL_DATE, metadata.getInternalDate().get().toInstant())
            .setInstant(SAVE_DATE, metadata.getSaveDate().map(Date::toInstant).orElse(null))
            .setInt(BODY_START_OCTET, Math.toIntExact(metadata.getBodyStartOctet().get()))
            .setLong(FULL_CONTENT_OCTETS, metadata.getSize().get())
            .setString(HEADER_CONTENT, metadata.getHeaderContent().get().asString())
            .build());
    }

    public Mono<Void> updateMetadata(ComposedMessageId composedMessageId, UpdatedFlags updatedFlags) {
        return cassandraAsyncExecutor.executeVoid(updateBoundStatement(composedMessageId, updatedFlags));
    }

    private BoundStatement updateBoundStatement(ComposedMessageId id, UpdatedFlags updatedFlags) {
        final BoundStatementBuilder statementBuilder = update.boundStatementBuilder()
            .setLong(MOD_SEQ, updatedFlags.getModSeq().asLong())
            .setUuid(MAILBOX_ID, ((CassandraId) id.getMailboxId()).asUuid())
            .setLong(IMAP_UID, id.getUid().asLong());

        if (updatedFlags.isChanged(Flag.ANSWERED)) {
            statementBuilder.setBoolean(ANSWERED, updatedFlags.isModifiedToSet(Flag.ANSWERED));
        } else {
            statementBuilder.unset(ANSWERED);
        }
        if (updatedFlags.isChanged(Flag.DRAFT)) {
            statementBuilder.setBoolean(DRAFT, updatedFlags.isModifiedToSet(Flag.DRAFT));
        } else {
            statementBuilder.unset(DRAFT);
        }
        if (updatedFlags.isChanged(Flag.FLAGGED)) {
            statementBuilder.setBoolean(FLAGGED, updatedFlags.isModifiedToSet(Flag.FLAGGED));
        } else {
            statementBuilder.unset(FLAGGED);
        }
        if (updatedFlags.isChanged(Flag.DELETED)) {
            statementBuilder.setBoolean(DELETED, updatedFlags.isModifiedToSet(Flag.DELETED));
        } else {
            statementBuilder.unset(DELETED);
        }
        if (updatedFlags.isChanged(Flag.RECENT)) {
            statementBuilder.setBoolean(RECENT, updatedFlags.getNewFlags().contains(Flag.RECENT));
        } else {
            statementBuilder.unset(RECENT);
        }
        if (updatedFlags.isChanged(Flag.SEEN)) {
            statementBuilder.setBoolean(SEEN, updatedFlags.isModifiedToSet(Flag.SEEN));
        } else {
            statementBuilder.unset(SEEN);
        }
        if (updatedFlags.isChanged(Flag.USER)) {
            statementBuilder.setBoolean(USER, updatedFlags.isModifiedToSet(Flag.USER));
        } else {
            statementBuilder.unset(USER);
        }
        Sets.SetView<String> removedFlags = Sets.difference(
            ImmutableSet.copyOf(updatedFlags.getOldFlags().getUserFlags()),
            ImmutableSet.copyOf(updatedFlags.getNewFlags().getUserFlags()));
        Sets.SetView<String> addedFlags = Sets.difference(
            ImmutableSet.copyOf(updatedFlags.getNewFlags().getUserFlags()),
            ImmutableSet.copyOf(updatedFlags.getOldFlags().getUserFlags()));
        if (addedFlags.isEmpty()) {
            statementBuilder.unset(ADDED_USERS_FLAGS);
        } else {
            statementBuilder.setSet(ADDED_USERS_FLAGS, addedFlags, String.class);
        }
        if (removedFlags.isEmpty()) {
            statementBuilder.unset(REMOVED_USERS_FLAGS);
        } else {
            statementBuilder.setSet(REMOVED_USERS_FLAGS, removedFlags, String.class);
        }
        return statementBuilder.build();
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
            .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
            .setLong(IMAP_UID, uid.asLong()));
    }

    public Flux<CassandraMessageMetadata> retrieveMessages(CassandraId mailboxId, MessageRange set, Limit limit) {
        return retrieveRows(mailboxId, set, limit)
            .map(this::fromRowToComposedMessageIdWithFlags)
            .handle(publishIfPresent());
    }

    public Flux<MessageUid> listUids(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeRows(selectAllUids.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID))
            .map(row -> MessageUid.of(TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(0), protocolVersion)));
    }

    public Flux<ComposedMessageIdWithMetaData> listMessagesMetadata(CassandraId mailboxId, MessageRange range) {
        return cassandraAsyncExecutor.executeRows(selectMetadataRange.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID_GTE, range.getUidFrom().asLong())
                .setLong(IMAP_UID_LTE, range.getUidTo().asLong()))
            .map(row -> {
                CassandraMessageId messageId = CassandraMessageId.Factory.of(row.get(MESSAGE_ID, TypeCodecs.TIMEUUID));
                return ComposedMessageIdWithMetaData.builder()
                    .modSeq(ModSeq.of(TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(MOD_SEQ), protocolVersion)))
                    .threadId(getThreadIdFromRow(row, messageId))
                    .flags(FlagsExtractor.getFlags(row))
                    .composedMessageId(new ComposedMessageId(mailboxId,
                        messageId,
                        MessageUid.of(TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(IMAP_UID), protocolVersion))))
                    .build();
            });
    }

    public Flux<MessageUid> listNotDeletedUids(CassandraId mailboxId, MessageRange range) {
        MemoizedSupplier<Integer> deletedPosition = new MemoizedSupplier<>();
        MemoizedSupplier<Integer> uidPosition = new MemoizedSupplier<>();

        return cassandraAsyncExecutor.executeRows(selectNotDeletedRange.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID_GTE, range.getUidFrom().asLong())
                .setLong(IMAP_UID_LTE, range.getUidTo().asLong()))
            .filter(row -> !TypeCodecs.BOOLEAN.decodePrimitive(
                row.getBytesUnsafe(deletedPosition.get(() -> row.getColumnDefinitions().firstIndexOf(DELETED))), protocolVersion))
            .map(row -> MessageUid.of(TypeCodecs.BIGINT.decodePrimitive(
                row.getBytesUnsafe(uidPosition.get(() -> row.getColumnDefinitions().firstIndexOf(IMAP_UID))), protocolVersion)));
    }

    private Flux<MessageUid> doListUids(CassandraId mailboxId, MessageRange range) {
        return cassandraAsyncExecutor.executeRows(selectUidOnlyRange.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID_GTE, range.getUidFrom().asLong())
                .setLong(IMAP_UID_LTE, range.getUidTo().asLong()))
            .map(row -> MessageUid.of(TypeCodecs.BIGINT.decodePrimitive(row.getBytesUnsafe(0), protocolVersion)));
    }

    public Flux<MessageUid> listUids(CassandraId mailboxId, MessageRange range) {
        if (range.getType() == MessageRange.Type.ALL) {
            return listUids(mailboxId);
        }
        return doListUids(mailboxId, range);
    }

    public Flux<CassandraMessageMetadata> retrieveAllMessages() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
                .setTimeout(Duration.ofDays(1)))
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
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setInt(LIMIT, limitAsInt))
            .orElseGet(() -> selectAll.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)));
    }

    private Flux<Row> selectFrom(CassandraId mailboxId, MessageUid uid, Limit limit) {
        return cassandraAsyncExecutor.executeRows(limit.getLimit()
            .map(limitAsInt -> selectUidGteLimited.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID, uid.asLong())
                .setInt(LIMIT, limitAsInt))
            .orElseGet(() -> selectUidGte.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID, uid.asLong())));
    }

    private Flux<Row> selectRange(CassandraId mailboxId, MessageUid from, MessageUid to, Limit limit) {
        return cassandraAsyncExecutor.executeRows(limit.getLimit()
            .map(limitAsInt -> selectUidRangeLimited.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID_GTE, from.asLong())
                .setLong(IMAP_UID_LTE, to.asLong())
                .setInt(LIMIT, limitAsInt))
            .orElseGet(() -> selectUidRange.bind()
                .set(MAILBOX_ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID)
                .setLong(IMAP_UID_GTE, from.asLong())
                .setLong(IMAP_UID_LTE, to.asLong())));
    }

    private Optional<CassandraMessageMetadata> fromRowToComposedMessageIdWithFlags(Row row) {
        UUID rowAsUuid = row.get(MESSAGE_ID, TypeCodecs.TIMEUUID);
        if (rowAsUuid == null) {
            // Out of order updates with concurrent deletes can result in the row being partially deleted
            // We filter out such records, and cleanup them.
            delete(CassandraId.of(row.get(MAILBOX_ID, TypeCodecs.TIMEUUID)),
                MessageUid.of(row.getLong(IMAP_UID)))
                .subscribeOn(Schedulers.parallel())
                .subscribe();
            return Optional.empty();
        }
        CassandraMessageId messageId = CassandraMessageId.Factory.of(rowAsUuid);
        return Optional.of(CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                    CassandraId.of(row.get(MAILBOX_ID, TypeCodecs.TIMEUUID)),
                    messageId,
                    MessageUid.of(row.getLong(IMAP_UID))))
                .flags(FlagsExtractor.getFlags(row))
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ)))
                .threadId(getThreadIdFromRow(row, messageId))
                .build())
            .bodyStartOctet(row.get(BODY_START_OCTET, Integer.class))
            .internalDate(Optional.ofNullable(row.get(INTERNAL_DATE, TypeCodecs.TIMESTAMP))
                .map(Date::from))
            .saveDate(Optional.ofNullable(row.get(SAVE_DATE, TypeCodecs.TIMESTAMP))
                .map(Date::from))
            .size(row.get(FULL_CONTENT_OCTETS, Long.class))
            .headerContent(Optional.ofNullable(row.get(HEADER_CONTENT, TypeCodecs.TEXT))
                .map(blobIdFactory::from))
            .build());
    }

    private ThreadId getThreadIdFromRow(Row row, MessageId messageId) {
        UUID threadIdUUID = row.get(THREAD_ID, TypeCodecs.TIMEUUID);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(threadIdUUID));
    }

    @VisibleForTesting
    Mono<Void> insertNullInternalDateAndHeaderContent(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatementBuilder statementBuilder = insert.boundStatementBuilder();
        if (flags.getUserFlags().length == 0) {
            statementBuilder.unset(USER_FLAGS);
        } else {
            statementBuilder.setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class);
        }
        return cassandraAsyncExecutor.executeVoid(statementBuilder
            .setUuid(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setUuid(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
            .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setBoolean(ANSWERED, flags.contains(Flag.ANSWERED))
            .setBoolean(DELETED, flags.contains(Flag.DELETED))
            .setBoolean(DRAFT, flags.contains(Flag.DRAFT))
            .setBoolean(FLAGGED, flags.contains(Flag.FLAGGED))
            .setBoolean(RECENT, flags.contains(Flag.RECENT))
            .setBoolean(SEEN, flags.contains(Flag.SEEN))
            .setBoolean(USER, flags.contains(Flag.USER))
            .setInstant(INTERNAL_DATE, null)
            .setInt(BODY_START_OCTET, 0)
            .setLong(FULL_CONTENT_OCTETS, 0)
            .setString(HEADER_CONTENT, null)
            .build());
    }
}
