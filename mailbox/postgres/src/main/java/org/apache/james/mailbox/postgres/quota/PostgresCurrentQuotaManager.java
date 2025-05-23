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

package org.apache.james.mailbox.postgres.quota;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.quota.PostgresQuotaCurrentValueDAO;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;

import reactor.core.publisher.Mono;

public class PostgresCurrentQuotaManager implements CurrentQuotaManager {

    private final PostgresQuotaCurrentValueDAO currentValueDao;

    @Inject
    public PostgresCurrentQuotaManager(PostgresQuotaCurrentValueDAO currentValueDao) {
        this.currentValueDao = currentValueDao;
    }

    @Override
    public Mono<QuotaCountUsage> getCurrentMessageCount(QuotaRoot quotaRoot) {
        return currentValueDao.getQuotaCurrentValue(asQuotaKeyCount(quotaRoot))
            .map(QuotaCurrentValue::getCurrentValue)
            .map(QuotaCountUsage::count)
            .defaultIfEmpty(QuotaCountUsage.count(0L));
    }

    @Override
    public Mono<QuotaSizeUsage> getCurrentStorage(QuotaRoot quotaRoot) {
        return currentValueDao.getQuotaCurrentValue(asQuotaKeySize(quotaRoot))
            .map(QuotaCurrentValue::getCurrentValue)
            .map(QuotaSizeUsage::size)
            .defaultIfEmpty(QuotaSizeUsage.size(0L));
    }

    @Override
    public Mono<CurrentQuotas> getCurrentQuotas(QuotaRoot quotaRoot) {
        return currentValueDao.getQuotaCurrentValues(QuotaComponent.MAILBOX, quotaRoot.asString())
            .collectList()
            .map(this::buildCurrentQuotas);
    }

    @Override
    public Mono<Void> increase(QuotaOperation quotaOperation) {
        return currentValueDao.increase(asQuotaKeyCount(quotaOperation.quotaRoot()), quotaOperation.count().asLong())
            .then(currentValueDao.increase(asQuotaKeySize(quotaOperation.quotaRoot()), quotaOperation.size().asLong()));
    }

    @Override
    public Mono<Void> decrease(QuotaOperation quotaOperation) {
        return currentValueDao.decrease(asQuotaKeyCount(quotaOperation.quotaRoot()), quotaOperation.count().asLong())
            .then(currentValueDao.decrease(asQuotaKeySize(quotaOperation.quotaRoot()), quotaOperation.size().asLong()));
    }

    @Override
    public Mono<Void> setCurrentQuotas(QuotaOperation quotaOperation) {
        return getCurrentQuotas(quotaOperation.quotaRoot())
            .filter(Predicate.not(Predicate.isEqual(CurrentQuotas.from(quotaOperation))))
            .flatMap(storedQuotas -> {
                long count = quotaOperation.count().asLong() - storedQuotas.count().asLong();
                long size = quotaOperation.size().asLong() - storedQuotas.size().asLong();

                return currentValueDao.increase(asQuotaKeyCount(quotaOperation.quotaRoot()), count)
                    .then(currentValueDao.increase(asQuotaKeySize(quotaOperation.quotaRoot()), size));
            });
    }

    private QuotaCurrentValue.Key asQuotaKeyCount(QuotaRoot quotaRoot) {
        return asQuotaKey(quotaRoot, QuotaType.COUNT);
    }

    private QuotaCurrentValue.Key asQuotaKeySize(QuotaRoot quotaRoot) {
        return asQuotaKey(quotaRoot, QuotaType.SIZE);
    }

    private QuotaCurrentValue.Key asQuotaKey(QuotaRoot quotaRoot, QuotaType quotaType) {
        return QuotaCurrentValue.Key.of(
            QuotaComponent.MAILBOX,
            quotaRoot.asString(),
            quotaType);
    }

    private CurrentQuotas buildCurrentQuotas(List<QuotaCurrentValue> quotaCurrentValues) {
        QuotaCountUsage count = extractQuotaByType(quotaCurrentValues, QuotaType.COUNT)
            .map(value -> QuotaCountUsage.count(value.getCurrentValue()))
            .orElse(QuotaCountUsage.count(0L));

        QuotaSizeUsage size = extractQuotaByType(quotaCurrentValues, QuotaType.SIZE)
            .map(value -> QuotaSizeUsage.size(value.getCurrentValue()))
            .orElse(QuotaSizeUsage.size(0L));

        return new CurrentQuotas(count, size);
    }

    private Optional<QuotaCurrentValue> extractQuotaByType(List<QuotaCurrentValue> quotaCurrentValues, QuotaType quotaType) {
        return quotaCurrentValues.stream()
            .filter(quotaValue -> quotaValue.getQuotaType().equals(quotaType))
            .findAny();
    }
}
