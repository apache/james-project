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

package org.apache.james.mailbox.jpa.mail.dao;

import static org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable.MAILBOX_ID;
import static org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable.MAILBOX_NAME;
import static org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable.MAILBOX_NAMESPACE;
import static org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable.MAILBOX_UID_VALIDITY;
import static org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable.USER_NAME;
import static org.jooq.impl.DSL.count;

import java.util.NoSuchElementException;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.jpa.mail.table.PostgresMailboxTable;
import org.apache.james.mailbox.jpa.user.PostgresMailboxId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.jooq.Record1;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxDAOImpl implements PostgresMailboxDAO {
    private static final char SQL_WILDCARD_CHAR = '%';

    private PostgresExecutor postgresExecutor;

    @Inject
    public PostgresMailboxDAOImpl(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public Mono<Mailbox> create(MailboxPath mailboxPath, UidValidity uidValidity) {
        return postgresExecutor.dslContext()
            .flatMapMany(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME, MAILBOX_NAME, USER_NAME, MAILBOX_NAMESPACE, MAILBOX_UID_VALIDITY)
                .values(mailboxPath.getName(), mailboxPath.getUser().asString(), mailboxPath.getNamespace(), uidValidity.asLong())
                .returningResult(MAILBOX_ID))
                .map(record -> new Mailbox(mailboxPath, uidValidity, PostgresMailboxId.of(record.get(MAILBOX_ID)))))
            .last()
            .onErrorResume(throwable -> {
                if (throwable.getMessage().contains("duplicate key value violates unique constraint")) {
                    return Mono.error(new MailboxExistsException(mailboxPath.getName()));
                } else {
                    return Mono.error(throwable);
                }
            });
    }

    @Override
    public Mono<MailboxId> rename(Mailbox mailbox) {
        Preconditions.checkNotNull(mailbox.getMailboxId(), "A mailbox we want to rename should have a defined mailboxId");

        return postgresExecutor.dslContext()
            .flatMap(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(MAILBOX_NAME, mailbox.getName())
                .set(USER_NAME, mailbox.getUser().asString())
                .set(MAILBOX_NAMESPACE, mailbox.getNamespace())))
            .thenReturn(mailbox.getMailboxId())
            .onErrorResume(throwable -> {
                if (throwable.getMessage().contains("duplicate key value violates unique constraint")) {
                    return Mono.error(new MailboxExistsException(mailbox.getName()));
                } else {
                    return Mono.error(throwable);
                }
            });
    }

    @Override
    public Mono<Void> delete(MailboxId mailboxId) {
        return postgresExecutor.dslContext()
            .flatMap(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(((PostgresMailboxId) mailboxId).getRawId()))))
            .then();
    }

    @Override
    public Mono<Mailbox> findMailboxByPath(MailboxPath mailboxPath) {
        return postgresExecutor.dslContext()
            .flatMapMany(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(MAILBOX_NAME.eq(mailboxPath.getName())
                    .and(USER_NAME.eq(mailboxPath.getUser().asString()))
                    .and(MAILBOX_NAMESPACE.eq(mailboxPath.getNamespace()))))
                .map(record -> generateMailbox(record.get(PostgresMailboxTable.MAILBOX_ID),
                    record.get(MAILBOX_NAMESPACE),
                    record.get(USER_NAME),
                    record.get(MAILBOX_NAME),
                    record.get(MAILBOX_UID_VALIDITY))))
            .last()
            .onErrorResume(NoSuchElementException.class, e -> Mono.empty());
    }

    @Override
    public Mono<Mailbox> findMailboxById(MailboxId id) {
        return postgresExecutor.dslContext()
            .flatMapMany(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                    .where(MAILBOX_ID.eq(((PostgresMailboxId) id).getRawId())))
                .map(record -> generateMailbox(record.get(PostgresMailboxTable.MAILBOX_ID),
                    record.get(MAILBOX_NAMESPACE),
                    record.get(USER_NAME),
                    record.get(MAILBOX_NAME),
                    record.get(MAILBOX_UID_VALIDITY))))
            .last()
            .onErrorResume(NoSuchElementException.class, e -> Mono.empty());
    }

    @Override
    public Flux<Mailbox> findMailboxWithPathLike(MailboxQuery.UserBound query) {
        return null;
    }

    @Override
    public Mono<Boolean> hasChildren(Mailbox mailbox, char delimiter) {
        final String name = mailbox.getName() + delimiter + SQL_WILDCARD_CHAR;

        return postgresExecutor.dslContext()
            .flatMapMany(dsl -> Flux.from(dsl.select(count()).from(TABLE_NAME)
                    .where(MAILBOX_NAME.like(name)
                        .and(USER_NAME.eq(mailbox.getUser().asString()))
                        .and(MAILBOX_NAMESPACE.eq(mailbox.getNamespace()))))
                .map(Record1::value1)
                .filter(count -> count > 0))
            .hasElements();
    }

    @Override
    public Flux<Mailbox> getAll() {
        return postgresExecutor.dslContext()
            .flatMapMany(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)))
                .map(record -> generateMailbox(record.get(PostgresMailboxTable.MAILBOX_ID),
                    record.get(MAILBOX_NAMESPACE),
                    record.get(USER_NAME),
                    record.get(MAILBOX_NAME),
                    record.get(MAILBOX_UID_VALIDITY)));
    }

    private Mailbox generateMailbox(long mailboxId, String namespace, String user, String name, long uidValidity) {
        MailboxPath path = new MailboxPath(namespace, Username.of(user), name);
        return new Mailbox(path, sanitizeUidValidity(uidValidity), new PostgresMailboxId(mailboxId));
    }

    private UidValidity sanitizeUidValidity(long uidValidity) {
        if (UidValidity.isValid(uidValidity)) {
            return UidValidity.of(uidValidity);
        }
        return UidValidity.generate();
    }
}
