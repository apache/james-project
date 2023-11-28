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

import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.IDENTIFIER;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.PK_CONSTRAINT_NAME;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.QUOTA_COMPONENT;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.QUOTA_LIMIT;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.QUOTA_SCOPE;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.QUOTA_TYPE;
import static org.apache.james.backends.postgres.quota.PostgresQuotaModule.PostgresQuotaLimitTable.TABLE_NAME;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaType;
import org.jooq.Record;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaLimitDAO {
    private static final Long EMPTY_QUOTA_LIMIT = null;

    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresQuotaLimitDAO(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<QuotaLimit> getQuotaLimit(QuotaLimit.QuotaLimitKey quotaKey) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(TABLE_NAME)
                .where(QUOTA_COMPONENT.eq(quotaKey.getQuotaComponent().getValue()))
                .and(QUOTA_SCOPE.eq(quotaKey.getQuotaScope().getValue()))
                .and(IDENTIFIER.eq(quotaKey.getIdentifier()))
                .and(QUOTA_TYPE.eq(quotaKey.getQuotaType().getValue()))))
            .map(this::asQuotaLimit);
    }

    public Flux<QuotaLimit> getQuotaLimits(QuotaComponent quotaComponent, QuotaScope quotaScope, String identifier) {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(QUOTA_COMPONENT.eq(quotaComponent.getValue()))
                .and(QUOTA_SCOPE.eq(quotaScope.getValue()))
                .and(IDENTIFIER.eq(identifier))))
            .map(this::asQuotaLimit);
    }

    public Mono<Void> setQuotaLimit(QuotaLimit quotaLimit) {
        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, QUOTA_SCOPE, IDENTIFIER, QUOTA_COMPONENT, QUOTA_TYPE, QUOTA_LIMIT)
                .values(quotaLimit.getQuotaScope().getValue(),
                    quotaLimit.getIdentifier(),
                    quotaLimit.getQuotaComponent().getValue(),
                    quotaLimit.getQuotaType().getValue(),
                    quotaLimit.getQuotaLimit().orElse(EMPTY_QUOTA_LIMIT))
                .onConflictOnConstraint(PK_CONSTRAINT_NAME)
                .doUpdate()
                .set(QUOTA_LIMIT, quotaLimit.getQuotaLimit().orElse(EMPTY_QUOTA_LIMIT))));
    }

    public Mono<Void> deleteQuotaLimit(QuotaLimit.QuotaLimitKey quotaKey) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(QUOTA_COMPONENT.eq(quotaKey.getQuotaComponent().getValue()))
            .and(QUOTA_SCOPE.eq(quotaKey.getQuotaScope().getValue()))
            .and(IDENTIFIER.eq(quotaKey.getIdentifier()))
            .and(QUOTA_TYPE.eq(quotaKey.getQuotaType().getValue()))));
    }

    private QuotaLimit asQuotaLimit(Record record) {
        return QuotaLimit.builder().quotaComponent(QuotaComponent.of(record.get(QUOTA_COMPONENT)))
            .quotaScope(QuotaScope.of(record.get(QUOTA_SCOPE)))
            .identifier(record.get(IDENTIFIER))
            .quotaType(QuotaType.of(record.get(QUOTA_TYPE)))
            .quotaLimit(record.get(QUOTA_LIMIT))
            .build();
    }
}
