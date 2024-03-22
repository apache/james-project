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

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Optional;

import jakarta.inject.Inject;

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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EnqueuedMailsDAO {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement selectStatement;
    private final PreparedStatement selectBlobIdsStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteBucketStatement;
    private final BlobId.Factory blobFactory;
    private final TupleType userHeaderNameHeaderValueTriple;

    @VisibleForTesting
    @Inject
    public EnqueuedMailsDAO(CqlSession session, BlobId.Factory blobIdFactory) {
        this.executor = new CassandraAsyncExecutor(session);

        this.selectStatement = prepareSelectFrom(session);
        this.insertStatement = prepareInsert(session);
        this.deleteBucketStatement = prepareDeleteBucket(session);
        this.selectBlobIdsStatement = prepareSelectBlobIds(session);
        this.blobFactory = blobIdFactory;

        this.userHeaderNameHeaderValueTriple = DataTypes.tupleOf(DataTypes.TEXT, DataTypes.TEXT, DataTypes.TEXT);
    }

    private PreparedStatement prepareSelectFrom(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(QUEUE_NAME).isEqualTo(bindMarker(QUEUE_NAME))
            .whereColumn(TIME_RANGE_START).isEqualTo(bindMarker(TIME_RANGE_START))
            .whereColumn(BUCKET_ID).isEqualTo(bindMarker(BUCKET_ID))
            .build());
    }

    private PreparedStatement prepareSelectBlobIds(CqlSession session) {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(HEADER_BLOB_ID, BODY_BLOB_ID)
            .build());
    }

    private PreparedStatement prepareDeleteBucket(CqlSession session) {
        return session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(QUEUE_NAME).isEqualTo(bindMarker(QUEUE_NAME))
            .whereColumn(TIME_RANGE_START).isEqualTo(bindMarker(TIME_RANGE_START))
            .whereColumn(BUCKET_ID).isEqualTo(bindMarker(BUCKET_ID))
            .build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
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
            .value(PER_RECIPIENT_SPECIFIC_HEADERS, bindMarker(PER_RECIPIENT_SPECIFIC_HEADERS))
            .build());
    }

    Mono<Void> insert(EnqueuedItemWithSlicingContext enqueuedItemWithSlicing) {
        EnqueuedItem enqueuedItem = enqueuedItemWithSlicing.getEnqueuedItem();
        EnqueuedItemWithSlicingContext.SlicingContext slicingContext = enqueuedItemWithSlicing.getSlicingContext();
        Mail mail = enqueuedItem.getMail();
        MimeMessagePartsId mimeMessagePartsId = enqueuedItem.getPartsId();

        BoundStatementBuilder statement = insertStatement.boundStatementBuilder()
            .setString(QUEUE_NAME, enqueuedItem.getMailQueueName().asString())
            .setInstant(TIME_RANGE_START, slicingContext.getTimeRangeStart())
            .setInt(BUCKET_ID, slicingContext.getBucketId().getValue())
            .setInstant(ENQUEUED_TIME, enqueuedItem.getEnqueuedTime())
            .setUuid(ENQUEUE_ID, enqueuedItem.getEnqueueId().asUUID())
            .setString(NAME, mail.getName())
            .setString(HEADER_BLOB_ID, mimeMessagePartsId.getHeaderBlobId().asString())
            .setString(BODY_BLOB_ID, mimeMessagePartsId.getBodyBlobId().asString())
            .setString(STATE, mail.getState())
            .setList(RECIPIENTS, asStringList(mail.getRecipients()), String.class)
            .setString(REMOTE_ADDR, mail.getRemoteAddr())
            .setString(REMOTE_HOST, mail.getRemoteHost())
            .setMap(ATTRIBUTES, toRawAttributeMap(mail), String.class, ByteBuffer.class)
            .setList(PER_RECIPIENT_SPECIFIC_HEADERS, toTupleList(userHeaderNameHeaderValueTriple, mail.getPerRecipientSpecificHeaders()), TupleValue.class);

        Optional.ofNullable(mail.getErrorMessage())
            .ifPresent(errorMessage -> statement.setString(ERROR_MESSAGE, mail.getErrorMessage()));

        Optional.ofNullable(mail.getLastUpdated())
            .map(Date::toInstant)
            .ifPresent(lastUpdated -> statement.setInstant(LAST_UPDATED, lastUpdated));

        mail.getMaybeSender()
            .asOptional()
            .map(MailAddress::asString)
            .ifPresent(mailAddress -> statement.setString(SENDER, mailAddress));

        return executor.executeVoid(statement.build());
    }

    @VisibleForTesting
    public Flux<EnqueuedItemWithSlicingContext> selectEnqueuedMails(MailQueueName queueName, Slice slice, BucketId bucketId) {
        return executor.executeRows(
                selectStatement.bind()
                    .setString(QUEUE_NAME, queueName.asString())
                    .setInstant(TIME_RANGE_START, slice.getStartSliceInstant())
                    .setInt(BUCKET_ID, bucketId.getValue()))
            .map(row -> EnqueuedMailsDaoUtil.toEnqueuedMail(row, blobFactory));
    }

    Mono<Void> deleteBucket(MailQueueName queueName, Slice slice, BucketId bucketId) {
        return executor.executeVoid(
            deleteBucketStatement.bind()
                .setString(QUEUE_NAME, queueName.asString())
                .setInstant(TIME_RANGE_START, slice.getStartSliceInstant())
                .setInt(BUCKET_ID, bucketId.getValue()));
    }

    Flux<BlobId> listBlobIds() {
        return executor.executeRows(selectBlobIdsStatement.bind())
            .flatMapIterable(row -> ImmutableList.of(
                blobFactory.from(row.getString(HEADER_BLOB_ID)),
                blobFactory.from(row.getString(BODY_BLOB_ID))));
    }
}
