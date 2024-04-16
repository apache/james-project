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

import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryUrlTable.TABLE_NAME;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryUrlTable.URL;

import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailRepositoryUrlStore implements MailRepositoryUrlStore {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresMailRepositoryUrlStore(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public void add(MailRepositoryUrl url) {
        postgresExecutor.executeVoid(context -> Mono.from(context.insertInto(TABLE_NAME, URL)
                .values(url.asString())
                .onConflict(URL)
                .doNothing()))
            .block();
    }

    @Override
    public Stream<MailRepositoryUrl> listDistinct() {
        return postgresExecutor.executeRows(context -> Flux.from(context.selectFrom(TABLE_NAME)))
            .map(record -> MailRepositoryUrl.from(record.get(URL)))
            .toStream();
    }

    @Override
    public boolean contains(MailRepositoryUrl url) {
        return listDistinct().anyMatch(url::equals);
    }
}
