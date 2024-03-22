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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.DeletedMailTable.ENQUEUE_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.DeletedMailTable.QUEUE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.DeletedMailTable.TABLE_NAME;
import static org.apache.james.util.FunctionalUtils.negate;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.MailQueueName;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import reactor.core.publisher.Mono;

public class DeletedMailsDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectOne;
    private final PreparedStatement insertOne;
    private final PreparedStatement deleteOne;

    @Inject
    DeletedMailsDAO(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.selectOne = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(QUEUE_NAME).isEqualTo(bindMarker(QUEUE_NAME))
            .whereColumn(ENQUEUE_ID).isEqualTo(bindMarker(ENQUEUE_ID))
            .build());
        this.insertOne = session.prepare(insertInto(TABLE_NAME)
            .value(QUEUE_NAME, bindMarker(QUEUE_NAME))
            .value(ENQUEUE_ID, bindMarker(ENQUEUE_ID))
            .build());
        this.deleteOne = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(QUEUE_NAME).isEqualTo(bindMarker(QUEUE_NAME))
            .whereColumn(ENQUEUE_ID).isEqualTo(bindMarker(ENQUEUE_ID))
            .build());
    }

    Mono<Void> markAsDeleted(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return executor.executeVoid(insertOne.bind()
            .setString(QUEUE_NAME, mailQueueName.asString())
            .setUuid(ENQUEUE_ID, enqueueId.asUUID()));
    }

    Mono<Boolean> isDeleted(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return executor.executeReturnExists(
            selectOne.bind()
                .setString(QUEUE_NAME, mailQueueName.asString())
                .setUuid(ENQUEUE_ID, enqueueId.asUUID()));
    }

    Mono<Void> removeDeletedMark(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return executor.executeVoid(
            deleteOne.bind()
                .setString(QUEUE_NAME, mailQueueName.asString())
                .setUuid(ENQUEUE_ID, enqueueId.asUUID()));
    }

    Mono<Boolean> isStillEnqueued(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return isDeleted(mailQueueName, enqueueId)
            .map(negate());
    }
}
