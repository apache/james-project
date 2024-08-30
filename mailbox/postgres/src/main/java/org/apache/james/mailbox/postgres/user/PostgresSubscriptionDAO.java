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

package org.apache.james.mailbox.postgres.user;

import static org.apache.james.mailbox.postgres.user.PostgresSubscriptionModule.MAILBOX;
import static org.apache.james.mailbox.postgres.user.PostgresSubscriptionModule.TABLE_NAME;
import static org.apache.james.mailbox.postgres.user.PostgresSubscriptionModule.USER;

import org.apache.james.backends.postgres.utils.PostgresExecutor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresSubscriptionDAO {
    protected final PostgresExecutor executor;

    public PostgresSubscriptionDAO(PostgresExecutor executor) {
        this.executor = executor;
    }

    public Mono<Void> save(String username, String mailbox) {
        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME, USER, MAILBOX)
            .values(username, mailbox)
            .onConflict(USER, MAILBOX)
            .doNothing()
            .returningResult(MAILBOX)));
    }

    public Mono<Void> delete(String username, String mailbox) {
        return executor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(USER.eq(username))
            .and(MAILBOX.eq(mailbox))));
    }

    public Flux<String> findMailboxByUser(String username) {
        return executor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(USER.eq(username))))
            .map(record -> record.get(MAILBOX));
    }
}
