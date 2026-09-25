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

package org.apache.james.mailbox.postgres.mail.dao;

import static org.apache.james.backends.postgres.PostgresCommons.DATE_TO_LOCAL_DATE_TIME;
import static org.apache.james.backends.postgres.PostgresCommons.IN_CLAUSE_MAX_SIZE;
import static org.apache.james.backends.postgres.PostgresCommons.LOCAL_DATE_TIME_DATE_FUNCTION;
import static org.apache.james.backends.postgres.PostgresCommons.UNNEST_FIELD;
import static org.apache.james.backends.postgres.PostgresCommons.tableField;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.EAGER_FETCH;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageTable.SIZE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.IS_ANSWERED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.IS_DELETED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.IS_DRAFT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.IS_FLAGGED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.IS_RECENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.IS_SEEN;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.MESSAGE_UID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.MOD_SEQ;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.REMOVE_ELEMENTS_FROM_ARRAY_FUNCTION_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.SAVE_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.THREAD_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageToMailboxTable.USER_FLAGS;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.BOOLEAN_FLAGS_MAPPING;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.FETCH_TYPE_TO_FETCH_STRATEGY;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.MESSAGE_METADATA_FIELDS_REQUIRE;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_FLAGS_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_MESSAGE_METADATA_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_MESSAGE_UID_FUNCTION;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.mail.Flags;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMessageDataDefinition.MessageTable;
import org.apache.james.mailbox.store.StoreMessageManager;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.streams.Limit;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.TableOnConditionStep;
import org.jooq.UpdateConditionStep;
import org.jooq.UpdateSetStep;
import org.jooq.impl.DSL;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxMessageDAO {

    public static class Factory {
        private final PostgresExecutor.Factory executorFactory;

        @Inject
        @Singleton
        public Factory(PostgresExecutor.Factory executorFactory) {
            this.executorFactory = executorFactory;
        }

        public PostgresMailboxMessageDAO create(Optional<Domain> domain) {
            return new PostgresMailboxMessageDAO(executorFactory.create(domain));
        }
    }

    private static final TableOnConditionStep<Record> MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP = TABLE_NAME.join(MessageTable.TABLE_NAME)
        .on(tableField(TABLE_NAME, MESSAGE_ID).eq(tableField(MessageTable.TABLE_NAME, MessageTable.MESSAGE_ID)));

    public static final SortField<Long> DEFAULT_SORT_ORDER_BY = MESSAGE_UID.asc();

    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxMessageDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<MessageUid> findFirstUnseenMessageUid(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRow(dslContext -> Flux.from(dslContext.select(MESSAGE_UID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq((mailboxId.asUuid())))
                .and(IS_SEEN.eq(false))
                .orderBy(DEFAULT_SORT_ORDER_BY)
                .limit(1)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<MessageUid> listUnseen(PostgresMailboxId mailboxId) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(IS_SEEN.eq(false)));
    }

    public Flux<MessageUid> listUnseen(PostgresMailboxId mailboxId, MessageRange range) {
        return switch (range.getType()) {
            case ALL -> listUnseen(mailboxId);
            case FROM -> listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
                .and(IS_SEEN.eq(false))
                .and(MESSAGE_UID.greaterOrEqual(range.getUidFrom().asLong())));
            case RANGE -> listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
                .and(IS_SEEN.eq(false))
                .and(MESSAGE_UID.greaterOrEqual(range.getUidFrom().asLong()))
                .and(MESSAGE_UID.lessOrEqual(range.getUidTo().asLong())));
            case ONE -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_UID)
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .and(IS_SEEN.eq(false))
                    .and(MESSAGE_UID.eq(range.getUidFrom().asLong()))
                    .orderBy(DEFAULT_SORT_ORDER_BY)), EAGER_FETCH)
                .map(RECORD_TO_MESSAGE_UID_FUNCTION);
        };
    }

    public Flux<MessageUid> findAllRecentMessageUid(PostgresMailboxId mailboxId) {
        if (!StoreMessageManager.HANDLE_RECENT) {
            return Flux.empty();
        }
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(IS_RECENT.eq(true)));
    }

    public Flux<MessageUid> listAllMessageUid(PostgresMailboxId mailboxId) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid()));
    }

    public Flux<MessageUid> listUids(PostgresMailboxId mailboxId, MessageRange range) {
        if (range.getType() == MessageRange.Type.ALL) {
            return listAllMessageUid(mailboxId);
        }
        return doListUids(mailboxId, range);
    }

    private Flux<MessageUid> doListUids(PostgresMailboxId mailboxId, MessageRange range) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(MESSAGE_UID.greaterOrEqual(range.getUidFrom().asLong()))
            .and(MESSAGE_UID.lessOrEqual(range.getUidTo().asLong())));
    }

    private Flux<MessageUid> listUidsPaginated(Condition condition) {
        return postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext.select(MESSAGE_UID)
                .from(TABLE_NAME)
                .where(condition)
                .and(afterUid(lastRecord))
                .orderBy(DEFAULT_SORT_ORDER_BY))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    private static Condition afterUid(Optional<Record> lastRecord) {
        return lastRecord.map(record -> MESSAGE_UID.greaterThan(record.get(MESSAGE_UID)))
            .orElseGet(DSL::noCondition);
    }

    public Mono<MessageMetaData> deleteByMailboxIdAndMessageUid(PostgresMailboxId mailboxId, MessageUid messageUid) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.eq(messageUid.asLong()))
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_MESSAGE_METADATA_FUNCTION);
    }

    public Flux<MessageMetaData> deleteByMailboxIdAndMessageUids(PostgresMailboxId mailboxId, List<MessageUid> uids) {
        if (uids.isEmpty()) {
            return Flux.empty();
        }
        Function<List<MessageUid>, Flux<MessageMetaData>> deletePublisherFunction = uidsToDelete -> postgresExecutor.executeDeleteAndReturnList(dslContext -> dslContext.deleteFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.in(uidsToDelete.stream().map(MessageUid::asLong).toArray(Long[]::new)))
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE))
            .map(RECORD_TO_MESSAGE_METADATA_FUNCTION);

        if (uids.size() <= IN_CLAUSE_MAX_SIZE) {
            return deletePublisherFunction.apply(uids);
        } else {
            return Flux.fromIterable(Iterables.partition(uids, IN_CLAUSE_MAX_SIZE))
                .flatMap(deletePublisherFunction);
        }
    }

    public Flux<MessageMetaData> deleteByMailboxId(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeDeleteAndReturnList(dslContext -> dslContext.deleteFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE))
            .map(RECORD_TO_MESSAGE_METADATA_FUNCTION)
            .collectList()
            .flatMapMany(Flux::fromIterable);
    }

    public Mono<Void> deleteByMessageIdAndMailboxIds(PostgresMessageId messageId, Collection<PostgresMailboxId> mailboxIds) {
        if (mailboxIds.isEmpty()) {
            return Mono.empty();
        }
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))
            .and(MAILBOX_ID.in(mailboxIds.stream().map(PostgresMailboxId::asUuid).collect(ImmutableList.toImmutableList())))));
    }

    public Mono<Void> deleteByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid()))));
    }

    public Mono<Integer> countTotalMessagesByMailboxId(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeCount(dslContext -> Mono.from(dslContext.selectCount()
            .from(TABLE_NAME)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))));
    }

    public Mono<Pair<Integer, Integer>> countTotalAndUnseenMessagesByMailboxId(PostgresMailboxId mailboxId) {
        Name totalCount = DSL.name("total_count");
        Name unSeenCount = DSL.name("unseen_count");
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(
                    DSL.count().as(totalCount),
                    DSL.count().filterWhere(IS_SEEN.eq(false)).as(unSeenCount))
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .map(record -> Pair.of(record.get(totalCount, Integer.class), record.get(unSeenCount, Integer.class)));
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, Record>> findMessagesByMailboxId(PostgresMailboxId mailboxId, Limit limit, MessageMapper.FetchType fetchType) {
        return findMessagesPaginated(MAILBOX_ID.eq(mailboxId.asUuid()), limit, fetchType);
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, Record>> findMessagesByMailboxIdAndBetweenUIDs(PostgresMailboxId mailboxId, MessageUid from, MessageUid to, Limit limit, FetchType fetchType) {
        return findMessagesPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
                .and(MESSAGE_UID.greaterOrEqual(from.asLong()))
                .and(MESSAGE_UID.lessOrEqual(to.asLong())),
            limit, fetchType);
    }

    private Flux<Pair<SimpleMailboxMessage.Builder, Record>> findMessagesPaginated(Condition condition, Limit limit, FetchType fetchType) {
        PostgresMailboxMessageFetchStrategy fetchStrategy = FETCH_TYPE_TO_FETCH_STRATEGY.apply(fetchType);
        Flux<Record> records = limit.getLimit()
            .map(limitValue -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(fetchStrategy.fetchFields())
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(condition)
                .orderBy(DEFAULT_SORT_ORDER_BY)
                .limit(limitValue)), EAGER_FETCH))
            .orElseGet(() -> postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext.select(fetchStrategy.fetchFields())
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(condition)
                .and(afterUid(lastRecord))
                .orderBy(DEFAULT_SORT_ORDER_BY)));
        return records.map(record -> Pair.of(fetchStrategy.toMessageBuilder().apply(record), record));
    }

    public Mono<Pair<SimpleMailboxMessage.Builder, Record>> findMessageByMailboxIdAndUid(PostgresMailboxId mailboxId, MessageUid uid, FetchType fetchType) {
        PostgresMailboxMessageFetchStrategy fetchStrategy = FETCH_TYPE_TO_FETCH_STRATEGY.apply(fetchType);
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(fetchStrategy.fetchFields())
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.eq(uid.asLong()))))
            .map(record -> Pair.of(fetchStrategy.toMessageBuilder().apply(record), record));
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, Record>> findMessagesByMailboxIdAndAfterUID(PostgresMailboxId mailboxId, MessageUid from, Limit limit, FetchType fetchType) {
        return findMessagesPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
                .and(MESSAGE_UID.greaterOrEqual(from.asLong())),
            limit, fetchType);
    }

    public Flux<SimpleMailboxMessage.Builder> findMessagesByMailboxIdAndUIDs(PostgresMailboxId mailboxId, List<MessageUid> uids) {
        if (uids.isEmpty()) {
            return Flux.empty();
        }
        PostgresMailboxMessageFetchStrategy fetchStrategy = PostgresMailboxMessageFetchStrategy.METADATA;
        Function<List<MessageUid>, Flux<SimpleMailboxMessage.Builder>> queryPublisherFunction =
            uidsToFetch -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(fetchStrategy.fetchFields())
                    .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                    .and(MESSAGE_UID.in(uidsToFetch.stream().map(MessageUid::asLong).toArray(Long[]::new)))
                    .orderBy(DEFAULT_SORT_ORDER_BY)), EAGER_FETCH)
                .map(fetchStrategy.toMessageBuilder());

        if (uids.size() <= IN_CLAUSE_MAX_SIZE) {
            return queryPublisherFunction.apply(uids);
        } else {
            return Flux.fromIterable(Iterables.partition(uids, IN_CLAUSE_MAX_SIZE))
                .flatMap(queryPublisherFunction);
        }
    }

    public Flux<MessageUid> findDeletedMessagesByMailboxId(PostgresMailboxId mailboxId) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(IS_DELETED.eq(true)));
    }

    public Flux<MessageUid> findDeletedMessagesByMailboxIdAndBetweenUIDs(PostgresMailboxId mailboxId, MessageUid from, MessageUid to) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(IS_DELETED.eq(true))
            .and(MESSAGE_UID.greaterOrEqual(from.asLong()))
            .and(MESSAGE_UID.lessOrEqual(to.asLong())));
    }

    public Flux<MessageUid> findDeletedMessagesByMailboxIdAndAfterUID(PostgresMailboxId mailboxId, MessageUid from) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(IS_DELETED.eq(true))
            .and(MESSAGE_UID.greaterOrEqual(from.asLong())));
    }

    public Mono<MessageUid> findDeletedMessageByMailboxIdAndUid(PostgresMailboxId mailboxId, MessageUid uid) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(MESSAGE_UID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(IS_DELETED.eq(true))
                .and(MESSAGE_UID.eq(uid.asLong()))))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<MessageUid> listNotDeletedUids(PostgresMailboxId mailboxId, MessageRange range) {
        return listUidsPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(MESSAGE_UID.greaterOrEqual(range.getUidFrom().asLong()))
            .and(MESSAGE_UID.lessOrEqual(range.getUidTo().asLong()))
            .and(IS_DELETED.eq(false)));
    }

    public Mono<Boolean> existsByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeExists(dslContext -> dslContext.selectOne()
            .from(TABLE_NAME)
            .where(MESSAGE_ID.eq(messageId.asUuid())));
    }

    public Flux<ComposedMessageIdWithMetaData> findMessagesMetadata(PostgresMailboxId mailboxId, MessageRange range) {
        return findMessagesMetadataPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(MESSAGE_UID.greaterOrEqual(range.getUidFrom().asLong()))
            .and(MESSAGE_UID.lessOrEqual(range.getUidTo().asLong())));
    }

    public Flux<ComposedMessageIdWithMetaData> findAllRecentMessageMetadata(PostgresMailboxId mailboxId) {
        return findMessagesMetadataPaginated(MAILBOX_ID.eq(mailboxId.asUuid())
            .and(IS_RECENT.eq(true)));
    }

    private Flux<ComposedMessageIdWithMetaData> findMessagesMetadataPaginated(Condition condition) {
        return postgresExecutor.executeRowsPaginated((dslContext, lastRecord) -> dslContext.select()
                .from(TABLE_NAME)
                .where(condition)
                .and(afterUid(lastRecord))
                .orderBy(DEFAULT_SORT_ORDER_BY))
            .map(RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION);
    }

    public Mono<Flags> replaceFlags(PostgresMailboxId mailboxId, MessageUid uid, Flags newFlags, ModSeq newModSeq) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(buildReplaceFlagsStatement(dslContext, newFlags, mailboxId, uid, newModSeq)
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_FLAGS_FUNCTION);
    }

    public Mono<Flags> addFlags(PostgresMailboxId mailboxId, MessageUid uid, Flags appendFlags, ModSeq newModSeq) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(buildAddFlagsStatement(dslContext, appendFlags, mailboxId, uid, newModSeq)
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_FLAGS_FUNCTION);
    }

    public Mono<Flags> removeFlags(PostgresMailboxId mailboxId, MessageUid uid, Flags removeFlags, ModSeq newModSeq) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(buildRemoveFlagsStatement(dslContext, removeFlags, mailboxId, uid, newModSeq)
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_FLAGS_FUNCTION);
    }

    private UpdateConditionStep<Record> buildAddFlagsStatement(DSLContext dslContext, Flags addFlags,
                                                               PostgresMailboxId mailboxId, MessageUid uid, ModSeq newModSeq) {
        AtomicReference<UpdateSetStep<Record>> updateStatement = new AtomicReference<>(dslContext.update(TABLE_NAME));

        BOOLEAN_FLAGS_MAPPING.forEach((flagColumn, flagMapped) -> {
            if (addFlags.contains(flagMapped)) {
                updateStatement.getAndUpdate(currentStatement -> currentStatement.set(flagColumn, true));
            }
        });

        if (addFlags.getUserFlags() != null && addFlags.getUserFlags().length > 0) {
            if (addFlags.getUserFlags().length == 1) {
                updateStatement.getAndUpdate(currentStatement -> currentStatement.set(USER_FLAGS, DSL.arrayAppend(USER_FLAGS, addFlags.getUserFlags()[0])));
            } else {
                updateStatement.getAndUpdate(currentStatement -> currentStatement.set(USER_FLAGS, DSL.arrayConcat(USER_FLAGS, addFlags.getUserFlags())));
            }
        }

        return updateStatement.get()
            .set(MOD_SEQ, newModSeq.asLong())
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_UID.eq(uid.asLong()));
    }

    private UpdateConditionStep<Record> buildReplaceFlagsStatement(DSLContext dslContext, Flags newFlags,
                                                                   PostgresMailboxId mailboxId, MessageUid uid, ModSeq newModSeq) {
        AtomicReference<UpdateSetStep<Record>> updateStatement = new AtomicReference<>(dslContext.update(TABLE_NAME));

        BOOLEAN_FLAGS_MAPPING.forEach((flagColumn, flagMapped) -> {
            updateStatement.getAndUpdate(currentStatement -> currentStatement.set(flagColumn, newFlags.contains(flagMapped)));
        });

        return updateStatement.get()
            .set(USER_FLAGS, newFlags.getUserFlags())
            .set(MOD_SEQ, newModSeq.asLong())
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_UID.eq(uid.asLong()));
    }

    private UpdateConditionStep<Record> buildRemoveFlagsStatement(DSLContext dslContext, Flags removeFlags,
                                                                  PostgresMailboxId mailboxId, MessageUid uid, ModSeq newModSeq) {
        AtomicReference<UpdateSetStep<Record>> updateStatement = new AtomicReference<>(dslContext.update(TABLE_NAME));

        BOOLEAN_FLAGS_MAPPING.forEach((flagColumn, flagMapped) -> {
            if (removeFlags.contains(flagMapped)) {
                updateStatement.getAndUpdate(currentStatement -> currentStatement.set(flagColumn, false));
            }
        });

        if (removeFlags.getUserFlags() != null && removeFlags.getUserFlags().length > 0) {
            if (removeFlags.getUserFlags().length == 1) {
                updateStatement.getAndUpdate(currentStatement -> currentStatement.set(USER_FLAGS, DSL.arrayRemove(USER_FLAGS, removeFlags.getUserFlags()[0])));
            } else {
                updateStatement.getAndUpdate(currentStatement -> currentStatement.set(USER_FLAGS, DSL.function(REMOVE_ELEMENTS_FROM_ARRAY_FUNCTION_NAME, String[].class,
                    USER_FLAGS,
                    DSL.array(removeFlags.getUserFlags()))));
            }
        }

        return updateStatement.get()
            .set(MOD_SEQ, newModSeq.asLong())
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_UID.eq(uid.asLong()));
    }

    public Mono<Flags> listDistinctUserFlags(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectDistinct(UNNEST_FIELD.apply(USER_FLAGS))
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .map(record -> record.get(0, String.class))
            .collectList()
            .map(flagList -> {
                Flags flags = new Flags();
                flagList.forEach(flags::add);
                return flags;
            });
    }

    public Flux<MessageMetaData> resetRecentFlag(PostgresMailboxId mailboxId, List<MessageUid> uids, ModSeq newModSeq) {
        if (uids.isEmpty()) {
            return Flux.empty();
        }
        Function<List<MessageUid>, Flux<MessageMetaData>> queryPublisherFunction = uidsMatching -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.update(TABLE_NAME)
                .set(IS_RECENT, false)
                .set(MOD_SEQ, newModSeq.asLong())
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.in(uidsMatching.stream().map(MessageUid::asLong).toArray(Long[]::new)))
                .and(MOD_SEQ.notEqual(newModSeq.asLong()))
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_MESSAGE_METADATA_FUNCTION);
        if (uids.size() <= IN_CLAUSE_MAX_SIZE) {
            return queryPublisherFunction.apply(uids);
        } else {
            return Flux.fromIterable(Iterables.partition(uids, IN_CLAUSE_MAX_SIZE))
                .flatMap(queryPublisherFunction);
        }
    }

    public Mono<Void> insert(MailboxMessage mailboxMessage) {
        return insert(mailboxMessage, (PostgresMailboxId) mailboxMessage.getMailboxId());
    }

    public Mono<Void> insert(MailboxMessage mailboxMessage, PostgresMailboxId mailboxId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(MAILBOX_ID, mailboxId.asUuid())
            .set(MESSAGE_UID, mailboxMessage.getUid().asLong())
            .set(MOD_SEQ, mailboxMessage.getModSeq().asLong())
            .set(MESSAGE_ID, ((PostgresMessageId) mailboxMessage.getMessageId()).asUuid())
            .set(THREAD_ID, ((PostgresMessageId) mailboxMessage.getThreadId().getBaseMessageId()).asUuid())
            .set(INTERNAL_DATE, DATE_TO_LOCAL_DATE_TIME.apply(mailboxMessage.getInternalDate()))
            .set(SIZE, mailboxMessage.getFullContentOctets())
            .set(IS_DELETED, mailboxMessage.isDeleted())
            .set(IS_ANSWERED, mailboxMessage.isAnswered())
            .set(IS_DRAFT, mailboxMessage.isDraft())
            .set(IS_FLAGGED, mailboxMessage.isFlagged())
            .set(IS_RECENT, mailboxMessage.isRecent())
            .set(IS_SEEN, mailboxMessage.isSeen())
            .set(USER_FLAGS, mailboxMessage.createFlags().getUserFlags())
            .set(SAVE_DATE, mailboxMessage.getSaveDate().map(DATE_TO_LOCAL_DATE_TIME).orElse(null))));
    }

    public Flux<MailboxId> findMailboxes(PostgresMessageId messageId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MAILBOX_ID)
                .from(TABLE_NAME)
                .where(MESSAGE_ID.eq(messageId.asUuid()))), EAGER_FETCH)
            .map(record -> PostgresMailboxId.of(record.get(MAILBOX_ID)));
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, Record>> findMessagesByMessageIds(Collection<PostgresMessageId> messageIds, MessageMapper.FetchType fetchType) {
        if (messageIds.isEmpty()) {
            return Flux.empty();
        }
        PostgresMailboxMessageFetchStrategy fetchStrategy = FETCH_TYPE_TO_FETCH_STRATEGY.apply(fetchType);

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(fetchStrategy.fetchFields())
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(DSL.field(TABLE_NAME.getName() + "." + MESSAGE_ID.getName())
                    .in(messageIds.stream().map(PostgresMessageId::asUuid).collect(ImmutableList.toImmutableList())))), EAGER_FETCH)
            .map(record -> Pair.of(fetchStrategy.toMessageBuilder().apply(record), record));
    }

    public Flux<ComposedMessageIdWithMetaData> findMetadataByMessageId(PostgresMessageId messageId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select()
                .from(TABLE_NAME)
                .where(MESSAGE_ID.eq(messageId.asUuid()))), EAGER_FETCH)
            .map(RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION);
    }

    public Flux<ComposedMessageIdWithMetaData> findMetadataByMessageId(PostgresMessageId messageId, PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select()
                .from(TABLE_NAME)
                .where(MESSAGE_ID.eq(messageId.asUuid()))
                .and(MAILBOX_ID.eq(mailboxId.asUuid()))), EAGER_FETCH)
            .map(RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION);
    }

    public Mono<Optional<Date>> retrieveInternalDate(PostgresMailboxId mailboxId, MessageUid uid) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(INTERNAL_DATE)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.eq(uid.asLong()))))
            .map(record -> Optional.of(LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(INTERNAL_DATE, LocalDateTime.class))))
            .defaultIfEmpty(Optional.empty());
    }

}
