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

package org.apache.james.mailrepository.postgres;

import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.ERROR;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.KEY;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.STATE;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.TABLE_NAME;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.URL;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.PostgresUtils;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailRepository implements MailRepository {
    private final PostgresExecutor postgresExecutor;
    private final MailRepositoryUrl url;

    public PostgresMailRepository(PostgresExecutor postgresExecutor, MailRepositoryUrl url) {
        this.postgresExecutor = postgresExecutor;
        this.url = url;
    }

    @Override
    public long size() throws MessagingException {
        return sizeReactive().block();
    }

    @Override
    public Mono<Long> sizeReactive() {
        return postgresExecutor.executeCount(context -> Mono.from(context.selectCount()
                .from(TABLE_NAME)
                .where(URL.eq(url.asString()))))
            .map(Integer::longValue);
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        MailKey mailKey = MailKey.forMail(mail);

        return postgresExecutor.executeVoid(context -> Mono.from(context.insertInto(TABLE_NAME)
            .set(URL, url.asString())
            .set(KEY, mailKey.asString())
            .set(STATE, mail.getState())))
            .thenReturn(mailKey)
            .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.empty())
            .block();
    }

    @Override
    public Iterator<MailKey> list() throws MessagingException {
        return postgresExecutor.executeRows(context -> Flux.from(context.select(KEY)
            .from(TABLE_NAME)
            .where(URL.eq(url.asString()))))
            .map(record -> new MailKey(record.get(KEY)))
            .toStream()
            .iterator();
    }

    @Override
    public Mail retrieve(MailKey key) {
        return postgresExecutor.executeRow(context -> Mono.from(context.select()
            .from(TABLE_NAME)
            .where(URL.eq(url.asString()))
            .and(KEY.eq(key.asString()))))
            .map(record -> MailImpl.builder()
                .name(key.asString())
                .state(record.get(STATE))
                .errorMessage(Optional.ofNullable(record.field(ERROR))
                    .map(f -> f.get(record))
                    .orElse(null))
                .build())
            .blockOptional()
            .orElse(null);
    }

    @Override
    public void remove(MailKey key) {
        removeReactive(key).block();
    }

    private Mono<Void> removeReactive(MailKey key) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(URL.eq(url.asString()))
            .and(KEY.eq(key.asString()))));
    }

    @Override
    public void remove(Collection<MailKey> keys) {
        Flux.fromIterable(keys)
            .concatMap(this::removeReactive)
            .then()
            .block();
    }

    @Override
    public void removeAll() {
        postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(URL.eq(url.asString()))))
            .block();
    }
}
