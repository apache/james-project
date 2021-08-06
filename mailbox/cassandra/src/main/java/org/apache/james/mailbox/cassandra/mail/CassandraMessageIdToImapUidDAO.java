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
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
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
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.THREAD_ID;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.THREAD_ID_LOWERCASE;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice;
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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Update;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMessageIdToImapUidDAO {
    private static final String MOD_SEQ_CONDITION = "modSeqCondition";

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
    private final CassandraConsistenciesConfiguration consistenciesConfiguration;

    @Inject
    public CassandraMessageIdToImapUidDAO(Session session, BlobId.Factory blobIdFactory, CassandraConsistenciesConfiguration consistenciesConfiguration,
                                          CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.blobIdFactory = blobIdFactory;
        this.consistenciesConfiguration = consistenciesConfiguration;
        this.cassandraConfiguration = cassandraConfiguration;
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
        this.insertForced = prepareInsertForced(session);
        this.update = prepareUpdate(session);
        this.selectAll = prepareSelectAll(session);
        this.select = prepareSelect(session);
        this.listStatement = prepareList(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
                .and(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
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
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT));
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(insert.ifNotExists());
        } else {
            return session.prepare(update(TABLE_NAME)
                .with(set(THREAD_ID, bindMarker(THREAD_ID)))
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
                .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
                .and(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
        }
    }

    private PreparedStatement prepareInsertForced(Session session) {
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
            .value(BODY_START_OCTET, bindMarker(BODY_START_OCTET))
            .value(FULL_CONTENT_OCTETS, bindMarker(FULL_CONTENT_OCTETS))
            .value(HEADER_CONTENT, bindMarker(HEADER_CONTENT));
        return session.prepare(insert);
    }

    private PreparedStatement prepareUpdate(Session session) {
        Update.Where update = update(TABLE_NAME)
            .with(set(MOD_SEQ, bindMarker(MOD_SEQ)))
            .and(set(ANSWERED, bindMarker(ANSWERED)))
            .and(set(DELETED, bindMarker(DELETED)))
            .and(set(DRAFT, bindMarker(DRAFT)))
            .and(set(FLAGGED, bindMarker(FLAGGED)))
            .and(set(RECENT, bindMarker(RECENT)))
            .and(set(SEEN, bindMarker(SEEN)))
            .and(set(USER, bindMarker(USER)))
            .and(set(USER_FLAGS, bindMarker(USER_FLAGS)))
            .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
            .and(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
            .and(eq(IMAP_UID, bindMarker(IMAP_UID)));

        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(update
                .onlyIf(eq(MOD_SEQ, bindMarker(MOD_SEQ_CONDITION))));
        } else {
            return session.prepare(update);
        }
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select()
                .from(TABLE_NAME)
                .where(eq(MESSAGE_ID_LOWERCASE, bindMarker(MESSAGE_ID_LOWERCASE))));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select().from(TABLE_NAME));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
                .from(TABLE_NAME)
                .where(eq(MESSAGE_ID_LOWERCASE, bindMarker(MESSAGE_ID_LOWERCASE)))
                .and(eq(MAILBOX_ID_LOWERCASE, bindMarker(MAILBOX_ID_LOWERCASE))));
    }

    public Mono<Void> delete(CassandraMessageId messageId, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
                .setUUID(MESSAGE_ID, messageId.get())
                .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Void> insert(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        ThreadId threadId = metadata.getComposedMessageId().getThreadId();

        BoundStatement boundStatement = insert.bind();
        if (metadata.getComposedMessageId().getFlags().getUserFlags().length == 0) {
            boundStatement.unset(USER_FLAGS);
        } else {
            boundStatement.setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()));
        }

        return cassandraAsyncExecutor.executeVoid(boundStatement
            .setUUID(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
            .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
            .setLong(IMAP_UID, composedMessageId.getUid().asLong())
            .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
            .setUUID(THREAD_ID, ((CassandraMessageId) threadId.getBaseMessageId()).get())
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

    public Mono<Void> insertForce(CassandraMessageMetadata metadata) {
        ComposedMessageId composedMessageId = metadata.getComposedMessageId().getComposedMessageId();
        Flags flags = metadata.getComposedMessageId().getFlags();
        return cassandraAsyncExecutor.executeVoid(insertForced.bind()
                .setUUID(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
                .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
                .setLong(IMAP_UID, composedMessageId.getUid().asLong())
                .setLong(MOD_SEQ, metadata.getComposedMessageId().getModSeq().asLong())
                .setBool(ANSWERED, flags.contains(Flag.ANSWERED))
                .setBool(DELETED, flags.contains(Flag.DELETED))
                .setBool(DRAFT, flags.contains(Flag.DRAFT))
                .setBool(FLAGGED, flags.contains(Flag.FLAGGED))
                .setBool(RECENT, flags.contains(Flag.RECENT))
                .setBool(SEEN, flags.contains(Flag.SEEN))
                .setBool(USER, flags.contains(Flag.USER))
                .setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()))
                .setTimestamp(INTERNAL_DATE, metadata.getInternalDate().get())
                .setInt(BODY_START_OCTET, Math.toIntExact(metadata.getBodyStartOctet().get()))
                .setLong(FULL_CONTENT_OCTETS, metadata.getSize().get())
                .setString(HEADER_CONTENT, metadata.getHeaderContent().get().asString()));
    }

    public Mono<Boolean> updateMetadata(ComposedMessageIdWithMetaData composedMessageIdWithMetaData, ModSeq oldModSeq) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        Flags flags = composedMessageIdWithMetaData.getFlags();
        return cassandraAsyncExecutor.executeReturnApplied(updateBoundStatement(composedMessageIdWithMetaData, composedMessageId, flags, oldModSeq));
    }

    private BoundStatement updateBoundStatement(ComposedMessageIdWithMetaData composedMessageIdWithMetaData, ComposedMessageId composedMessageId, Flags flags,
                                                ModSeq oldModSeq) {
        final BoundStatement boundStatement = update.bind()
            .setLong(MOD_SEQ, composedMessageIdWithMetaData.getModSeq().asLong())
            .setBool(ANSWERED, flags.contains(Flag.ANSWERED))
            .setBool(DELETED, flags.contains(Flag.DELETED))
            .setBool(DRAFT, flags.contains(Flag.DRAFT))
            .setBool(FLAGGED, flags.contains(Flag.FLAGGED))
            .setBool(RECENT, flags.contains(Flag.RECENT))
            .setBool(SEEN, flags.contains(Flag.SEEN))
            .setBool(USER, flags.contains(Flag.USER))
            .setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()))
            .setUUID(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
            .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
            .setLong(IMAP_UID, composedMessageId.getUid().asLong());
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return boundStatement.setLong(MOD_SEQ_CONDITION, oldModSeq.asLong());
        }
        return boundStatement;
    }

    public Flux<CassandraMessageMetadata> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId, ConsistencyChoice readConsistencyChoice) {
        return cassandraAsyncExecutor.executeRows(
                    selectStatement(messageId, mailboxId)
                    .setConsistencyLevel(readConsistencyChoice.choose(consistenciesConfiguration)))
                .map(this::toComposedMessageIdWithMetadata);
    }

    @VisibleForTesting
    public Flux<CassandraMessageMetadata> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return retrieve(messageId, mailboxId, ConsistencyChoice.STRONG);
    }

    public Flux<CassandraMessageMetadata> retrieveAllMessages() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind()
                .setReadTimeoutMillis(Duration.ofDays(1).toMillisPart()))
            .map(this::toComposedMessageIdWithMetadata);
    }


    private CassandraMessageMetadata toComposedMessageIdWithMetadata(Row row) {
        final CassandraMessageId messageId = CassandraMessageId.Factory.of(row.getUUID(MESSAGE_ID_LOWERCASE));
        return CassandraMessageMetadata.builder()
            .ids(ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                    CassandraId.of(row.getUUID(MAILBOX_ID_LOWERCASE)),
                    messageId,
                    MessageUid.of(row.getLong(IMAP_UID))))
                .flags(FlagsExtractor.getFlags(row))
                .threadId(getThreadIdFromRow(row, messageId))
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ_LOWERCASE)))
                .build())
                .bodyStartOctet(row.getInt(BODY_START_OCTET_LOWERCASE))
                .internalDate(row.getTimestamp(INTERNAL_DATE_LOWERCASE))
                .size(row.getLong(FULL_CONTENT_OCTETS_LOWERCASE))
                .headerContent(Optional.ofNullable(row.getString(HEADER_CONTENT_LOWERCASE))
                .map(blobIdFactory::from))
            .build();
    }

    private ThreadId getThreadIdFromRow(Row row, MessageId messageId) {
        UUID threadIdUUID = row.getUUID(THREAD_ID_LOWERCASE);
        if (threadIdUUID == null) {
            return ThreadId.fromBaseMessageId(messageId);
        }
        return ThreadId.fromBaseMessageId(CassandraMessageId.Factory.of(threadIdUUID));
    }

    private Statement selectStatement(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return mailboxId
            .map(cassandraId -> select.bind()
                .setUUID(MESSAGE_ID_LOWERCASE, messageId.get())
                .setUUID(MAILBOX_ID_LOWERCASE, cassandraId.asUuid()))
            .orElseGet(() -> selectAll.bind().setUUID(MESSAGE_ID_LOWERCASE, messageId.get()));
    }
}
