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
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
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
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.FIELDS;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.MOD_SEQ;
import static org.apache.james.mailbox.cassandra.table.MessageIdToImapUid.TABLE_NAME;

import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId.Factory;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ConsistencyLevel;
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
    public enum ConsistencyChoice {
        STRONG(CassandraConsistenciesConfiguration::getLightweightTransaction),
        WEAK(CassandraConsistenciesConfiguration::getRegular);

        private final Function<CassandraConsistenciesConfiguration, ConsistencyLevel> consistencyLevelChoice;


        ConsistencyChoice(Function<CassandraConsistenciesConfiguration, ConsistencyLevel> consistencyLevelChoice) {
            this.consistencyLevelChoice = consistencyLevelChoice;
        }

        public ConsistencyLevel choose(CassandraConsistenciesConfiguration configuration) {
            return consistencyLevelChoice.apply(configuration);
        }
    }

    private static final String MOD_SEQ_CONDITION = "modSeqCondition";

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final Factory messageIdFactory;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement update;
    private final PreparedStatement selectAll;
    private final PreparedStatement select;
    private final PreparedStatement listStatement;
    private final CassandraConfiguration cassandraConfiguration;
    private final CassandraConsistenciesConfiguration consistenciesConfiguration;

    @Inject
    public CassandraMessageIdToImapUidDAO(Session session, CassandraConsistenciesConfiguration consistenciesConfiguration,
                                          CassandraMessageId.Factory messageIdFactory, CassandraConfiguration cassandraConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.consistenciesConfiguration = consistenciesConfiguration;
        this.cassandraConfiguration = cassandraConfiguration;
        this.messageIdFactory = messageIdFactory;
        this.delete = prepareDelete(session);
        this.insert = prepareInsert(session);
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
            .value(MOD_SEQ, bindMarker(MOD_SEQ))
            .value(ANSWERED, bindMarker(ANSWERED))
            .value(DELETED, bindMarker(DELETED))
            .value(DRAFT, bindMarker(DRAFT))
            .value(FLAGGED, bindMarker(FLAGGED))
            .value(RECENT, bindMarker(RECENT))
            .value(SEEN, bindMarker(SEEN))
            .value(USER, bindMarker(USER))
            .value(USER_FLAGS, bindMarker(USER_FLAGS));
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return session.prepare(insert.ifNotExists());
        } else {
            return session.prepare(insert);
        }
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
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID))));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select(FIELDS).from(TABLE_NAME));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(MESSAGE_ID, bindMarker(MESSAGE_ID)))
                .and(eq(MAILBOX_ID, bindMarker(MAILBOX_ID))));
    }

    public Mono<Void> delete(CassandraMessageId messageId, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
                .setUUID(MESSAGE_ID, messageId.get())
                .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    public Mono<Void> insert(ComposedMessageIdWithMetaData composedMessageIdWithMetaData) {
        ComposedMessageId composedMessageId = composedMessageIdWithMetaData.getComposedMessageId();
        Flags flags = composedMessageIdWithMetaData.getFlags();
        return cassandraAsyncExecutor.executeVoid(insert.bind()
                .setUUID(MESSAGE_ID, ((CassandraMessageId) composedMessageId.getMessageId()).get())
                .setUUID(MAILBOX_ID, ((CassandraId) composedMessageId.getMailboxId()).asUuid())
                .setLong(IMAP_UID, composedMessageId.getUid().asLong())
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

    public Flux<ComposedMessageIdWithMetaData> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId, ConsistencyChoice readConsistencyChoice) {
        return cassandraAsyncExecutor.executeRows(
                    selectStatement(messageId, mailboxId)
                    .setConsistencyLevel(readConsistencyChoice.choose(consistenciesConfiguration)))
                .map(this::toComposedMessageIdWithMetadata);
    }

    @VisibleForTesting
    public Flux<ComposedMessageIdWithMetaData> retrieve(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return retrieve(messageId, mailboxId, ConsistencyChoice.STRONG);
    }

    public Flux<ComposedMessageIdWithMetaData> retrieveAllMessages() {
        return cassandraAsyncExecutor.executeRows(listStatement.bind())
            .map(row -> toComposedMessageIdWithMetadata(row));
    }

    private ComposedMessageIdWithMetaData toComposedMessageIdWithMetadata(Row row) {
        return ComposedMessageIdWithMetaData.builder()
                .composedMessageId(new ComposedMessageId(
                    CassandraId.of(row.getUUID(MAILBOX_ID)),
                    messageIdFactory.of(row.getUUID(MESSAGE_ID)),
                    MessageUid.of(row.getLong(IMAP_UID))))
                .flags(FlagsExtractor.getFlags(row))
                .modSeq(ModSeq.of(row.getLong(MOD_SEQ)))
                .build();
    }

    private Statement selectStatement(CassandraMessageId messageId, Optional<CassandraId> mailboxId) {
        return mailboxId
            .map(cassandraId -> select.bind()
                .setUUID(MESSAGE_ID, messageId.get())
                .setUUID(MAILBOX_ID, cassandraId.asUuid()))
            .orElseGet(() -> selectAll.bind().setUUID(MESSAGE_ID, messageId.get()));
    }
}
