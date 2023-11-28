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

package org.apache.james.sieve.postgres;

import static org.apache.james.core.quota.QuotaType.SIZE;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.backends.postgres.quota.PostgresQuotaLimitDAO;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaSizeLimit;

import reactor.core.publisher.Mono;

public class PostgresSieveQuotaDAO {
    public static final QuotaComponent QUOTA_COMPONENT = QuotaComponent.of("SIEVE");
    public static final String GLOBAL = "GLOBAL";

    private final PostgresQuotaCurrentValueDAO currentValueDao;
    private final PostgresQuotaLimitDAO limitDao;

    @Inject
    public PostgresSieveQuotaDAO(PostgresQuotaCurrentValueDAO currentValueDao, PostgresQuotaLimitDAO limitDao) {
        this.currentValueDao = currentValueDao;
        this.limitDao = limitDao;
    }

    public Mono<Long> spaceUsedBy(Username username) {
        QuotaCurrentValue.Key quotaKey = asQuotaKey(username);

        return currentValueDao.getQuotaCurrentValue(quotaKey).map(QuotaCurrentValue::getCurrentValue)
            .switchIfEmpty(Mono.just(0L));
    }

    private QuotaCurrentValue.Key asQuotaKey(Username username) {
        return QuotaCurrentValue.Key.of(
            QUOTA_COMPONENT,
            username.asString(),
            SIZE);
    }

    public Mono<Void> updateSpaceUsed(Username username, long spaceUsed) {
        QuotaCurrentValue.Key quotaKey = asQuotaKey(username);

        return currentValueDao.increase(quotaKey, spaceUsed);
    }

    public Mono<Optional<QuotaSizeLimit>> getGlobalQuota() {
        return limitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QUOTA_COMPONENT, QuotaScope.GLOBAL, GLOBAL, SIZE))
            .map(v -> v.getQuotaLimit().map(QuotaSizeLimit::size))
            .switchIfEmpty(Mono.just(Optional.empty()));
    }

    public Mono<Void> setGlobalQuota(QuotaSizeLimit quota) {
        return limitDao.setQuotaLimit(QuotaLimit.builder()
            .quotaComponent(QUOTA_COMPONENT)
            .quotaScope(QuotaScope.GLOBAL)
            .quotaType(SIZE)
            .identifier(GLOBAL)
            .quotaLimit(quota.asLong())
            .build());
    }

    public Mono<Void> removeGlobalQuota() {
        return limitDao.deleteQuotaLimit(QuotaLimit.QuotaLimitKey.of(QUOTA_COMPONENT, QuotaScope.GLOBAL, GLOBAL, SIZE));
    }

    public Mono<Optional<QuotaSizeLimit>> getQuota(Username username) {
        return limitDao.getQuotaLimits(QUOTA_COMPONENT, QuotaScope.USER, username.asString())
            .map(v -> v.getQuotaLimit().map(QuotaSizeLimit::size))
            .switchIfEmpty(Mono.just(Optional.empty()))
            .single();
    }

    public Mono<Void> setQuota(Username username, QuotaSizeLimit quota) {
        return limitDao.setQuotaLimit(QuotaLimit.builder()
            .quotaComponent(QUOTA_COMPONENT)
            .quotaScope(QuotaScope.USER)
            .quotaType(SIZE)
            .identifier(username.asString())
            .quotaLimit(quota.asLong())
            .build());
    }

    public Mono<Void> removeQuota(Username username) {
        return limitDao.deleteQuotaLimit(QuotaLimit.QuotaLimitKey.of(
            QUOTA_COMPONENT, QuotaScope.USER, username.asString(), SIZE));
    }

    public Mono<Void> resetSpaceUsed(Username username, long spaceUsed) {
        return spaceUsedBy(username).flatMap(currentSpace -> currentValueDao.increase(asQuotaKey(username), spaceUsed - currentSpace));
    }
}
