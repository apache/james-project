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
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.cassandra.table.CassandraDomainMaxQuota;
import org.apache.james.mailbox.quota.Limits;
import org.apache.james.mailbox.quota.QuotaCodec;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Mono;

@Deprecated() // To be removed after release 3.9.0
public class CassandraPerDomainMaxQuotaDao {
    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement setMaxStorageStatement;
    private final PreparedStatement setMaxMessageStatement;
    private final PreparedStatement getMaxStatement;
    private final PreparedStatement removeMaxStorageStatement;
    private final PreparedStatement removeMaxMessageStatement;

    @Inject
    public CassandraPerDomainMaxQuotaDao(CqlSession session) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.setMaxStorageStatement = session.prepare(setMaxStorageStatement().build());
        this.setMaxMessageStatement = session.prepare(setMaxMessageStatement().build());
        this.getMaxStatement = session.prepare(getMaxStatement().build());
        this.removeMaxStorageStatement = session.prepare(removeMaxStorageStatement().build());
        this.removeMaxMessageStatement = session.prepare(removeMaxMessageStatement().build());
    }

    private Delete removeMaxMessageStatement() {
        return deleteFrom(CassandraDomainMaxQuota.TABLE_NAME).column(CassandraDomainMaxQuota.MESSAGE_COUNT)
            .where(column(CassandraDomainMaxQuota.DOMAIN).isEqualTo(bindMarker()));
    }

    private Delete removeMaxStorageStatement() {
        return deleteFrom(CassandraDomainMaxQuota.TABLE_NAME).column(CassandraDomainMaxQuota.STORAGE)
            .where(column(CassandraDomainMaxQuota.DOMAIN).isEqualTo(bindMarker()));
    }

    private Select getMaxStatement() {
        return selectFrom(CassandraDomainMaxQuota.TABLE_NAME)
            .all()
            .where(column(CassandraDomainMaxQuota.DOMAIN).isEqualTo(bindMarker()));
    }

    private Insert setMaxMessageStatement() {
        return insertInto(CassandraDomainMaxQuota.TABLE_NAME)
            .value(CassandraDomainMaxQuota.DOMAIN, bindMarker())
            .value(CassandraDomainMaxQuota.MESSAGE_COUNT, bindMarker());
    }

    private Insert setMaxStorageStatement() {
        return insertInto(CassandraDomainMaxQuota.TABLE_NAME)
            .value(CassandraDomainMaxQuota.DOMAIN, bindMarker())
            .value(CassandraDomainMaxQuota.STORAGE, bindMarker());
    }

    Mono<Void> setMaxStorage(Domain domain, QuotaSizeLimit maxStorageQuota) {
        return queryExecutor.executeVoid(setMaxStorageStatement.bind(domain.asString(), QuotaCodec.quotaValueToLong(maxStorageQuota)));
    }

    Mono<Void> setMaxMessage(Domain domain, QuotaCountLimit maxMessageCount) {
        return queryExecutor.executeVoid(setMaxMessageStatement.bind(domain.asString(), QuotaCodec.quotaValueToLong(maxMessageCount)));
    }

    Mono<QuotaSizeLimit> getMaxStorage(Domain domain) {
        return queryExecutor.executeSingleRow(getMaxStatement.bind(domain.asString()))
            .map(row -> Optional.ofNullable(row.get(CassandraDomainMaxQuota.STORAGE, Long.class)))
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaSize)
            .handle(publishIfPresent());
    }

    Mono<QuotaCountLimit> getMaxMessage(Domain domain) {
        return queryExecutor.executeSingleRow(getMaxStatement.bind(domain.asString()))
            .map(row -> Optional.ofNullable(row.get(CassandraDomainMaxQuota.MESSAGE_COUNT, Long.class)))
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaCount)
            .handle(publishIfPresent());
    }

    Mono<Limits> getLimits(Domain domain) {
        return queryExecutor.executeSingleRow(getMaxStatement.bind(domain.asString()))
            .map(row -> {
                Optional<Long> sizeLimit = Optional.ofNullable(row.get(CassandraDomainMaxQuota.STORAGE, Long.class));
                Optional<Long> countLimit = Optional.ofNullable(row.get(CassandraDomainMaxQuota.MESSAGE_COUNT, Long.class));

                return new Limits(
                    sizeLimit.flatMap(QuotaCodec::longToQuotaSize),
                    countLimit.flatMap(QuotaCodec::longToQuotaCount));
            })
            .switchIfEmpty(Mono.just(Limits.empty()));
    }

    Mono<Void> removeMaxMessage(Domain domain) {
        return queryExecutor.executeVoid(removeMaxMessageStatement.bind(domain.asString()));
    }

    Mono<Void> removeMaxStorage(Domain domain) {
        return queryExecutor.executeVoid(removeMaxStorageStatement.bind(domain.asString()));
    }
}
