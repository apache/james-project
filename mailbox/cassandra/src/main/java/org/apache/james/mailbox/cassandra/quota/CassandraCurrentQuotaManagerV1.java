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

package org.apache.james.mailbox.cassandra.quota;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota.MESSAGE_COUNT;
import static org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota.QUOTA_ROOT;
import static org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota.STORAGE;
import static org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota.TABLE_NAME;

import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Mono;

public class CassandraCurrentQuotaManagerV1 implements CurrentQuotaManager {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement increaseStatement;
    private final PreparedStatement decreaseStatement;
    private final PreparedStatement getCurrentMessageCountStatement;
    private final PreparedStatement getCurrentStorageStatement;
    private final PreparedStatement getCurrentQuotasStatement;

    @Inject
    public CassandraCurrentQuotaManagerV1(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.increaseStatement = session.prepare(update(TABLE_NAME)
            .increment(MESSAGE_COUNT, bindMarker())
            .increment(STORAGE, bindMarker())
            .where(column(QUOTA_ROOT).isEqualTo(bindMarker()))
            .build());
        this.decreaseStatement = session.prepare(update(TABLE_NAME)
            .decrement(MESSAGE_COUNT, bindMarker())
            .decrement(STORAGE, bindMarker())
            .where(column(QUOTA_ROOT).isEqualTo(bindMarker()))
            .build());
        this.getCurrentMessageCountStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(MESSAGE_COUNT)
            .where(column(QUOTA_ROOT).isEqualTo(bindMarker()))
            .build());
        this.getCurrentStorageStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(STORAGE)
            .where(column(QUOTA_ROOT).isEqualTo(bindMarker()))
            .build());
        this.getCurrentQuotasStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(MESSAGE_COUNT, STORAGE)
            .where(column(QUOTA_ROOT).isEqualTo(bindMarker()))
            .build());
    }

    @Override
    public Mono<Void> increase(QuotaOperation quotaOperation) {
        return cassandraAsyncExecutor.executeVoid(increaseStatement.bind(quotaOperation.count().asLong(),
            quotaOperation.size().asLong(),
            quotaOperation.quotaRoot().getValue()));
    }

    @Override
    public Mono<Void> decrease(QuotaOperation quotaOperation) {
        return cassandraAsyncExecutor.executeVoid(decreaseStatement.bind(quotaOperation.count().asLong(),
            quotaOperation.size().asLong(),
            quotaOperation.quotaRoot().getValue()));
    }

    @Override
    public Mono<QuotaCountUsage> getCurrentMessageCount(QuotaRoot quotaRoot) {
        return cassandraAsyncExecutor.executeSingleRow(getCurrentMessageCountStatement.bind(quotaRoot.getValue()))
            .map(row -> QuotaCountUsage.count(row.getLong(CassandraCurrentQuota.MESSAGE_COUNT)))
            .defaultIfEmpty(QuotaCountUsage.count(0L));
    }

    @Override
    public Mono<QuotaSizeUsage> getCurrentStorage(QuotaRoot quotaRoot) {
        return cassandraAsyncExecutor.executeSingleRow(getCurrentStorageStatement.bind(quotaRoot.getValue()))
            .map(row -> QuotaSizeUsage.size(row.getLong(CassandraCurrentQuota.STORAGE)))
            .defaultIfEmpty(QuotaSizeUsage.size(0L));
    }

    @Override
    public Mono<CurrentQuotas> getCurrentQuotas(QuotaRoot quotaRoot) {
        return cassandraAsyncExecutor.executeSingleRow(getCurrentQuotasStatement.bind(quotaRoot.getValue()))
            .map(row -> new CurrentQuotas(
                QuotaCountUsage.count(row.getLong(MESSAGE_COUNT)),
                QuotaSizeUsage.size(row.getLong(STORAGE))))
            .defaultIfEmpty(CurrentQuotas.emptyQuotas());
    }

    @Override
    public Mono<Void> setCurrentQuotas(QuotaOperation quotaOperation) {
        return getCurrentQuotas(quotaOperation.quotaRoot())
            .filter(Predicate.not(Predicate.isEqual(CurrentQuotas.from(quotaOperation))))
            .flatMap(storedQuotas -> cassandraAsyncExecutor.executeVoid(decreaseStatement.bind(storedQuotas.count().asLong(),
                    storedQuotas.size().asLong(),
                    quotaOperation.quotaRoot().asString()))
                .then(increase(quotaOperation)));
    }
}
