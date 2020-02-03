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

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ATTRIBUTES;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.BODY_BLOB_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.BUCKET_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ENQUEUED_TIME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ENQUEUE_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ERROR_MESSAGE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.HEADER_BLOB_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.LAST_UPDATED;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.NAME;
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
import static org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDaoUtil.toRawAttributeMap;
import static org.apache.james.queue.rabbitmq.view.cassandra.EnqueuedMailsDaoUtil.toTupleList;

import java.util.Date;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.BucketId;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices.Slice;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;
import org.apache.mailet.Mail;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TupleType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EnqueuedMailsDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectFrom;
    private final PreparedStatement insert;
    private final BlobId.Factory blobFactory;
    private final TupleType userHeaderNameHeaderValueTriple;

    @Inject
    EnqueuedMailsDAO(Session session, BlobId.Factory blobIdFactory) {
        this.executor = new CassandraAsyncExecutor(session);

        this.selectFrom = prepareSelectFrom(session);
        this.insert = prepareInsert(session);
        this.blobFactory = blobIdFactory;
        this.userHeaderNameHeaderValueTriple = session.getCluster().getMetadata().newTupleType(text(), text(), text());
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
            .value(ENQUEUE_ID, bindMarker(ENQUEUE_ID))
            .value(NAME, bindMarker(NAME))
            .value(HEADER_BLOB_ID, bindMarker(HEADER_BLOB_ID))
            .value(BODY_BLOB_ID, bindMarker(BODY_BLOB_ID))
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

    Mono<Void> insert(EnqueuedItemWithSlicingContext enqueuedItemWithSlicing) {
        EnqueuedItem enqueuedItem = enqueuedItemWithSlicing.getEnqueuedItem();
        EnqueuedItemWithSlicingContext.SlicingContext slicingContext = enqueuedItemWithSlicing.getSlicingContext();
        Mail mail = enqueuedItem.getMail();
        MimeMessagePartsId mimeMessagePartsId = enqueuedItem.getPartsId();

        BoundStatement statement = insert.bind()
            .setString(QUEUE_NAME, enqueuedItem.getMailQueueName().asString())
            .setTimestamp(TIME_RANGE_START, Date.from(slicingContext.getTimeRangeStart()))
            .setInt(BUCKET_ID, slicingContext.getBucketId().getValue())
            .setTimestamp(ENQUEUED_TIME, Date.from(enqueuedItem.getEnqueuedTime()))
            .setUUID(ENQUEUE_ID, enqueuedItem.getEnqueueId().asUUID())
            .setString(NAME, mail.getName())
            .setString(HEADER_BLOB_ID, mimeMessagePartsId.getHeaderBlobId().asString())
            .setString(BODY_BLOB_ID, mimeMessagePartsId.getBodyBlobId().asString())
            .setString(STATE, mail.getState())
            .setList(RECIPIENTS, asStringList(mail.getRecipients()))

            .setString(REMOTE_ADDR, mail.getRemoteAddr())
            .setString(REMOTE_HOST, mail.getRemoteHost())
            .setTimestamp(LAST_UPDATED, mail.getLastUpdated())
            .setMap(ATTRIBUTES, toRawAttributeMap(mail))
            .setList(PER_RECIPIENT_SPECIFIC_HEADERS, toTupleList(userHeaderNameHeaderValueTriple, mail.getPerRecipientSpecificHeaders()));

        Optional.ofNullable(mail.getErrorMessage())
            .ifPresent(errorMessage -> statement.setString(ERROR_MESSAGE, mail.getErrorMessage()));

        mail.getMaybeSender()
            .asOptional()
            .map(MailAddress::asString)
            .ifPresent(mailAddress -> statement.setString(SENDER, mailAddress));

        return executor.executeVoid(statement);
    }

    Flux<EnqueuedItemWithSlicingContext> selectEnqueuedMails(
        MailQueueName queueName, Slice slice, BucketId bucketId) {

        return executor.executeRows(
                selectFrom.bind()
                    .setString(QUEUE_NAME, queueName.asString())
                    .setTimestamp(TIME_RANGE_START, Date.from(slice.getStartSliceInstant()))
                    .setInt(BUCKET_ID, bucketId.getValue()))
            .map(row -> EnqueuedMailsDaoUtil.toEnqueuedMail(row, blobFactory));
    }

}
