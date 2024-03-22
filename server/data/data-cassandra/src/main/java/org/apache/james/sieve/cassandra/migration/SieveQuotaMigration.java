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

package org.apache.james.sieve.cassandra.migration;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.core.Username;
import org.apache.james.sieve.cassandra.CassandraSieveQuotaDAO;
import org.apache.james.user.api.UsersRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SieveQuotaMigration implements Migration {
    private final UsersRepository usersRepository;
    private final CassandraSieveQuotaDAO oldDAO;
    private final CassandraSieveQuotaDAO newDAO;

    @Inject
    public SieveQuotaMigration(UsersRepository usersRepository, @Named("old") CassandraSieveQuotaDAO oldDAO, @Named("new") CassandraSieveQuotaDAO newDAO) {
        this.usersRepository = usersRepository;
        this.oldDAO = oldDAO;
        this.newDAO = newDAO;
    }

    @Override
    public void apply() throws InterruptedException {
        oldDAO.getQuota()
            .flatMap(maybeLimit -> maybeLimit.map(newDAO::setQuota).orElse(Mono.empty()))
            .block();

        Flux.from(usersRepository.listReactive())
            .flatMap(username -> migrateLimit(username)
                .then(migrateCurrentValue(username)))
            .then()
            .block();
    }

    private Mono<Void> migrateCurrentValue(Username username) {
        return oldDAO.spaceUsedBy(username)
            .flatMap(currentValue -> newDAO.updateSpaceUsed(username, currentValue));
    }

    private Mono<Void> migrateLimit(Username username) {
        return oldDAO.getQuota(username)
            .flatMap(maybeLimit -> maybeLimit.map(limit -> newDAO.setQuota(username, limit)).orElse(Mono.empty()));
    }
}
