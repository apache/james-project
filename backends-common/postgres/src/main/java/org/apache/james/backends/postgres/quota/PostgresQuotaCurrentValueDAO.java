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

package org.apache.james.backends.postgres.quota;

import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.COMPONENT;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.CURRENT_VALUE;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.IDENTIFIER;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.TABLE_NAME;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.TYPE;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;

import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.jooq.Field;
import org.jooq.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaCurrentValueDAO {
    private static final boolean IS_INCREASE = true;

    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresQuotaCurrentValueDAO(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> increase(QuotaCurrentValue.Key quotaKey, long amount) {
        return updateCurrentValue(quotaKey, amount, IS_INCREASE)
            .switchIfEmpty(Mono.defer(() -> insert(quotaKey, amount, IS_INCREASE)))
            .then();
    }

    public Mono<Long> updateCurrentValue(QuotaCurrentValue.Key quotaKey, long amount, boolean isIncrease) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(CURRENT_VALUE, getCurrentValueOperator(isIncrease, amount))
                .where(IDENTIFIER.eq(quotaKey.getIdentifier()),
                    COMPONENT.eq(quotaKey.getQuotaComponent().getValue()),
                    TYPE.eq(quotaKey.getQuotaType().getValue()))
                .returning(CURRENT_VALUE)))
            .map(record -> record.get(CURRENT_VALUE));
    }

    public Mono<Long> upsert(QuotaCurrentValue.Key quotaKey, long newCurrentValue) {
        return update(quotaKey, newCurrentValue)
            .switchIfEmpty(Mono.defer(() -> insert(quotaKey, newCurrentValue, IS_INCREASE)));
    }

    public Mono<Long> update(QuotaCurrentValue.Key quotaKey, long newCurrentValue) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
                .set(CURRENT_VALUE, newCurrentValue)
                .where(IDENTIFIER.eq(quotaKey.getIdentifier()),
                    COMPONENT.eq(quotaKey.getQuotaComponent().getValue()),
                    TYPE.eq(quotaKey.getQuotaType().getValue()))
                .returning(CURRENT_VALUE)))
            .map(record -> record.get(CURRENT_VALUE));
    }

    private Field<Long> getCurrentValueOperator(boolean isIncrease, long amount) {
        if (isIncrease) {
            return CURRENT_VALUE.plus(amount);
        }
        return CURRENT_VALUE.minus(amount);
    }

    public Mono<Long> insert(QuotaCurrentValue.Key quotaKey, long amount, boolean isIncrease) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(IDENTIFIER, quotaKey.getIdentifier())
                .set(COMPONENT, quotaKey.getQuotaComponent().getValue())
                .set(TYPE, quotaKey.getQuotaType().getValue())
                .set(CURRENT_VALUE, newCurrentValue(amount, isIncrease))
                .returning(CURRENT_VALUE)))
            .map(record -> record.get(CURRENT_VALUE));
    }

    private Long newCurrentValue(long amount, boolean isIncrease) {
        if (isIncrease) {
            return amount;
        }
        return -amount;
    }

    public Mono<Void> decrease(QuotaCurrentValue.Key quotaKey, long amount) {
        return updateCurrentValue(quotaKey, amount, !IS_INCREASE)
            .switchIfEmpty(Mono.defer(() -> insert(quotaKey, amount, !IS_INCREASE)))
            .then();
    }

    public Mono<QuotaCurrentValue> getQuotaCurrentValue(QuotaCurrentValue.Key quotaKey) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(CURRENT_VALUE)
            .from(TABLE_NAME)
            .where(IDENTIFIER.eq(quotaKey.getIdentifier()),
                COMPONENT.eq(quotaKey.getQuotaComponent().getValue()),
                TYPE.eq(quotaKey.getQuotaType().getValue()))))
            .map(toQuotaCurrentValue(quotaKey));
    }

    public Mono<Void> deleteQuotaCurrentValue(QuotaCurrentValue.Key quotaKey) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(IDENTIFIER.eq(quotaKey.getIdentifier()),
                    COMPONENT.eq(quotaKey.getQuotaComponent().getValue()),
                    TYPE.eq(quotaKey.getQuotaType().getValue()))));
    }

    public Flux<QuotaCurrentValue> getQuotaCurrentValues(QuotaComponent quotaComponent, String identifier) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.select(TYPE, CURRENT_VALUE)
                .from(TABLE_NAME)
                .where(IDENTIFIER.eq(identifier),
                    COMPONENT.eq(quotaComponent.getValue()))))
            .map(toQuotaCurrentValue(quotaComponent, identifier));
    }

    private Function<Record, QuotaCurrentValue> toQuotaCurrentValue(QuotaCurrentValue.Key quotaKey) {
        return record -> QuotaCurrentValue.builder().quotaComponent(quotaKey.getQuotaComponent())
            .identifier(quotaKey.getIdentifier())
            .quotaType(quotaKey.getQuotaType())
            .currentValue(record.get(CURRENT_VALUE)).build();
    }

    private static Function<Record, QuotaCurrentValue> toQuotaCurrentValue(QuotaComponent quotaComponent, String identifier) {
        return record -> QuotaCurrentValue.builder().quotaComponent(quotaComponent)
                .identifier(identifier)
                .quotaType(QuotaType.of(record.get(TYPE)))
                .currentValue(record.get(CURRENT_VALUE)).build();
    }
}
