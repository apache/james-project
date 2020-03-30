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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIdTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.IMAP_UID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMessageIds.MESSAGE_ID;
import static org.apache.james.mailbox.cassandra.table.Flag.ANSWERED;
import static org.apache.james.mailbox.cassandra.table.Flag.DELETED;
import static org.apache.james.mailbox.cassandra.table.Flag.DRAFT;
import static org.apache.james.mailbox.cassandra.table.Flag.FLAGGED;
import static org.apache.james.mailbox.cassandra.table.Flag.RECENT;
import static org.apache.james.mailbox.cassandra.table.Flag.SEEN;
import static org.apache.james.mailbox.cassandra.table.Flag.USER;
import static org.apache.james.mailbox.cassandra.table.Flag.USER_FLAGS;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ;

import java.util.Optional;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId.Factory;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageRange;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMessageIdDAO {

    private static final String IMAP_UID_GTE = IMAP_UID + "_GTE";
    private static final String IMAP_UID_LTE = IMAP_UID + "_LTE";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final Factory messageIdFactory;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectAllUids;
    private final PreparedStatement selectUidGte;
    private final PreparedStatement selectUidRange;
    private final PreparedStatement update;

    @Inject
    public CassandraMessageIdDAO(Session session, CassandraMessageId.Factory messageIdFactory) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.messageIdFactory = messageIdFactory;
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
        this.update = prepareUpdate(session);
        this.select = prepareSelect(session);
        this.selectAllUids = prepareSelectAllUids(session);
        this.selectUidGte = prepareSelectUidGte(session);
        this.selectUidRange = prepareSelectUidRange(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
                .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
                .value(IMAP_UID, bindMarker(IMAP_UID))
                .value(MOD_SEQ, bindMarker(MOD_SEQ))
                .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
                .value(ANSWERED, bindMarker(ANSWERED))
                .value(DELETED, bindMarker(DELETED))
                .value(DRAFT, bindMarker(DRAFT))
                .value(FLAGGED, bindMarker(FLAGGED))
                .value(RECENT, bindMarker(RECENT))
                .value(SEEN, bindMarker(SEEN))
                .value(USER, bindMarker(USER))
                .value(USER_FLAGS, bindMarker(USER_FLAGS)));
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
                .and(set(USER_FLAGS, bindMarker(USER_FLAGS)))
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(eq(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelectAllUids(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    private PreparedStatement prepareSelectUidGte(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(gte(IMAP_UID, bindMarker(IMAP_UID))));
    }

    private PreparedStatement prepareSelectUidRange(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MAILBOX_ID, bindMarker(MAILBOX_ID)))
                .and(gte(IMAP_UID, bindMarker(IMAP_UID_GTE)))
                .and(lte(IMAP_UID, bindMarker(IMAP_UID_LTE))));
    }

    public Mono<Void> delete(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    public Mono<Void> insert(ComposedMessageIdWithMetaData composedMessageIdWithMetaData) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        Flags flags = composedMessageIdWithMetaData.getFlags();
        return cassandraAsyncExecutor.executeVoid(insert.bind()
                .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
                .setLong(IMAP_UID, composedMessageId.getUid().asLong())
                .setUUID(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
                .setLong(MOD_SEQ, composedMessageIdWithMetaData.getModSeq().asLong())
                .setBool(ANSWERED, flags.contains(Flag.ANSWERED))
                .setBool(DELETED, flags.contains(Flag.DELETED))
                .setBool(DRAFT, flags.contains(Flag.DRAFT))
                .setBool(FLAGGED, flags.contains(Flag.FLAGGED))
                .setBool(RECENT, flags.contains(Flag.RECENT))
                .setBool(SEEN, flags.contains(Flag.SEEN))
                .setBool(USER, flags.contains(Flag.USER))
                .setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags())));
    }

    public Mono<Void> updateMetadata(ComposedMessageIdWithMetaData composedMessageIdWithMetaData) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        Flags flags = composedMessageIdWithMetaData.getFlags();
        return cassandraAsyncExecutor.executeVoid(update.bind()
                .setLong(MOD_SEQ, composedMessageIdWithMetaData.getModSeq().asLong())
                .setBool(ANSWERED, flags.contains(Flag.ANSWERED))
                .setBool(DELETED, flags.contains(Flag.DELETED))
                .setBool(DRAFT, flags.contains(Flag.DRAFT))
                .setBool(FLAGGED, flags.contains(Flag.FLAGGED))
                .setBool(RECENT, flags.contains(Flag.RECENT))
                .setBool(SEEN, flags.contains(Flag.SEEN))
                .setBool(USER, flags.contains(Flag.USER))
                .setSet(USER_FLAGS, ImmutableSet.copyOf(flags.getUserFlags()))
                .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
                .setLong(IMAP_UID, composedMessageId.getUid().asLong()));
    }

    public Mono<Optional<ComposedMessageIdWithMetaData>> retrieve(CassandraId mailboxId, MessageUid uid) {
        return asOptionalOfCassandraMessageId(selectOneRow(mailboxId, uid));
    }

    private Mono<Optional<ComposedMessageIdWithMetaData>> asOptionalOfCassandraMessageId(Mono<Row> row) {
        return row
                .map(this::fromRowToComposedMessageIdWithFlags)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Row> selectOneRow(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeSingleRow(select.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    public Flux<ComposedMessageIdWithMetaData> retrieveMessages(CassandraId mailboxId, MessageRange set) {
        return retrieveRows(mailboxId, set)
            .map(this::fromRowToComposedMessageIdWithFlags);
    }

    private Flux<Row> retrieveRows(CassandraId mailboxId, MessageRange set) {
        switch (set.getType()) {
        case ALL:
            return selectAll(mailboxId);
        case FROM:
            return selectFrom(mailboxId, set.getUidFrom());
        case RANGE:
            return selectRange(mailboxId, set.getUidFrom(), set.getUidTo());
        case ONE:
            return Flux.concat(selectOneRow(mailboxId, set.getUidFrom()));
        }
        throw new UnsupportedOperationException();
    }

    private Flux<Row> selectAll(CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeRows(selectAllUids.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    private Flux<Row> selectFrom(CassandraId mailboxId, MessageUid uid) {
        return cassandraAsyncExecutor.executeRows(selectUidGte.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID, uid.asLong()));
    }

    private Flux<Row> selectRange(CassandraId mailboxId, MessageUid from, MessageUid to) {
        return cassandraAsyncExecutor.executeRows(selectUidRange.bind()
                .setUUID(MAILBOX_ID, mailboxId.asUuid())
                .setLong(IMAP_UID_GTE, from.asLong())
                .setLong(IMAP_UID_LTE, to.asLong()));
    }

    private ComposedMessageIdWithMetaData fromRowToComposedMessageIdWithFlags(Row row) {
        return ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                        CassandraId.of(row.getUUID(MAILBOX_ID)),
                        messageIdFactory.of(row.getUUID(MESSAGE_ID)),
                        MessageUid.of(row.getLong(IMAP_UID))))
                .flags(new FlagsExtractor(row).getFlags())
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ)))
                .build();
    }
}
