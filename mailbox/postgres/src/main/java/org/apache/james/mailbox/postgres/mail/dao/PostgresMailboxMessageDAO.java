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
import static org.apache.james.backends.postgres.PostgresCommons.TABLE_FIELD;
import static org.apache.james.backends.postgres.PostgresCommons.UNNEST_FIELD;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.BLOB_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.INTERNAL_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable.SIZE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_ANSWERED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_DELETED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_DRAFT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_FLAGGED;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_RECENT;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.IS_SEEN;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MESSAGE_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MESSAGE_UID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.MOD_SEQ;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.SAVE_DATE;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.THREAD_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageToMailboxTable.USER_FLAGS;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.BOOLEAN_FLAGS_MAPPING;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.MESSAGE_METADATA_FIELDS_REQUIRE;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_MESSAGE_METADATA_FUNCTION;
import static org.apache.james.mailbox.postgres.mail.dao.PostgresMailboxMessageDAOUtils.RECORD_TO_MESSAGE_UID_FUNCTION;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.mailbox.postgres.mail.PostgresMessageModule.MessageTable;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.streams.Limit;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectFinalStep;
import org.jooq.SelectSeekStep1;
import org.jooq.SortField;
import org.jooq.TableOnConditionStep;
import org.jooq.UpdateConditionStep;
import org.jooq.UpdateSetStep;
import org.jooq.impl.DSL;

