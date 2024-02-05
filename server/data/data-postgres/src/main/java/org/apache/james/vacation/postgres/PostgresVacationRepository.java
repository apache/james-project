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

package org.apache.james.vacation.postgres;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.api.VacationRepository;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class PostgresVacationRepository implements VacationRepository {
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    @Singleton
    public PostgresVacationRepository(PostgresExecutor.Factory executorFactory) {
        this.executorFactory = executorFactory;
    }

    @Override
    public Mono<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(vacationPatch);
        if (vacationPatch.isIdentity()) {
            return Mono.empty();
        } else {
            return vacationResponseDao(accountId).modifyVacation(accountId, vacationPatch);
        }
    }

    @Override
    public Mono<Vacation> retrieveVacation(AccountId accountId) {
        return vacationResponseDao(accountId).retrieveVacation(accountId).map(optional -> optional.orElse(DEFAULT_VACATION));
    }

    private PostgresVacationResponseDAO vacationResponseDao(AccountId accountId) {
        return new PostgresVacationResponseDAO(executorFactory.create(Username.of(accountId.getIdentifier()).getDomainPart()));
    }
}
