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

package org.apache.james.events;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.events.tables.CassandraEventDeadLettersGroupTable;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraEventDeadLettersGroupDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectAllStatement;

    @Inject
    CassandraEventDeadLettersGroupDAO(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertStatement = prepareInsertStatement(session);
        this.selectAllStatement = prepareSelectStatement(session);
    }

    private PreparedStatement prepareInsertStatement(Session session) {
        return session.prepare(QueryBuilder.insertInto(CassandraEventDeadLettersGroupTable.TABLE_NAME)
            .value(CassandraEventDeadLettersGroupTable.GROUP, bindMarker(CassandraEventDeadLettersGroupTable.GROUP)));
    }

    private PreparedStatement prepareSelectStatement(Session session) {
        return session.prepare(QueryBuilder.select(CassandraEventDeadLettersGroupTable.GROUP)
            .from(CassandraEventDeadLettersGroupTable.TABLE_NAME));
    }

    Mono<Void> storeGroup(Group group) {
        return executor.executeVoid(insertStatement.bind()
                .setString(CassandraEventDeadLettersGroupTable.GROUP, group.asString()));
    }

    Flux<Group> retrieveAllGroups() {
        return executor.executeRows(selectAllStatement.bind())
            .map(Throwing.function(row -> Group.deserialize(row.getString(CassandraEventDeadLettersGroupTable.GROUP))));
    }
}