import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxMessageDAO {

    private static final TableOnConditionStep<Record> MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP = TABLE_NAME.join(MessageTable.TABLE_NAME)
        .on(TABLE_FIELD.of(TABLE_NAME, MESSAGE_ID).eq(TABLE_FIELD.of(MessageTable.TABLE_NAME, MessageTable.MESSAGE_ID)));

    public static final SortField<Long> DEFAULT_SORT_ORDER_BY = MESSAGE_UID.asc();

    private static SelectFinalStep<Record1<Long>> selectMessageUidByMailboxIdAndExtraConditionQuery(PostgresMailboxId mailboxId, Condition extraCondition, Limit limit, DSLContext dslContext) {
        SelectSeekStep1<Record1<Long>, Long> queryWithoutLimit = dslContext.select(MESSAGE_UID)
            .from(TABLE_NAME)
            .where(MAILBOX_ID.eq((mailboxId.asUuid())))
            .and(extraCondition)
            .orderBy(MESSAGE_UID.asc());
        return limit.getLimit().map(limitValue -> (SelectFinalStep<Record1<Long>>) queryWithoutLimit.limit(limitValue))
            .orElse(queryWithoutLimit);
    }

    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxMessageDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<MessageUid> findFirstUnseenMessageUid(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(selectMessageUidByMailboxIdAndExtraConditionQuery(mailboxId,
                IS_SEEN.eq(false), Limit.limit(1), dslContext)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<MessageUid> findAllRecentMessageUid(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(selectMessageUidByMailboxIdAndExtraConditionQuery(mailboxId,
                IS_RECENT.eq(true), Limit.unlimited(), dslContext)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<MessageUid> listAllMessageUid(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(selectMessageUidByMailboxIdAndExtraConditionQuery(mailboxId,
                DSL.noCondition(), Limit.unlimited(), dslContext)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Mono<MessageMetaData> deleteByMailboxIdAndMessageUid(PostgresMailboxId mailboxId, MessageUid messageUid) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.eq(messageUid.asLong()))
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_MESSAGE_METADATA_FUNCTION);
    }

    public Flux<MessageMetaData> deleteByMailboxIdAndMessageUids(PostgresMailboxId mailboxId, List<MessageUid> uids) {
        Function<List<MessageUid>, Flux<MessageMetaData>> deletePublisherFunction = uidsToDelete -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.deleteFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.in(uidsToDelete.stream().map(MessageUid::asLong).toArray(Long[]::new)))
                .returning(MESSAGE_METADATA_FIELDS_REQUIRE)))
            .map(RECORD_TO_MESSAGE_METADATA_FUNCTION);

        if (uids.size() <= IN_CLAUSE_MAX_SIZE) {
            return deletePublisherFunction.apply(uids);
        } else {
            return Flux.fromIterable(Iterables.partition(uids, IN_CLAUSE_MAX_SIZE))
                .flatMap(deletePublisherFunction);
        }
    }

    public Mono<Integer> countUnseenMessagesByMailboxId(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeCount(dslContext -> Mono.from(dslContext.selectCount()
            .from(TABLE_NAME)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(IS_SEEN.eq(false))));
    }

    public Mono<Integer> countTotalMessagesByMailboxId(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeCount(dslContext -> Mono.from(dslContext.selectCount()
            .from(TABLE_NAME)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))));
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, String>> findMessagesByMailboxId(PostgresMailboxId mailboxId, Limit limit) {
        Function<DSLContext, SelectSeekStep1<Record, Long>> queryWithoutLimit = dslContext -> dslContext.select()
            .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .orderBy(DEFAULT_SORT_ORDER_BY);

        return postgresExecutor.executeRows(dslContext -> limit.getLimit()
                .map(limitValue -> Flux.from(queryWithoutLimit.andThen(step -> step.limit(limitValue)).apply(dslContext)))
                .orElse(Flux.from(queryWithoutLimit.apply(dslContext))))
            .map(record -> Pair.of(RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION.apply(record), record.get(BLOB_ID)));
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, String>> findMessagesByMailboxIdAndBetweenUIDs(PostgresMailboxId mailboxId, MessageUid from, MessageUid to, Limit limit) {
        Function<DSLContext, SelectSeekStep1<Record, Long>> queryWithoutLimit = dslContext -> dslContext.select()
            .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_UID.greaterOrEqual(from.asLong()))
            .and(MESSAGE_UID.lessOrEqual(to.asLong()))
            .orderBy(DEFAULT_SORT_ORDER_BY);

        return postgresExecutor.executeRows(dslContext -> limit.getLimit()
                .map(limitValue -> Flux.from(queryWithoutLimit.andThen(step -> step.limit(limitValue)).apply(dslContext)))
                .orElse(Flux.from(queryWithoutLimit.apply(dslContext))))
            .map(record -> Pair.of(RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION.apply(record), record.get(BLOB_ID)));
    }

    public Mono<Pair<SimpleMailboxMessage.Builder, String>> findMessageByMailboxIdAndUid(PostgresMailboxId mailboxId, MessageUid uid) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select()
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.eq(uid.asLong()))))
            .map(record -> Pair.of(RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION.apply(record), record.get(BLOB_ID)));
    }

    public Flux<Pair<SimpleMailboxMessage.Builder, String>> findMessagesByMailboxIdAndAfterUID(PostgresMailboxId mailboxId, MessageUid from, Limit limit) {
        Function<DSLContext, SelectSeekStep1<Record, Long>> queryWithoutLimit = dslContext -> dslContext.select()
            .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_UID.greaterOrEqual(from.asLong()))
            .orderBy(DEFAULT_SORT_ORDER_BY);

        return postgresExecutor.executeRows(dslContext -> limit.getLimit()
                .map(limitValue -> Flux.from(queryWithoutLimit.andThen(step -> step.limit(limitValue)).apply(dslContext)))
                .orElse(Flux.from(queryWithoutLimit.apply(dslContext))))
            .map(record -> Pair.of(RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION.apply(record), record.get(BLOB_ID)));
    }

    public Flux<SimpleMailboxMessage.Builder> findMessagesByMailboxIdAndUIDs(PostgresMailboxId mailboxId, List<MessageUid> uids) {
        Function<List<MessageUid>, Flux<SimpleMailboxMessage.Builder>> queryPublisherFunction = uidsToFetch -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select()
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.in(uidsToFetch.stream().map(MessageUid::asLong).toArray(Long[]::new)))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_MAILBOX_MESSAGE_BUILDER_FUNCTION);

        if (uids.size() <= IN_CLAUSE_MAX_SIZE) {
            return queryPublisherFunction.apply(uids);
        } else {
            return Flux.fromIterable(Iterables.partition(uids, IN_CLAUSE_MAX_SIZE))
                .flatMap(queryPublisherFunction);
        }
    }

    public Flux<MessageUid> findDeletedMessagesByMailboxId(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_UID)
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(IS_DELETED.eq(true))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<MessageUid> findDeletedMessagesByMailboxIdAndBetweenUIDs(PostgresMailboxId mailboxId, MessageUid from, MessageUid to) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_UID)
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(IS_DELETED.eq(true))
                .and(MESSAGE_UID.greaterOrEqual(from.asLong()))
                .and(MESSAGE_UID.lessOrEqual(to.asLong()))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<MessageUid> findDeletedMessagesByMailboxIdAndAfterUID(PostgresMailboxId mailboxId, MessageUid from) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MESSAGE_UID)
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(IS_DELETED.eq(true))
                .and(MESSAGE_UID.greaterOrEqual(from.asLong()))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Mono<MessageUid> findDeletedMessageByMailboxIdAndUid(PostgresMailboxId mailboxId, MessageUid uid) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(MESSAGE_UID)
                .from(MESSAGES_JOIN_MAILBOX_MESSAGES_CONDITION_STEP)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(IS_DELETED.eq(true))
                .and(MESSAGE_UID.eq(uid.asLong()))))
            .map(RECORD_TO_MESSAGE_UID_FUNCTION);
    }

    public Flux<ComposedMessageIdWithMetaData> findMessagesMetadata(PostgresMailboxId mailboxId, MessageRange range) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select()
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.greaterOrEqual(range.getUidFrom().asLong()))
                .and(MESSAGE_UID.lessOrEqual(range.getUidTo().asLong()))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION);
    }

    public Flux<ComposedMessageIdWithMetaData> findMessagesMetadata(PostgresMailboxId mailboxId, List<MessageUid> messageUids) {
        Function<List<MessageUid>, Flux<ComposedMessageIdWithMetaData>> queryPublisherFunction = uidsToFetch -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select()
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(MESSAGE_UID.in(uidsToFetch.stream().map(MessageUid::asLong).toArray(Long[]::new)))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION);

        if (messageUids.size() <= IN_CLAUSE_MAX_SIZE) {
            return queryPublisherFunction.apply(messageUids);
        } else {
            return Flux.fromIterable(Iterables.partition(messageUids, IN_CLAUSE_MAX_SIZE))
                .flatMap(queryPublisherFunction);
        }
    }

    public Flux<ComposedMessageIdWithMetaData> findAllRecentMessageMetadata(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select()
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))
                .and(IS_RECENT.eq(true))
                .orderBy(DEFAULT_SORT_ORDER_BY)))
            .map(RECORD_TO_COMPOSED_MESSAGE_ID_WITH_META_DATA_FUNCTION);
    }

    public Mono<Void> updateFlag(PostgresMailboxId mailboxId, MessageUid uid, UpdatedFlags updatedFlags) {
        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(buildUpdateFlagStatement(dslContext, updatedFlags, mailboxId, uid)));
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

    private UpdateConditionStep<Record> buildUpdateFlagStatement(DSLContext dslContext, UpdatedFlags updatedFlags,
                                                                 PostgresMailboxId mailboxId, MessageUid uid) {
        AtomicReference<UpdateSetStep<Record>> updateStatement = new AtomicReference<>(dslContext.update(TABLE_NAME));

        BOOLEAN_FLAGS_MAPPING.forEach((flagColumn, flagMapped) -> {
            if (updatedFlags.isChanged(flagMapped)) {
                updateStatement.getAndUpdate(currentStatement -> {
                    if (flagMapped.equals(Flag.RECENT)) {
                        return currentStatement.set(flagColumn, updatedFlags.getNewFlags().contains(Flag.RECENT));
                    }
                    return currentStatement.set(flagColumn, updatedFlags.isModifiedToSet(flagMapped));
                });
            }
        });

        return updateStatement.get()
            .set(USER_FLAGS, updatedFlags.getNewFlags().getUserFlags())
            .where(MAILBOX_ID.eq(mailboxId.asUuid()))
            .and(MESSAGE_UID.eq(uid.asLong()));
    }

    public Flux<MessageMetaData> resetRecentFlag(PostgresMailboxId mailboxId, List<MessageUid> uids, ModSeq newModSeq) {
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
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(MAILBOX_ID, ((PostgresMailboxId) mailboxMessage.getMailboxId()).asUuid())
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

}
