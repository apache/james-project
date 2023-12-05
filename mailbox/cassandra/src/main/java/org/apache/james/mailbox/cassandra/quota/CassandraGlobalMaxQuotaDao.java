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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota.KEY;
import static org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota.MESSAGE;
import static org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota.STORAGE;
import static org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraGlobalMaxQuota.VALUE;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.quota.Limits;
import org.apache.james.mailbox.quota.QuotaCodec;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

import reactor.core.publisher.Mono;

@Deprecated() // To be removed after release 3.9.0
public class CassandraGlobalMaxQuotaDao {

    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement setGlobalMaxStorageStatement;
    private final PreparedStatement setGlobalMaxMessageStatement;
    private final PreparedStatement getGlobalMaxStatement;
    private final PreparedStatement removeMessageMaxQuotaStatement;
    private final PreparedStatement removeStorageMaxQuotaStatement;

    @Inject
    public CassandraGlobalMaxQuotaDao(CqlSession session) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.getGlobalMaxStatement = session.prepare(getGlobalMaxStatement().build());
        this.setGlobalMaxMessageStatement = session.prepare(setGlobalMaxMessageStatement().build());
        this.setGlobalMaxStorageStatement = session.prepare(setGlobalMaxStorageStatement().build());
        this.removeMessageMaxQuotaStatement = session.prepare(removeMessageMaxQuotaStatement().build());
        this.removeStorageMaxQuotaStatement = session.prepare(removeStorageMaxQuotaStatement().build());
    }

    private Delete removeMessageMaxQuotaStatement() {
        return deleteFrom(TABLE_NAME)
            .column(MESSAGE)
            .where(column(KEY).isEqualTo(bindMarker(KEY)));
    }

    private Delete removeStorageMaxQuotaStatement() {
        return deleteFrom(TABLE_NAME)
            .column(STORAGE)
            .where(column(KEY).isEqualTo(bindMarker(KEY)));
    }

    private Insert setGlobalMaxStorageStatement() {
        return insertInto(TABLE_NAME)
            .value(KEY, literal(VALUE))
            .value(STORAGE, bindMarker(STORAGE));
    }

    private Insert setGlobalMaxMessageStatement() {
        return insertInto(TABLE_NAME)
            .value(KEY, literal(VALUE))
            .value(MESSAGE, bindMarker(MESSAGE));
    }

    private Select getGlobalMaxStatement() {
        return selectFrom(TABLE_NAME)
            .all()
            .where(column(KEY).isEqualTo(bindMarker(KEY)));
    }

    Mono<Void> setGlobalMaxStorage(QuotaSizeLimit globalMaxStorage) {
        return queryExecutor.executeVoid(setGlobalMaxStorageStatement.bind(QuotaCodec.quotaValueToLong(globalMaxStorage)));
    }

    Mono<Void> setGlobalMaxMessage(QuotaCountLimit globalMaxMessageCount) {
        return queryExecutor.executeVoid(setGlobalMaxMessageStatement.bind(QuotaCodec.quotaValueToLong(globalMaxMessageCount)));
    }

    Mono<QuotaSizeLimit> getGlobalMaxStorage() {
        return queryExecutor.executeSingleRow(getGlobalMaxStatement.bind()
                .setString(KEY, VALUE))
            .map(row -> Optional.ofNullable(row.get(STORAGE, Long.class)))
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaSize)
            .handle(publishIfPresent());
    }

    Mono<QuotaCountLimit> getGlobalMaxMessage() {
        return queryExecutor.executeSingleRow(getGlobalMaxStatement.bind()
                .setString(KEY, VALUE))
            .map(row -> Optional.ofNullable(row.get(MESSAGE, Long.class)))
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaCount)
            .handle(publishIfPresent());
    }

    Mono<Void> removeGlobaltMaxStorage() {
        return queryExecutor.executeVoid(removeStorageMaxQuotaStatement.bind()
            .setString(KEY, VALUE));
    }

    Mono<Void> removeGlobalMaxMessage() {
        return queryExecutor.executeVoid(removeMessageMaxQuotaStatement.bind()
            .setString(KEY, VALUE));
    }

    Mono<Limits> getGlobalLimits() {
        return queryExecutor.executeSingleRow(getGlobalMaxStatement.bind()
            .setString(KEY, VALUE))
            .map(row -> new Limits(
                Optional.ofNullable(row.get(STORAGE, Long.class)).flatMap(QuotaCodec::longToQuotaSize),
                Optional.ofNullable(row.get(MESSAGE, Long.class)).flatMap(QuotaCodec::longToQuotaCount)))
            .switchIfEmpty(Mono.just(Limits.empty()));
    }
}
