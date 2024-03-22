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

package org.apache.james.mailrepository.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static java.util.List.of;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.KEYS_TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.MAIL_KEY;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REPOSITORY_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailRepositoryKeysDAO {
    private static final String COUNT = "count";

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertKey;
    private final PreparedStatement deleteKey;
    private final PreparedStatement listKeys;
    private final PreparedStatement countKeys;
    private final boolean strongConsistency;

    @Inject
    public CassandraMailRepositoryKeysDAO(CqlSession session, CassandraConfiguration cassandraConfiguration) {
        this.strongConsistency = cassandraConfiguration.isMailRepositoryStrongConsistency();
        this.executor = new CassandraAsyncExecutor(session);

        this.insertKey = prepareInsert(session);
        this.deleteKey = prepareDelete(session);
        this.listKeys = prepareList(session);
        this.countKeys = prepareCount(session);
    }

    private PreparedStatement prepareList(CqlSession session) {
        return session.prepare(selectFrom(KEYS_TABLE_NAME)
            .column(MAIL_KEY)
            .where(column(REPOSITORY_NAME).isEqualTo(bindMarker(REPOSITORY_NAME)))
            .build());
    }

    private PreparedStatement prepareCount(CqlSession session) {
        return session.prepare(selectFrom(KEYS_TABLE_NAME)
            .countAll().as(COUNT)
            .where(column(REPOSITORY_NAME).isEqualTo(bindMarker(REPOSITORY_NAME)))
            .build());
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        Delete deleteStatement = deleteFrom(KEYS_TABLE_NAME)
            .where(of(column(REPOSITORY_NAME).isEqualTo(bindMarker(REPOSITORY_NAME)),
                column(MAIL_KEY).isEqualTo(bindMarker(MAIL_KEY))));

        if (strongConsistency) {
            return session.prepare(deleteStatement.ifExists().build());
        }
        return session.prepare(deleteStatement.build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        Insert insertStatement = insertInto(KEYS_TABLE_NAME)
            .value(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))
            .value(MAIL_KEY, bindMarker(MAIL_KEY));

        if (strongConsistency) {
            return session.prepare(insertStatement.ifNotExists().build());
        }
        return session.prepare(insertStatement.build());
    }

    public Mono<Boolean> store(MailRepositoryUrl url, MailKey key) {
        Mono<Boolean> operation = executor.executeReturnApplied(insertKey.bind()
            .setString(REPOSITORY_NAME, url.asString())
            .setString(MAIL_KEY, key.asString()));

        if (strongConsistency) {
            return operation;
        } else {
            return operation.switchIfEmpty(Mono.just(true));
        }
    }

    public Flux<MailKey> list(MailRepositoryUrl url) {
        return executor.executeRows(listKeys.bind()
                .setString(REPOSITORY_NAME, url.asString()))
            .map(row -> new MailKey(row.getString(MAIL_KEY)));
    }

    public Mono<Long> getCount(MailRepositoryUrl url) {
        return executor.executeSingleRow(countKeys.bind()
            .setString(REPOSITORY_NAME, url.asString()))
            .map(row -> row.getLong(COUNT))
            .switchIfEmpty(Mono.just(0L));
    }

    public Mono<Boolean> remove(MailRepositoryUrl url, MailKey key) {
        Mono<Boolean> operation = executor.executeReturnApplied(deleteKey.bind()
            .setString(REPOSITORY_NAME, url.asString())
            .setString(MAIL_KEY, key.asString()));

        if (strongConsistency) {
            return operation;
        } else {
            return operation.switchIfEmpty(Mono.just(true));
        }
    }
}
