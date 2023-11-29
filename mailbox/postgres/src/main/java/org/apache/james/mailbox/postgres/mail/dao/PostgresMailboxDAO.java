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

import static org.apache.james.backends.postgres.utils.PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE;
import static org.apache.james.mailbox.postgres.PostgresMailboxIdFaker.getMailboxId;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_ACL;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_HIGHEST_MODSEQ;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_LAST_UID;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_NAMESPACE;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.MAILBOX_UID_VALIDITY;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxModule.PostgresMailboxTable.USER_NAME;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.apache.james.mailbox.store.MailboxExpressionBackwardCompatibility;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Hstore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresMailboxDAO.class);
    private static final char SQL_WILDCARD_CHAR = '%';
    private static final Function<MailboxACL, Hstore> MAILBOX_ACL_TO_HSTORE_FUNCTION = acl -> Hstore.hstore(acl.getEntries()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().serialize(),
            entry -> entry.getValue().serialize())));

    private static final Function<Hstore, MailboxACL> HSTORE_TO_MAILBOX_ACL_FUNCTION = hstore -> new MailboxACL(hstore.data()
        .entrySet()
        .stream()
        .map(entry -> deserializeMailboxACLEntry(entry.getKey(), entry.getValue()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)));

    private static Optional<Map.Entry<MailboxACL.EntryKey, MailboxACL.Rfc4314Rights>> deserializeMailboxACLEntry(String key, String value) {
        try {
            MailboxACL.EntryKey entryKey = MailboxACL.EntryKey.deserialize(key);
            MailboxACL.Rfc4314Rights rfc4314Rights = MailboxACL.Rfc4314Rights.deserialize(value);
            return Optional.of(Map.entry(entryKey, rfc4314Rights));
        } catch (UnsupportedRightException e) {
            LOGGER.error("Error while deserializing mailbox ACL", e);
            return Optional.empty();
        }
    }

    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        final PostgresMailboxId mailboxId = PostgresMailboxId.generate();

        return postgresExecutor.executeVoid(dslContext ->
                Mono.from(dslContext.insertInto(TABLE_NAME, MAILBOX_ID, MAILBOX_NAME, USER_NAME, MAILBOX_NAMESPACE, MAILBOX_UID_VALIDITY)
                    .values(mailboxId.asUuid(), mailboxPath.getName(), mailboxPath.getUser().asString(), mailboxPath.getNamespace(), uidValidity.asLong())))
            .thenReturn(new Mailbox(mailboxPath, uidValidity, mailboxId))
            .onErrorMap(UNIQUE_CONSTRAINT_VIOLATION_PREDICATE,
                e -> new MailboxExistsException(mailboxPath.getName()));
    }

    public Mono<MailboxId> rename(Mailbox mailbox) {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        return findMailboxByPath(mailbox.generateAssociatedPath())
            .flatMap(m -> Mono.error(new MailboxExistsException(mailbox.getName())))
            .then(update(mailbox));
    }

    private Mono<MailboxId> update(Mailbox mailbox) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(MAILBOX_NAME, mailbox.getName())
                .set(USER_NAME, mailbox.getUser().asString())
                .set(MAILBOX_NAMESPACE, mailbox.getNamespace())
                .where(MAILBOX_ID.eq(((PostgresMailboxId) mailbox.getMailboxId()).asUuid()))
                .returning(MAILBOX_ID)))
            .map(record -> mailbox.getMailboxId())
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(mailbox.getMailboxId())));
    }

    public Mono<MailboxACL> upsertACL(MailboxId mailboxId, MailboxACL acl) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(MAILBOX_ACL, MAILBOX_ACL_TO_HSTORE_FUNCTION.apply(acl))
            .where(MAILBOX_ID.eq(((PostgresMailboxId) mailboxId).asUuid()))
            .returning(MAILBOX_ACL)))
            .map(record -> HSTORE_TO_MAILBOX_ACL_FUNCTION.apply(record.get(MAILBOX_ACL)));
    }

    public Flux<Mailbox> findNonPersonalMailboxes(Username userName, MailboxACL.Right right) {
        String mailboxACLEntryByUser = String.format("mailbox_acl -> '%s'", userName.asString());

        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(MAILBOX_ACL.isNotNull(),
                    DSL.field(mailboxACLEntryByUser).isNotNull(),
                    DSL.field(mailboxACLEntryByUser).contains(Character.toString(right.asCharacter())))))
            .map(this::asMailbox);
    }

    public Mono<Void> delete(MailboxId mailboxId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MAILBOX_ID.eq(((PostgresMailboxId) mailboxId).asUuid()))));
    }

    public Mono<Mailbox> findMailboxByPath(MailboxPath mailboxPath) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(TABLE_NAME)
            .where(MAILBOX_NAME.eq(mailboxPath.getName())
                .and(USER_NAME.eq(mailboxPath.getUser().asString()))
                .and(MAILBOX_NAMESPACE.eq(mailboxPath.getNamespace())))))
            .map(this::asMailbox);
    }

    public Mono<Mailbox> findMailboxById(MailboxId id) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(TABLE_NAME)
            .where(MAILBOX_ID.eq(((PostgresMailboxId) id).asUuid()))))
            .map(this::asMailbox)
            .switchIfEmpty(Mono.error(new MailboxNotFoundException(id)));
    }

    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        String pathLike = MailboxExpressionBackwardCompatibility.getPathLike(query);

        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
            .where(MAILBOX_NAME.like(pathLike)
                .and(USER_NAME.eq(query.getFixedUser().asString()))
                .and(MAILBOX_NAMESPACE.eq(query.getFixedNamespace())))))
            .map(this::asMailbox)
            .filter(query::matches);
    }

    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        String name = mailbox.getName() + delimiter + SQL_WILDCARD_CHAR;

        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(count()).from(TABLE_NAME)
                .where(MAILBOX_NAME.like(name)
                    .and(USER_NAME.eq(mailbox.getUser().asString()))
                    .and(MAILBOX_NAMESPACE.eq(mailbox.getNamespace())))))
            .map(record -> record.get(0, Integer.class))
            .filter(count -> count > 0)
            .hasElements();
    }

    public Flux<Mailbox> getAll() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)))
            .map(this::asMailbox);
    }

    private Mailbox asMailbox(Record record) {
        Mailbox mailbox = new Mailbox(new MailboxPath(record.get(MAILBOX_NAMESPACE), Username.of(record.get(USER_NAME)), record.get(MAILBOX_NAME)),
            UidValidity.of(record.get(MAILBOX_UID_VALIDITY)), PostgresMailboxId.of(record.get(MAILBOX_ID)));
        mailbox.setACL(HSTORE_TO_MAILBOX_ACL_FUNCTION.apply(Hstore.hstore(record.get(MAILBOX_ACL, LinkedHashMap.class))));
        return mailbox;
    }

    public Mono<MessageUid> findLastUidByMailboxId(MailboxId mailboxId) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.select(MAILBOX_LAST_UID)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(getMailboxId(mailboxId).asUuid()))))
            .flatMap(record -> Mono.justOrEmpty(record.get(MAILBOX_LAST_UID)))
            .map(MessageUid::of);
    }

    public Mono<MessageUid> incrementAndGetLastUid(MailboxId mailboxId, int count) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.update(TABLE_NAME)
                .set(MAILBOX_LAST_UID, coalesce(MAILBOX_LAST_UID, 0L).add(count))
                .where(MAILBOX_ID.eq(getMailboxId(mailboxId).asUuid()))
                .returning(MAILBOX_LAST_UID)))
            .map(record -> record.get(MAILBOX_LAST_UID))
            .map(MessageUid::of);
    }


    public Mono<ModSeq> findHighestModSeqByMailboxId(MailboxId mailboxId) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.select(MAILBOX_HIGHEST_MODSEQ)
                .from(TABLE_NAME)
                .where(MAILBOX_ID.eq(getMailboxId(mailboxId).asUuid()))))
            .flatMap(record -> Mono.justOrEmpty(record.get(MAILBOX_HIGHEST_MODSEQ)))
            .map(ModSeq::of);
    }

    public Mono<ModSeq> incrementAndGetModSeq(MailboxId mailboxId) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.update(TABLE_NAME)
                .set(MAILBOX_HIGHEST_MODSEQ, coalesce(MAILBOX_HIGHEST_MODSEQ, 0L).add(1))
                .where(MAILBOX_ID.eq(getMailboxId(mailboxId).asUuid()))
                .returning(MAILBOX_HIGHEST_MODSEQ)))
            .map(record -> record.get(MAILBOX_HIGHEST_MODSEQ))
            .map(ModSeq::of);
    }

    public Mono<Pair<MessageUid, ModSeq>> incrementAndGetLastUidAndModSeq(MailboxId mailboxId) {
        int increment = 1;
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.update(TABLE_NAME)
                .set(MAILBOX_LAST_UID, coalesce(MAILBOX_LAST_UID, 0L).add(increment))
                .set(MAILBOX_HIGHEST_MODSEQ, coalesce(MAILBOX_HIGHEST_MODSEQ, 0L).add(increment))
                .where(MAILBOX_ID.eq(getMailboxId(mailboxId).asUuid()))
                .returning(MAILBOX_LAST_UID, MAILBOX_HIGHEST_MODSEQ)))
            .map(record -> Pair.of(MessageUid.of(record.get(MAILBOX_LAST_UID)), ModSeq.of(record.get(MAILBOX_HIGHEST_MODSEQ))));
    }
}
