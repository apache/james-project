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
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.DeletedMailTable.ENQUEUE_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.DeletedMailTable.QUEUE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.DeletedMailTable.TABLE_NAME;
import static org.apache.james.util.FunctionalUtils.negate;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.MailQueueName;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import reactor.core.publisher.Mono;

public class DeletedMailsDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectOne;
    private final PreparedStatement insertOne;

    @Inject
    DeletedMailsDAO(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.selectOne = prepareSelectExist(session);
        this.insertOne = prepareInsert(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(QUEUE_NAME, bindMarker(QUEUE_NAME))
            .value(ENQUEUE_ID, bindMarker(ENQUEUE_ID)));
    }

    private PreparedStatement prepareSelectExist(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(QUEUE_NAME, bindMarker(QUEUE_NAME)))
            .and(eq(ENQUEUE_ID, bindMarker(ENQUEUE_ID))));
    }

    Mono<Void> markAsDeleted(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return executor.executeVoid(insertOne.bind()
            .setString(QUEUE_NAME, mailQueueName.asString())
            .setUUID(ENQUEUE_ID, enqueueId.asUUID()));
    }

    Mono<Boolean> isDeleted(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return executor.executeReturnExists(
            selectOne.bind()
                .setString(QUEUE_NAME, mailQueueName.asString())
                .setUUID(ENQUEUE_ID, enqueueId.asUUID()));
    }

    Mono<Boolean> isStillEnqueued(MailQueueName mailQueueName, EnqueueId enqueueId) {
        return isDeleted(mailQueueName, enqueueId)
            .map(negate());
    }
}
