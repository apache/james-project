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

package org.apache.james.queue.rabbitmq.view.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.BrowseStartTable.BROWSE_START;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.BrowseStartTable.QUEUE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.BrowseStartTable.TABLE_NAME;

import java.time.Instant;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.queue.rabbitmq.MailQueueName;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BrowseStartDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectOne;
    private final PreparedStatement selectAll;
    private final PreparedStatement insertOne;
    private final PreparedStatement updateOne;

    @Inject
    BrowseStartDAO(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);

        this.selectOne = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(QUEUE_NAME).isEqualTo(bindMarker(QUEUE_NAME))
            .build());

        this.selectAll = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .build());

        this.updateOne = session.prepare(update(TABLE_NAME)
            .setColumn(BROWSE_START, bindMarker(BROWSE_START))
            .whereColumn(QUEUE_NAME).isEqualTo(bindMarker(QUEUE_NAME))
            .build());

        this.insertOne = session.prepare(insertInto(TABLE_NAME)
            .value(BROWSE_START, bindMarker(BROWSE_START))
            .value(QUEUE_NAME, bindMarker(QUEUE_NAME))
            .ifNotExists()
            .build());
    }

    Mono<Instant> findBrowseStart(MailQueueName queueName) {
        return selectOne(queueName)
            .mapNotNull(row -> row.getInstant(BROWSE_START));
    }

    Mono<Void> updateBrowseStart(MailQueueName mailQueueName, Instant sliceStart) {
        return executor.executeVoid(updateOne.bind()
            .setInstant(BROWSE_START, sliceStart)
            .setString(QUEUE_NAME, mailQueueName.asString()));
    }

    Mono<Void> insertInitialBrowseStart(MailQueueName mailQueueName, Instant sliceStart) {
        return executor.executeVoid(insertOne.bind()
            .setInstant(BROWSE_START, sliceStart)
            .setString(QUEUE_NAME, mailQueueName.asString()));
    }

    Flux<Pair<MailQueueName, Instant>> listAll() {
        return executor.executeRows(selectAll.bind())
            .map(row -> Pair.of(MailQueueName.fromString(row.getString(QUEUE_NAME)), row.getInstant(BROWSE_START)));
    }

    @VisibleForTesting
    Mono<Row> selectOne(MailQueueName queueName) {
        return executor.executeSingleRow(
            selectOne.bind()
                .setString(QUEUE_NAME, queueName.asString()));
    }
}
