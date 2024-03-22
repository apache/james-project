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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailrepository.cassandra.UrlsTable.TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.UrlsTable.URL;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class UrlsDao {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement selectAll;
    private final PreparedStatement select;

    @Inject
    public UrlsDao(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);

        this.insert = prepareInsert(session);
        this.selectAll = prepareSelectAll(session);
        this.select = prepareSelect(session);
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
                .column(URL)
                .where(column(URL).isEqualTo(bindMarker(URL)))
            .build());
    }

    private PreparedStatement prepareSelectAll(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
                .column(URL)
            .build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(URL, bindMarker(URL))
            .build());
    }

    public Mono<Void> addUrl(MailRepositoryUrl url) {
        return executor.executeVoid(
            insert.bind()
                .setString(URL, url.asString()));
    }

    public Mono<Optional<MailRepositoryUrl>> retrieve(MailRepositoryUrl url) {
        return executor.executeSingleRowOptional(
                select.bind()
                    .setString(URL, url.asString()))
            .map(optional -> optional.map(this::toUrl));
    }

    public Flux<MailRepositoryUrl> retrieveUsedUrls() {
        return executor.executeRows(selectAll.bind())
            .map(this::toUrl);
    }

    private MailRepositoryUrl toUrl(Row row) {
        return MailRepositoryUrl.from(row.getString(URL));
    }
}
