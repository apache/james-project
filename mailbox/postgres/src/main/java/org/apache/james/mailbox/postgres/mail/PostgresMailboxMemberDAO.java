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

package org.apache.james.mailbox.postgres.mail;

import static org.apache.james.mailbox.postgres.mail.PostgresMailboxMemberModule.PostgresMailboxMemberTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxMemberModule.PostgresMailboxMemberTable.TABLE_NAME;
import static org.apache.james.mailbox.postgres.mail.PostgresMailboxMemberModule.PostgresMailboxMemberTable.USER_NAME;

import java.util.List;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.postgres.PostgresMailboxId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxMemberDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxMemberDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Flux<PostgresMailboxId> findMailboxIdByUsername(Username username) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(MAILBOX_ID)
                .from(TABLE_NAME)
                .where(USER_NAME.eq(username.asString()))))
            .map(record -> PostgresMailboxId.of(record.get(MAILBOX_ID)));
    }

    public Mono<Void> insert(PostgresMailboxId mailboxId, List<Username> usernames) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(usernames.stream()      //TODO check issue: batch does not throw exception
            .map(username -> dslContext.insertInto(TABLE_NAME)
                .set(USER_NAME, username.asString())
                .set(MAILBOX_ID, mailboxId.asUuid()))
            .toList())));
    }

    public Mono<Void> delete(PostgresMailboxId mailboxId, List<Username> usernames) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(usernames.stream()
            .map(username -> dslContext.deleteFrom(TABLE_NAME)
                .where(USER_NAME.eq(username.asString())
                .and(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .toList())));
    }
}
