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

package org.apache.james.mailbox.cassandra.user;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable.MAILBOX;
import static org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable.USER;

import java.util.List;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraSubscriptionMapper extends NonTransactionalMapper implements SubscriptionMapper {
    private final CqlSession session;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement insertStatement;
    private final CassandraAsyncExecutor executor;

    public CassandraSubscriptionMapper(CqlSession session) {
        this.session = session;
        this.executor = new CassandraAsyncExecutor(session);

        this.deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(USER).isEqualTo(bindMarker(USER)),
                column(MAILBOX).isEqualTo(bindMarker(MAILBOX)))
            .build());
        this.selectStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(MAILBOX)
            .where(column(USER).isEqualTo(bindMarker(USER)))
            .build());
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(MAILBOX, bindMarker(MAILBOX))
            .build());
    }

    @Override
    public void delete(Subscription subscription) {
        deleteReactive(subscription).block();
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) {
        return Flux.from(
                session.executeReactive(selectStatement.bind()
                    .setString(USER, user.asString())))
            .map(row -> new Subscription(user, row.getString(MAILBOX)))
            .collectList()
            .block();
    }

    @Override
    public Flux<Subscription> findSubscriptionsForUserReactive(Username user) {
        return executor.executeRows(
                selectStatement.bind()
                    .setString(USER, user.asString()))
            .map(row -> new Subscription(user, row.getString(MAILBOX)));
    }

    @Override
    public void save(Subscription subscription) {
        saveReactive(subscription).block();
    }

    @Override
    public Mono<Void> saveReactive(Subscription subscription) {
        return executor.executeVoid(insertStatement.bind()
            .setString(USER, subscription.getUser().asString())
            .setString(MAILBOX, subscription.getMailbox()));
    }

    @Override
    public Mono<Void> deleteReactive(Subscription subscription) {
        return executor.executeVoid(deleteStatement.bind()
            .setString(USER, subscription.getUser().asString())
            .setString(MAILBOX, subscription.getMailbox()));
    }
}
