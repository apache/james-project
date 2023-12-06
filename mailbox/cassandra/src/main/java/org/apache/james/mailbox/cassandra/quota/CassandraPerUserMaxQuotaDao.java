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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.cassandra.table.CassandraMaxQuota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.Limits;
import org.apache.james.mailbox.quota.QuotaCodec;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Mono;

@Deprecated() // To be removed after release 3.9.0
public class CassandraPerUserMaxQuotaDao {

    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement setMaxStorageStatement;
    private final PreparedStatement setMaxMessageStatement;
    private final PreparedStatement getMaxStatement;
    private final PreparedStatement removeMaxStorageStatement;
    private final PreparedStatement removeMaxMessageStatement;

    @Inject
    public CassandraPerUserMaxQuotaDao(CqlSession session) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.setMaxStorageStatement = session.prepare(setMaxStorageStatement().build());
        this.setMaxMessageStatement = session.prepare(setMaxMessageStatement().build());
        this.getMaxStatement = session.prepare(getMaxStatement().build());
        this.removeMaxStorageStatement = session.prepare(removeMaxStorageStatement().build());
        this.removeMaxMessageStatement = session.prepare(removeMaxMessageStatement().build());
    }

    private Delete removeMaxMessageStatement() {
        return deleteFrom(CassandraMaxQuota.TABLE_NAME).column(CassandraMaxQuota.MESSAGE_COUNT)
            .where(column(CassandraMaxQuota.QUOTA_ROOT).isEqualTo(bindMarker()));
    }

    private Delete removeMaxStorageStatement() {
        return deleteFrom(CassandraMaxQuota.TABLE_NAME).column(CassandraMaxQuota.STORAGE)
            .where(column(CassandraMaxQuota.QUOTA_ROOT).isEqualTo(bindMarker()));
    }

    private Select getMaxStatement() {
        return selectFrom(CassandraMaxQuota.TABLE_NAME)
            .all()
            .where(column(CassandraMaxQuota.QUOTA_ROOT).isEqualTo(bindMarker()));
    }

    private Insert setMaxMessageStatement() {
        return insertInto(CassandraMaxQuota.TABLE_NAME)
            .value(CassandraMaxQuota.QUOTA_ROOT, bindMarker())
            .value(CassandraMaxQuota.MESSAGE_COUNT, bindMarker());
    }

    private Insert setMaxStorageStatement() {
        return insertInto(CassandraMaxQuota.TABLE_NAME)
            .value(CassandraMaxQuota.QUOTA_ROOT, bindMarker())
            .value(CassandraMaxQuota.STORAGE, bindMarker());
    }

    Mono<Void> setMaxStorage(QuotaRoot quotaRoot, QuotaSizeLimit maxStorageQuota) {
        return queryExecutor.executeVoid(setMaxStorageStatement.bind(quotaRoot.getValue(), QuotaCodec.quotaValueToLong(maxStorageQuota)));
    }

    Mono<Void> setMaxMessage(QuotaRoot quotaRoot, QuotaCountLimit maxMessageCount) {
        return queryExecutor.executeVoid(setMaxMessageStatement.bind(quotaRoot.getValue(), QuotaCodec.quotaValueToLong(maxMessageCount)));
    }

    Mono<QuotaSizeLimit> getMaxStorage(QuotaRoot quotaRoot) {
        return queryExecutor.executeSingleRow(getMaxStatement.bind(quotaRoot.getValue()))
            .map(row -> Optional.ofNullable(row.get(CassandraMaxQuota.STORAGE, Long.class)))
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaSize)
            .handle(publishIfPresent());
    }

    Mono<QuotaCountLimit> getMaxMessage(QuotaRoot quotaRoot) {
        return queryExecutor.executeSingleRow(getMaxStatement.bind(quotaRoot.getValue()))
            .map(row -> Optional.ofNullable(row.get(CassandraMaxQuota.MESSAGE_COUNT, Long.class)))
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaCount)
            .handle(publishIfPresent());
    }

    Mono<Limits> getLimits(QuotaRoot quotaRoot) {
        return queryExecutor.executeSingleRow(getMaxStatement.bind(quotaRoot.getValue()))
            .map(row -> {
                Optional<Long> sizeLimit = Optional.ofNullable(row.get(CassandraMaxQuota.STORAGE, Long.class));
                Optional<Long> countLimit = Optional.ofNullable(row.get(CassandraMaxQuota.MESSAGE_COUNT, Long.class));

                return new Limits(
                    sizeLimit.flatMap(QuotaCodec::longToQuotaSize),
                    countLimit.flatMap(QuotaCodec::longToQuotaCount));
            })
            .switchIfEmpty(Mono.just(Limits.empty()));
    }

    Mono<Void> removeMaxMessage(QuotaRoot quotaRoot) {
        return queryExecutor.executeVoid(removeMaxMessageStatement.bind(quotaRoot.getValue()));
    }

    Mono<Void> removeMaxStorage(QuotaRoot quotaRoot) {
        return queryExecutor.executeVoid(removeMaxStorageStatement.bind(quotaRoot.getValue()));
    }
}
