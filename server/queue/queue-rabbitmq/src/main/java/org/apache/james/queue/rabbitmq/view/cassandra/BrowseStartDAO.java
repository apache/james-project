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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.BrowseStartTable.BROWSE_START;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.BrowseStartTable.QUEUE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.BrowseStartTable.TABLE_NAME;

import java.time.Instant;
import java.util.Date;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.queue.rabbitmq.MailQueueName;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class BrowseStartDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectOne;
    private final PreparedStatement insertOne;
    private final PreparedStatement updateOne;

    @Inject
    BrowseStartDAO(Session session) {
        this.executor = new CassandraAsyncExecutor(session);

        this.selectOne = prepareSelectOne(session);
        this.updateOne = prepareUpdate(session);
        this.insertOne = prepareInsertOne(session);
    }

    private PreparedStatement prepareSelectOne(Session session) {
        return session.prepare(select()
                .from(TABLE_NAME)
                .where(eq(QUEUE_NAME, bindMarker(QUEUE_NAME))));
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(BROWSE_START, bindMarker(BROWSE_START)))
            .where(eq(QUEUE_NAME, bindMarker(QUEUE_NAME))));
    }

    private PreparedStatement prepareInsertOne(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .ifNotExists()
            .value(BROWSE_START, bindMarker(BROWSE_START))
            .value(QUEUE_NAME, bindMarker(QUEUE_NAME)));
    }

    Mono<Instant> findBrowseStart(MailQueueName queueName) {
        return selectOne(queueName)
            .map(this::getBrowseStart);
    }

    Mono<Void> updateBrowseStart(MailQueueName mailQueueName, Instant sliceStart) {
        return executor.executeVoid(updateOne.bind()
            .setTimestamp(BROWSE_START, Date.from(sliceStart))
            .setString(QUEUE_NAME, mailQueueName.asString()));
    }

    Mono<Void> insertInitialBrowseStart(MailQueueName mailQueueName, Instant sliceStart) {
        return executor.executeVoid(insertOne.bind()
            .setTimestamp(BROWSE_START, Date.from(sliceStart))
            .setString(QUEUE_NAME, mailQueueName.asString()));
    }

    @VisibleForTesting
    Mono<Row> selectOne(MailQueueName queueName) {
        return executor.executeSingleRow(
                selectOne.bind()
                    .setString(QUEUE_NAME, queueName.asString()));
    }

    private Instant getBrowseStart(Row row) {
        return row.getTimestamp(BROWSE_START).toInstant();
    }
}
