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
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.PRIMARY_KEY_CONSTRAINT_NAME;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.TABLE_NAME;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaCurrentValueTable.TYPE;

import java.util.function.Function;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaCurrentValueDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresQuotaCurrentValueDAO.class);

    private final PostgresExecutor postgresExecutor;

    public PostgresQuotaCurrentValueDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> increase(QuotaCurrentValue.Key quotaKey, long amount) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(IDENTIFIER, quotaKey.getIdentifier())
            .set(COMPONENT, quotaKey.getQuotaComponent().getValue())
            .set(TYPE, quotaKey.getQuotaType().getValue())
            .set(CURRENT_VALUE, amount)
            .onConflictOnConstraint(PRIMARY_KEY_CONSTRAINT_NAME)
            .doUpdate()
            .set(CURRENT_VALUE, CURRENT_VALUE.plus(amount))))
            .onErrorResume(ex -> {
                LOGGER.warn("Failure when increasing {} {} quota for {}. Quota current value is thus not updated and needs re-computation",
                    quotaKey.getQuotaComponent().getValue(), quotaKey.getQuotaType().getValue(), quotaKey.getIdentifier(), ex);
                return Mono.empty();
            });
    }

    public Mono<Void> decrease(QuotaCurrentValue.Key quotaKey, long amount) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(IDENTIFIER, quotaKey.getIdentifier())
            .set(COMPONENT, quotaKey.getQuotaComponent().getValue())
            .set(TYPE, quotaKey.getQuotaType().getValue())
            .set(CURRENT_VALUE, -amount)
            .onConflictOnConstraint(PRIMARY_KEY_CONSTRAINT_NAME)
            .doUpdate()
            .set(CURRENT_VALUE, CURRENT_VALUE.minus(amount))))
            .onErrorResume(ex -> {
                LOGGER.warn("Failure when decreasing {} {} quota for {}. Quota current value is thus not updated and needs re-computation",
                    quotaKey.getQuotaComponent().getValue(), quotaKey.getQuotaType().getValue(), quotaKey.getIdentifier(), ex);
                return Mono.empty();
            });
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
