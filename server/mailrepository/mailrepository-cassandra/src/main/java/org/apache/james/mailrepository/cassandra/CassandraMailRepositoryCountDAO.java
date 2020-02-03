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
import static com.datastax.driver.core.querybuilder.QueryBuilder.decr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.COUNT;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.COUNT_TABLE;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REPOSITORY_NAME;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class CassandraMailRepositoryCountDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement increment;
    private final PreparedStatement decrement;
    private final PreparedStatement select;

    @Inject
    public CassandraMailRepositoryCountDAO(Session session) {
        this.executor = new CassandraAsyncExecutor(session);

        this.increment = prepareIncrement(session);
        this.decrement = prepareDecrement(session);
        this.select = prepareSelect(session);
    }

    private PreparedStatement prepareDecrement(Session session) {
        return session.prepare(update(COUNT_TABLE)
            .with(decr(COUNT))
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))));
    }

    private PreparedStatement prepareIncrement(Session session) {
        return session.prepare(update(COUNT_TABLE)
            .with(incr(COUNT))
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(COUNT)
            .from(COUNT_TABLE)
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))));
    }

    public Mono<Void> increment(MailRepositoryUrl url) {
        return executor.executeVoid(increment.bind()
            .setString(REPOSITORY_NAME, url.asString()));
    }

    public Mono<Void> decrement(MailRepositoryUrl url) {
        return executor.executeVoid(decrement.bind()
            .setString(REPOSITORY_NAME, url.asString()));
    }

    public Mono<Long> getCount(MailRepositoryUrl url) {
        return executor.executeSingleRowOptional(select.bind()
                .setString(REPOSITORY_NAME, url.asString()))
            .map(this::toCount);
    }

    private Long toCount(Optional<Row> rowOptional) {
        return rowOptional
            .map(row -> row.getLong(COUNT))
            .orElse(0L);
    }
}
