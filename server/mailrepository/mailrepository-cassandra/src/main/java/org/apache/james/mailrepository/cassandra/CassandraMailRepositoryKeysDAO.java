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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.KEYS_TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.MAIL_KEY;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REPOSITORY_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailRepositoryKeysDAO {

    private final CassandraAsyncExecutor executor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement insertKey;
    private final PreparedStatement deleteKey;
    private final PreparedStatement listKeys;

    @Inject
    public CassandraMailRepositoryKeysDAO(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;

        this.insertKey = prepareInsert(session);
        this.deleteKey = prepareDelete(session);
        this.listKeys = prepareList(session);
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select(MAIL_KEY)
            .from(KEYS_TABLE_NAME)
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(KEYS_TABLE_NAME)
            .ifExists()
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME)))
            .and(eq(MAIL_KEY, bindMarker(MAIL_KEY))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(KEYS_TABLE_NAME)
            .ifNotExists()
            .value(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))
            .value(MAIL_KEY, bindMarker(MAIL_KEY)));
    }

    public Mono<Boolean> store(MailRepositoryUrl url, MailKey key) {
        return executor.executeReturnApplied(insertKey.bind()
            .setString(REPOSITORY_NAME, url.asString())
            .setString(MAIL_KEY, key.asString()));
    }

    public Flux<MailKey> list(MailRepositoryUrl url) {
        return executor.execute(listKeys.bind()
            .setString(REPOSITORY_NAME, url.asString()))
            .flatMapMany(cassandraUtils::convertToFlux)
            .map(row -> new MailKey(row.getString(MAIL_KEY)));
    }

    public Mono<Boolean> remove(MailRepositoryUrl url, MailKey key) {
        return executor.executeReturnApplied(deleteKey.bind()
            .setString(REPOSITORY_NAME, url.asString())
            .setString(MAIL_KEY, key.asString()));
    }
}
