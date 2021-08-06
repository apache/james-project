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
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;

import reactor.core.publisher.Mono;

public class CassandraGlobalMaxQuotaDao {

    private final CassandraAsyncExecutor queryExecutor;
    private final PreparedStatement setGlobalMaxStorageStatement;
    private final PreparedStatement setGlobalMaxMessageStatement;
    private final PreparedStatement getGlobalMaxStatement;
    private final PreparedStatement removeMessageMaxQuotaStatement;
    private final PreparedStatement removeStorageMaxQuotaStatement;

    @Inject
    public CassandraGlobalMaxQuotaDao(Session session) {
        this.queryExecutor = new CassandraAsyncExecutor(session);
        this.getGlobalMaxStatement = session.prepare(getGlobalMaxStatement());
        this.setGlobalMaxMessageStatement = session.prepare(setGlobalMaxMessageStatement());
        this.setGlobalMaxStorageStatement = session.prepare(setGlobalMaxStorageStatement());
        this.removeMessageMaxQuotaStatement = session.prepare(removeMessageMaxQuotaStatement());
        this.removeStorageMaxQuotaStatement = session.prepare(removeStorageMaxQuotaStatement());
    }

    private Delete.Where removeMessageMaxQuotaStatement() {
        return delete(MESSAGE)
            .from(TABLE_NAME)
            .where(eq(KEY, bindMarker(KEY)));
    }

    private Delete.Where removeStorageMaxQuotaStatement() {
        return delete(STORAGE)
            .from(TABLE_NAME)
            .where(eq(KEY, bindMarker(KEY)));
    }

    private Insert setGlobalMaxStorageStatement() {
        return insertInto(TABLE_NAME)
            .value(KEY, VALUE)
            .value(STORAGE, bindMarker(STORAGE));
    }

    private Insert setGlobalMaxMessageStatement() {
        return insertInto(TABLE_NAME)
            .value(KEY, VALUE)
            .value(MESSAGE, bindMarker(MESSAGE));
    }

    private Select.Where getGlobalMaxStatement() {
        return select()
            .from(TABLE_NAME)
            .where(eq(KEY, bindMarker(KEY)));
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
}
