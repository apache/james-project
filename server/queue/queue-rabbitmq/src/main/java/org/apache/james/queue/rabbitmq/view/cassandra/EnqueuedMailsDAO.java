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
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ATTRIBUTES;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.BUCKET_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ENQUEUED_TIME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ERROR_MESSAGE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.LAST_UPDATED;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.MAIL_KEY;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.PER_RECIPIENT_SPECIFIC_HEADERS;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.QUEUE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.RECIPIENTS;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.REMOTE_ADDR;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.REMOTE_HOST;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.SENDER;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.STATE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.TABLE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.TIME_RANGE_START;
import static org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDaoUtil.asStringList;
import static org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDaoUtil.toHeaderMap;
import static org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDaoUtil.toRawAttributeMap;
import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import static org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedMail;
import org.apache.mailet.Mail;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

class EnqueuedMailsDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectFrom;
    private final PreparedStatement insert;
    private final CassandraUtils cassandraUtils;
    private final CassandraTypesProvider cassandraTypesProvider;

    @Inject
    EnqueuedMailsDAO(Session session, CassandraUtils cassandraUtils, CassandraTypesProvider cassandraTypesProvider) {
        this.executor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;
        this.cassandraTypesProvider = cassandraTypesProvider;

        this.selectFrom = prepareSelectFrom(session);
        this.insert = prepareInsert(session);
    }

    private PreparedStatement prepareSelectFrom(Session session) {
        return session.prepare(select()
            .from(TABLE_NAME)
            .where(eq(QUEUE_NAME, bindMarker(QUEUE_NAME)))
            .and(eq(TIME_RANGE_START, bindMarker(TIME_RANGE_START)))
            .and(eq(BUCKET_ID, bindMarker(BUCKET_ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(QUEUE_NAME, bindMarker(QUEUE_NAME))
            .value(TIME_RANGE_START, bindMarker(TIME_RANGE_START))
            .value(BUCKET_ID, bindMarker(BUCKET_ID))
            .value(MAIL_KEY, bindMarker(MAIL_KEY))
            .value(ENQUEUED_TIME, bindMarker(ENQUEUED_TIME))
            .value(STATE, bindMarker(STATE))
            .value(SENDER, bindMarker(SENDER))
            .value(RECIPIENTS, bindMarker(RECIPIENTS))
            .value(ATTRIBUTES, bindMarker(ATTRIBUTES))
            .value(ERROR_MESSAGE, bindMarker(ERROR_MESSAGE))
            .value(REMOTE_ADDR, bindMarker(REMOTE_ADDR))
            .value(REMOTE_HOST, bindMarker(REMOTE_HOST))
            .value(LAST_UPDATED, bindMarker(LAST_UPDATED))
            .value(PER_RECIPIENT_SPECIFIC_HEADERS, bindMarker(PER_RECIPIENT_SPECIFIC_HEADERS)));
    }

    CompletableFuture<Void> insert(EnqueuedMail enqueuedMail) {
        Mail mail = enqueuedMail.getMail();

        return executor.executeVoid(insert.bind()
            .setString(QUEUE_NAME, enqueuedMail.getMailQueueName().asString())
            .setTimestamp(TIME_RANGE_START, Date.from(enqueuedMail.getTimeRangeStart()))
            .setInt(BUCKET_ID, enqueuedMail.getBucketId().getValue())
            .setTimestamp(ENQUEUED_TIME, Date.from(enqueuedMail.getEnqueuedTime()))
            .setString(MAIL_KEY, mail.getName())
            .setString(STATE, mail.getState())
            .setString(SENDER, Optional.ofNullable(mail.getSender())
                .map(MailAddress::asString)
                .orElse(null))
            .setList(RECIPIENTS, asStringList(mail.getRecipients()))
            .setString(ERROR_MESSAGE, mail.getErrorMessage())
            .setString(REMOTE_ADDR, mail.getRemoteAddr())
            .setString(REMOTE_HOST, mail.getRemoteHost())
            .setTimestamp(LAST_UPDATED, mail.getLastUpdated())
            .setMap(ATTRIBUTES, toRawAttributeMap(mail))
            .setMap(PER_RECIPIENT_SPECIFIC_HEADERS, toHeaderMap(cassandraTypesProvider, mail.getPerRecipientSpecificHeaders()))
        );
    }

    CompletableFuture<Stream<EnqueuedMail>> selectEnqueuedMails(
        MailQueueName queueName, Slice slice, BucketId bucketId) {

        return executor.execute(
            selectFrom.bind()
                .setString(QUEUE_NAME, queueName.asString())
                .setTimestamp(TIME_RANGE_START, Date.from(slice.getStartSliceInstant()))
                .setInt(BUCKET_ID, bucketId.getValue()))
            .thenApply(resultSet -> cassandraUtils.convertToStream(resultSet)
                .map(EnqueuedMailsDaoUtil::toEnqueuedMail));
    }

}
