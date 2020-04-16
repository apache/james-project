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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.decr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.cassandra.table.CassandraCurrentQuota;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.StoreCurrentQuotaManager;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class CassandraCurrentQuotaManager implements StoreCurrentQuotaManager {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement increaseStatement;
    private final PreparedStatement decreaseStatement;
    private final PreparedStatement getCurrentMessageCountStatement;
    private final PreparedStatement getCurrentStorageStatement;

    @Inject
    public CassandraCurrentQuotaManager(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.increaseStatement = session.prepare(update(CassandraCurrentQuota.TABLE_NAME)
            .with(incr(CassandraCurrentQuota.MESSAGE_COUNT, bindMarker()))
            .and(incr(CassandraCurrentQuota.STORAGE, bindMarker()))
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
        this.decreaseStatement = session.prepare(update(CassandraCurrentQuota.TABLE_NAME)
            .with(decr(CassandraCurrentQuota.MESSAGE_COUNT, bindMarker()))
            .and(decr(CassandraCurrentQuota.STORAGE, bindMarker()))
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
        this.getCurrentMessageCountStatement = session.prepare(select(CassandraCurrentQuota.MESSAGE_COUNT)
            .from(CassandraCurrentQuota.TABLE_NAME)
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
        this.getCurrentStorageStatement = session.prepare(select(CassandraCurrentQuota.STORAGE)
            .from(CassandraCurrentQuota.TABLE_NAME)
            .where(eq(CassandraCurrentQuota.QUOTA_ROOT, bindMarker())));
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
}
