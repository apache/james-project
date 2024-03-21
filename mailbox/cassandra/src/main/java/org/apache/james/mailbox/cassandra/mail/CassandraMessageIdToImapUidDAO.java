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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.setColumn;
import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.STRONG;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.SAVE_DATE;
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
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.THREAD_ID;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.update.Update;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMessageIdToImapUidDAO {
    private static final String MOD_SEQ_CONDITION = "modSeqCondition";
    private static final String ADDED_USERS_FLAGS = "added_user_flags";
    private static final String REMOVED_USERS_FLAGS = "removed_user_flags";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final BlobId.Factory blobIdFactory;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement insertForced;
    private final PreparedStatement update;
    private final PreparedStatement selectAll;
    private final PreparedStatement select;
    private final PreparedStatement listStatement;
    private final CassandraConfiguration cassandraConfiguration;
    private final CqlSession session;
    private final DriverExecutionProfile lwtProfile;

    @Inject
    public CassandraMessageIdToImapUidDAO(CqlSession session, BlobId.Factory blobIdFactory,
                                          CassandraConfiguration cassandraConfiguration) {
        this.session = session;
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.cassandraConfiguration = cassandraConfiguration;
        this.delete = prepareDelete();
        this.insert = prepareInsert();
        this.insertForced = prepareInsertForced();
        this.update = prepareUpdate();
        this.selectAll = prepareSelectAll();
        this.select = prepareSelect();
        this.listStatement = prepareList();
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
    }

    private PreparedStatement prepareDelete() {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(List.of(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID))))
            .build());
    }

    private PreparedStatement prepareInsert() {
        Insert insert = insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(IMAP_UID, bindMarker(IMAP_UID))
            .value(THREAD_ID, bindMarker(THREAD_ID))
            .value(MOD_SEQ, bindMarker(MOD_SEQ))
            .value(ANSWERED, bindMarker(ANSWERED))
            .value(DELETED, bindMarker(DELETED))
            .value(DRAFT, bindMarker(DRAFT))
            .value(FLAGGED, bindMarker(FLAGGED))
            .value(RECENT, bindMarker(RECENT))
            .value(SEEN, bindMarker(SEEN))
            .value(USER, bindMarker(USER))
            .value(USER_FLAGS, bindMarker(USER_FLAGS))
            .value(INTERNAL_DATE, bindMarker(INTERNAL_DATE))
            .value(SAVE_DATE, bindMarker(SAVE_DATE))
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT));
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(insert.ifNotExists().build());
        } else {
            return session.prepare(QueryBuilder.update(TABLE_NAME)
                .set(setColumn(THREAD_ID, bindMarker(THREAD_ID)),
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
                    setColumn(HEADER_CONTENT, bindMarker(HEADER_CONTENT)))
                .append(USER_FLAGS, bindMarker(USER_FLAGS))
                .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                    column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                    column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)))
                .build());
        }
    }

    private PreparedStatement prepareInsertForced() {
        Insert insert = insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(IMAP_UID, bindMarker(IMAP_UID))
            .value(MOD_SEQ, bindMarker(MOD_SEQ))
            .value(ANSWERED, bindMarker(ANSWERED))
            .value(DELETED, bindMarker(DELETED))
            .value(DRAFT, bindMarker(DRAFT))
            .value(FLAGGED, bindMarker(FLAGGED))
            .value(RECENT, bindMarker(RECENT))
            .value(SEEN, bindMarker(SEEN))
            .value(USER, bindMarker(USER))
            .value(USER_FLAGS, bindMarker(USER_FLAGS))
            .value(INTERNAL_DATE, bindMarker(INTERNAL_DATE))
            .value(SAVE_DATE, bindMarker(SAVE_DATE))
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT));
        return session.prepare(insert.build());
    }

    private PreparedStatement prepareUpdate() {
        Update update = QueryBuilder.update(TABLE_NAME)
            .set(setColumn(MOD_SEQ, bindMarker(MOD_SEQ)),
                setColumn(ANSWERED, bindMarker(ANSWERED)),
                setColumn(DELETED, bindMarker(DELETED)),
                setColumn(DRAFT, bindMarker(DRAFT)),
                setColumn(FLAGGED, bindMarker(FLAGGED)),
                setColumn(RECENT, bindMarker(RECENT)),
                setColumn(SEEN, bindMarker(SEEN)),
                setColumn(USER, bindMarker(USER)))
            .append(USER_FLAGS, bindMarker(ADDED_USERS_FLAGS))
            .remove(USER_FLAGS, bindMarker(REMOVED_USERS_FLAGS))
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)),
                column(IMAP_UID).isEqualTo(bindMarker(IMAP_UID)));

        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(update.ifColumn(MOD_SEQ).isEqualTo(bindMarker(MOD_SEQ_CONDITION)).build());
        } else {
            return session.prepare(update.build());
        }
    }

    private PreparedStatement prepareSelectAll() {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)))
            .build());
    }

    private PreparedStatement prepareList() {
        return session.prepare(selectFrom(TABLE_NAME).all().build());
    }

    private PreparedStatement prepareSelect() {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID)),
                column(MAILBOX_ID).isEqualTo(bindMarker(MAILBOX_ID)))
            .build());
    }

    public Mono<Void> delete(CassandraMessageId messageId, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUuid(MESSAGE_ID, messageId.get())
            .setUuid(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Void> insert(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatementBuilder statementBuilder = insert.boundStatementBuilder();
        if (metadata.getComposedMessageId().getFlags().getUserFlags().length == 0) {
            statementBuilder.unset(USER_FLAGS);
        } else {
            statementBuilder.setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class);
        }

        return cassandraAsyncExecutor.executeVoid(statementBuilder
            .set(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get(), TypeCodecs.TIMEUUID)
            .set(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid(), TypeCodecs.TIMEUUID)
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
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

    public Mono<Void> insertForce(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        return cassandraAsyncExecutor.executeVoid(insertForced.bind()
            .set(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get(), TypeCodecs.TIMEUUID)
            .set(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid(), TypeCodecs.TIMEUUID)
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setBoolean(ANSWERED, flags.contains(Flag.ANSWERED))
            .setBoolean(DELETED, flags.contains(Flag.DELETED))
            .setBoolean(DRAFT, flags.contains(Flag.DRAFT))
            .setBoolean(FLAGGED, flags.contains(Flag.FLAGGED))
            .setBoolean(RECENT, flags.contains(Flag.RECENT))
            .setBoolean(SEEN, flags.contains(Flag.SEEN))
            .setBoolean(USER, flags.contains(Flag.USER))
            .setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class)
            .setInstant(INTERNAL_DATE, metadata.getInternalDate().get().toInstant())
            .setInstant(SAVE_DATE, metadata.getSaveDate().map(Date::toInstant).orElse(null))
            .setInt(BODY_START_OCTET, Math.toIntExact(metadata.getBodyStartOctet().get()))
            .setLong(FULL_CONTENT_OCTETS, metadata.getSize().get())
            .setString(HEADER_CONTENT, metadata.getHeaderContent().get().asString()));
    }

    public Mono<Boolean> updateMetadata(ComposedMessageId id, UpdatedFlags updatedFlags, ModSeq previousModeq) {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return cassandraAsyncExecutor.executeReturnApplied(updateBoundStatement(id, updatedFlags, previousModeq));
        } else {
            return cassandraAsyncExecutor.executeVoid(updateBoundStatement(id, updatedFlags, previousModeq)).thenReturn(true);
        }
    }

    private BoundStatement updateBoundStatement(ComposedMessageId id, UpdatedFlags updatedFlags, ModSeq previousModeq) {
        final BoundStatementBuilder statementBuilder = update.boundStatementBuilder()
            .setLong(MOD_SEQ, updatedFlags.getModSeq().asLong())
            .setUuid(MESSAGE_ID, ((CassandraMessageId) id.getMessageId()).get())
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
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            statementBuilder.setLong(MOD_SEQ_CONDITION, previousModeq.asLong());
        }
        return statementBuilder.build();
    }

    public Flux<CassandraMessageMetadata> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId, JamesExecutionProfiles.ConsistencyChoice readConsistencyChoice) {
        return cassandraAsyncExecutor.executeRows(setExecutionProfileIfNeeded(selectStatement(messageId, mailboxId), readConsistencyChoice))
            .map(this::toComposedMessageIdWithMetadata);
    }

    @VisibleForTesting
    public Flux<CassandraMessageMetadata> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return retrieve(messageId, mailboxId, STRONG);
    }

    public Flux<CassandraMessageMetadata> retrieveAllMessages() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
                .setTimeout(Duration.ofDays(1)))
            .map(this::toComposedMessageIdWithMetadata);
    }

    private CassandraMessageMetadata toComposedMessageIdWithMetadata(Row row) {
        final CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUuid(MESSAGE_ID));
        return CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                    CassandraId.of(row.getUuid(MAILBOX_ID)),
                    messageId,
                    MessageUid.of(row.getLong(IMAP_UID))))
                .flags(FlagsExtractor.getFlags(row))
                .threadId(getThreadIdFromRow(row, messageId))
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ)))
                .build())
            .bodyStartOctet(row.get(BODY_START_OCTET, Integer.class))
            .internalDate(Optional.ofNullable(row.get(INTERNAL_DATE, TypeCodecs.TIMESTAMP))
                .map(Date::from))
            .saveDate(Optional.ofNullable(row.get(SAVE_DATE, TypeCodecs.TIMESTAMP))
                .map(Date::from))
            .size(row.get(FULL_CONTENT_OCTETS, Long.class))
            .headerContent(Optional.ofNullable(row.getString(HEADER_CONTENT))
                .map(blobIdFactory::from))
            .build();
    }

    private ThreadId getThreadIdFromRow(Row row, MessageId messageId) {
        UUID threadIdUUID = row.get(THREAD_ID, TypeCodecs.TIMEUUID);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(threadIdUUID));
    }

    private BoundStatement selectStatement(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return mailboxId
            .map(cassandraId -> select.bind()
                .setUuid(MESSAGE_ID, messageId.get())
                .setUuid(MAILBOX_ID, cassandraId.asUuid()))
            .orElseGet(() -> selectAll.bind().setUuid(MESSAGE_ID, messageId.get()));
    }

    private BoundStatement setExecutionProfileIfNeeded(BoundStatement statement, JamesExecutionProfiles.ConsistencyChoice consistencyChoice) {
        if (consistencyChoice.equals(STRONG)) {
            return statement.setExecutionProfile(lwtProfile);
        } else {
            return statement;
        }
    }

    @VisibleForTesting
    Mono<Void> insertNullInternalDateAndHeaderContent(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatementBuilder statementBuilder = insert.boundStatementBuilder();
        if (metadata.getComposedMessageId().getFlags().getUserFlags().length == 0) {
            statementBuilder.unset(USER_FLAGS);
        } else {
            statementBuilder.setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()), String.class);
        }

        return cassandraAsyncExecutor.executeVoid(statementBuilder
            .set(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get(), TypeCodecs.TIMEUUID)
            .set(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid(), TypeCodecs.TIMEUUID)
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setUuid(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
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
