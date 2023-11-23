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
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.zero;

import java.util.Objects;
import java.util.function.Function;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.jooq.Record;

import com.google.common.base.MoreObjects;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaCurrentValueDAO {
    public static class QuotaKey {

        public static QuotaKey of(QuotaComponent component, String identifier, QuotaType quotaType) {
            return new QuotaKey(component, identifier, quotaType);
        }

        private final QuotaComponent quotaComponent;
        private final String identifier;
        private final QuotaType quotaType;

        public QuotaComponent getQuotaComponent() {
            return quotaComponent;
        }

        public String getIdentifier() {
            return identifier;
        }

        public QuotaType getQuotaType() {
            return quotaType;
        }

        private QuotaKey(QuotaComponent quotaComponent, String identifier, QuotaType quotaType) {
            this.quotaComponent = quotaComponent;
            this.identifier = identifier;
            this.quotaType = quotaType;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(quotaComponent, identifier, quotaType);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof QuotaKey) {
                QuotaKey other = (QuotaKey) o;
                return Objects.equals(quotaComponent, other.quotaComponent)
                    && Objects.equals(identifier, other.identifier)
                    && Objects.equals(quotaType, other.quotaType);
            }
            return false;
        }

        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("quotaComponent", quotaComponent)
                .add("identifier", identifier)
                .add("quotaType", quotaType)
                .toString();
        }
    }

    private final PostgresExecutor postgresExecutor;

    public PostgresQuotaCurrentValueDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> increase(QuotaKey quotaKey, long amount) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(IDENTIFIER, quotaKey.getIdentifier())
            .set(COMPONENT, quotaKey.getQuotaComponent().getValue())
            .set(TYPE, quotaKey.getQuotaType().getValue())
            .set(CURRENT_VALUE, amount)
            .onConflict(IDENTIFIER, COMPONENT, TYPE)
            .doUpdate()
            .set(CURRENT_VALUE, CURRENT_VALUE.plus(amount))));
    }

    public Mono<Void> decrease(QuotaKey quotaKey, long amount) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(IDENTIFIER, quotaKey.getIdentifier())
            .set(COMPONENT, quotaKey.getQuotaComponent().getValue())
            .set(TYPE, quotaKey.getQuotaType().getValue())
            .set(CURRENT_VALUE, 0L)
            .onConflict(IDENTIFIER, COMPONENT, TYPE)
            .doUpdate()
            .set(CURRENT_VALUE, greatest(CURRENT_VALUE.minus(amount),
                zero()))));
    }

    public Mono<QuotaCurrentValue> getQuotaCurrentValue(QuotaKey quotaKey) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(CURRENT_VALUE)
            .from(TABLE_NAME)
            .where(IDENTIFIER.eq(quotaKey.getIdentifier()),
                COMPONENT.eq(quotaKey.getQuotaComponent().getValue()),
                TYPE.eq(quotaKey.getQuotaType().getValue()))))
            .map(toQuotaCurrentValue(quotaKey));
    }

    public Mono<Void> deleteQuotaCurrentValue(QuotaKey quotaKey) {
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

    private Function<Record, QuotaCurrentValue> toQuotaCurrentValue(QuotaKey quotaKey) {
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
