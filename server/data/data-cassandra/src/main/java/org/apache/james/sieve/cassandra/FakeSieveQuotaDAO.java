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

package org.apache.james.sieve.cassandra;


import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;

import reactor.core.publisher.Mono;

public class FakeSieveQuotaDAO implements CassandraSieveQuotaDAO {
    private static final String MESSAGE = "Use quota compatility mode in cassandra.properties for running the 12 -> 13 migration";

    @Inject
    public FakeSieveQuotaDAO() {

    }

    @Override
    public Mono<Long> spaceUsedBy(Username username) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Void> updateSpaceUsed(Username username, long spaceUsed) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Optional<QuotaSizeLimit>> getQuota() {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Void> setQuota(QuotaSizeLimit quota) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Void> removeQuota() {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Optional<QuotaSizeLimit>> getQuota(Username username) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Void> setQuota(Username username, QuotaSizeLimit quota) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Void> removeQuota(Username username) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }

    @Override
    public Mono<Void> resetSpaceUsed(Username username, long spaceUsed) {
        return Mono.error(new NotImplementedException(MESSAGE));
    }
}
