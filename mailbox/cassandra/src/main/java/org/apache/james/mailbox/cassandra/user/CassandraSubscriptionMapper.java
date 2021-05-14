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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable.MAILBOX;
import static org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraSubscriptionTable.USER;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;

public class CassandraSubscriptionMapper extends NonTransactionalMapper implements SubscriptionMapper {
    private final Session session;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement insertStatement;
    private final CassandraAsyncExecutor executor;

    public CassandraSubscriptionMapper(Session session, CassandraUtils cassandraUtils) {
        this.session = session;
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;

        this.deleteStatement = session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(USER, bindMarker(USER)))
            .and(eq(MAILBOX, bindMarker(MAILBOX))));
        this.selectStatement = session.prepare(select(MAILBOX)
            .from(TABLE_NAME)
            .where(eq(USER, bindMarker(USER))));
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(MAILBOX, bindMarker(MAILBOX)));
    }

    @Override
    public synchronized void delete(Subscription subscription) {
        session.execute(deleteStatement.bind()
            .setString(USER, subscription.getUser().asString())
            .setString(MAILBOX, subscription.getMailbox()));
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) {
        return cassandraUtils.convertToStream(
            session.execute(selectStatement.bind()
                .setString(USER, user.asString())))
            .map((row) -> new Subscription(user, row.getString(MAILBOX)))
            .collect(Collectors.toList());
    }

    @Override
    public Flux<Subscription> findSubscriptionsForUserReactive(Username user) {
        return executor.executeRows(
            selectStatement.bind()
                .setString(USER, user.asString()))
            .map((row) -> new Subscription(user, row.getString(MAILBOX)));
    }

    @Override
    public synchronized void save(Subscription subscription) {
        session.execute(insertStatement.bind()
            .setString(USER, subscription.getUser().asString())
            .setString(MAILBOX, subscription.getMailbox()));
    }
}
