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
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailrepository.cassandra.UrlsTable.TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.UrlsTable.URL;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class UrlsDao {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement selectAll;
    private final PreparedStatement select;
    private final CassandraUtils cassandraUtils;

    @Inject
    public UrlsDao(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;

        this.insert = prepareInsert(session);
        this.selectAll = prepareSelectAll(session);
        this.select = prepareSelect(session);
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(URL)
            .from(TABLE_NAME)
            .where(eq(URL, bindMarker(URL))));
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select(URL)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(URL, bindMarker(URL)));
    }

    public CompletableFuture<Void> addUrl(MailRepositoryUrl url) {
        return executor.executeVoid(
            insert.bind()
                .setString(URL, url.asString()));
    }

    public CompletableFuture<Optional<MailRepositoryUrl>> retrieve(MailRepositoryUrl url) {
        return executor.executeSingleRow(
            select.bind()
                .setString(URL, url.asString()))
            .thenApply(optional -> optional.map(this::toUrl));
    }

    public CompletableFuture<Stream<MailRepositoryUrl>> retrieveUsedUrls() {
        return executor.execute(selectAll.bind())
            .thenApply(resultSet -> cassandraUtils.convertToStream(resultSet)
                .map(this::toUrl));
    }

    private MailRepositoryUrl toUrl(Row row) {
        return MailRepositoryUrl.from(row.getString(URL));
    }
}
