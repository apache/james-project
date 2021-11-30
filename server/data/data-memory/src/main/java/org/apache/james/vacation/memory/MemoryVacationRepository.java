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

package org.apache.james.vacation.memory;

import java.util.HashMap;
import java.util.Map;

import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.api.VacationRepository;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryVacationRepository implements VacationRepository {

    private final Map<AccountId, Vacation> vacationMap;

    public MemoryVacationRepository() {
        this.vacationMap = new HashMap<>();
    }

    @Override
    public Mono<Vacation> retrieveVacation(AccountId accountId) {
        Preconditions.checkNotNull(accountId);
        return Mono.just(vacationMap.getOrDefault(accountId, DEFAULT_VACATION));
    }

    @Override
    public Mono<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(vacationPatch);
        return retrieveVacation(accountId)
            .doOnNext(oldVacation -> vacationMap.put(accountId, vacationPatch.patch(oldVacation)))
            .then();
    }


}
