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

import org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaType;

import reactor.core.publisher.Mono;

public class CassandraSieveQuotaDAOV2 implements CassandraSieveQuotaDAO {
    public static final QuotaComponent QUOTA_COMPONENT = QuotaComponent.of("SIEVE");
    public static final String GLOBAL = "GLOBAL";

    private final CassandraQuotaCurrentValueDao currentValueDao;
    private final CassandraQuotaLimitDao limitDao;

    @Inject
    public CassandraSieveQuotaDAOV2(CassandraQuotaCurrentValueDao currentValueDao, CassandraQuotaLimitDao limitDao) {
        this.currentValueDao = currentValueDao;
        this.limitDao = limitDao;
    }

    @Override
    public Mono<Long> spaceUsedBy(Username username) {
        QuotaCurrentValue.Key quotaKey = asQuotaKey(username);

        return currentValueDao.getQuotaCurrentValue(quotaKey).map(QuotaCurrentValue::getCurrentValue)
            .switchIfEmpty(Mono.just(0L));
    }

    private QuotaCurrentValue.Key asQuotaKey(Username username) {
        return QuotaCurrentValue.Key.of(
            QUOTA_COMPONENT,
            username.asString(),
            QuotaType.SIZE);
    }

    @Override
    public Mono<Void> updateSpaceUsed(Username username, long spaceUsed) {
        QuotaCurrentValue.Key quotaKey = asQuotaKey(username);

        return currentValueDao.deleteQuotaCurrentValue(quotaKey)
            .then(currentValueDao.increase(quotaKey, spaceUsed));
    }

    @Override
    public Mono<Optional<QuotaSizeLimit>> getQuota() {
        return limitDao.getQuotaLimits(QUOTA_COMPONENT, QuotaScope.GLOBAL, GLOBAL)
            .map(v -> v.getQuotaLimit().map(QuotaSizeLimit::size))
            .switchIfEmpty(Mono.just(Optional.empty()))
            .single();
    }

    @Override
    public Mono<Void> setQuota(QuotaSizeLimit quota) {
        return limitDao.setQuotaLimit(QuotaLimit.builder()
            .quotaComponent(QUOTA_COMPONENT)
            .quotaScope(QuotaScope.GLOBAL)
            .quotaType(QuotaType.SIZE)
            .identifier(GLOBAL)
            .quotaLimit(quota.asLong())
            .build());
    }

    @Override
    public Mono<Void> removeQuota() {
        return limitDao.deleteQuotaLimit(QuotaLimit.QuotaLimitKey.of(QUOTA_COMPONENT, QuotaScope.GLOBAL, GLOBAL, QuotaType.SIZE));
    }

    @Override
    public Mono<Optional<QuotaSizeLimit>> getQuota(Username username) {
        return limitDao.getQuotaLimits(QUOTA_COMPONENT, QuotaScope.USER, username.asString())
            .map(v -> v.getQuotaLimit().map(QuotaSizeLimit::size))
            .switchIfEmpty(Mono.just(Optional.empty()))
            .single();
    }

    @Override
    public Mono<Void> setQuota(Username username, QuotaSizeLimit quota) {
        return limitDao.setQuotaLimit(QuotaLimit.builder()
            .quotaComponent(QUOTA_COMPONENT)
            .quotaScope(QuotaScope.USER)
            .quotaType(QuotaType.SIZE)
            .identifier(username.asString())
            .quotaLimit(quota.asLong())
            .build());
    }

    @Override
    public Mono<Void> removeQuota(Username username) {
        return limitDao.deleteQuotaLimit(QuotaLimit.QuotaLimitKey.of(
            QUOTA_COMPONENT, QuotaScope.USER, username.asString(), QuotaType.SIZE));
    }

    @Override
    public Mono<Void> resetSpaceUsed(Username username, long spaceUsed) {
        return spaceUsedBy(username).flatMap(currentSpace -> currentValueDao.increase(asQuotaKey(username), spaceUsed - currentSpace));
    }
}
