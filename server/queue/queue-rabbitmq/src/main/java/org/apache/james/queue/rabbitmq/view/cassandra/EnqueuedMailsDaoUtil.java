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
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.TIME_RANGE_START;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.HeaderEntry.HEADER_NAME_INDEX;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.HeaderEntry.HEADER_VALUE_INDEX;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.HeaderEntry.USER_INDEX;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.rabbitmq.EnqueueId;
import org.apache.james.queue.rabbitmq.EnqueuedItem;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedItemWithSlicingContext;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EnqueuedMailsDaoUtil {

    static EnqueuedItemWithSlicingContext toEnqueuedMail(Row row, BlobId.Factory blobFactory) {
        MailQueueName queueName = MailQueueName.fromString(row.getString(QUEUE_NAME));
        EnqueueId enqueueId = EnqueueId.of(row.getUuid(ENQUEUE_ID));
        Instant timeRangeStart = row.getInstant(TIME_RANGE_START);
        BucketedSlices.BucketId bucketId = BucketedSlices.BucketId.of(row.getInt(BUCKET_ID));
        Instant enqueuedTime = row.getInstant(ENQUEUED_TIME);
        BlobId headerBlobId = blobFactory.from(row.getString(HEADER_BLOB_ID));
        BlobId bodyBlobId = blobFactory.from(row.getString(BODY_BLOB_ID));
        MimeMessagePartsId mimeMessagePartsId = MimeMessagePartsId
            .builder()
            .headerBlobId(headerBlobId)
            .bodyBlobId(bodyBlobId)
            .build();

        MailAddress sender = Optional.ofNullable(row.getString(SENDER))
            .map(Throwing.function(MailAddress::new))
            .orElse(null);
        List<MailAddress> recipients = row.getList(RECIPIENTS, String.class)
            .stream()
            .map(Throwing.function(MailAddress::new))
            .collect(ImmutableList.toImmutableList());
        String state = row.getString(STATE);
        String remoteAddr = row.getString(REMOTE_ADDR);
        String remoteHost = row.getString(REMOTE_HOST);
        String errorMessage = row.getString(ERROR_MESSAGE);
        String name = row.getString(NAME);

        Date lastUpdated = Optional.ofNullable(row.getInstant(LAST_UPDATED))
            .map(Date::from)
            .orElse(null);

        Map<String, ByteBuffer> rawAttributes = row.getMap(ATTRIBUTES, String.class, ByteBuffer.class);
        PerRecipientHeaders perRecipientHeaders = fromList(row.getList(PER_RECIPIENT_SPECIFIC_HEADERS, TupleValue.class));

        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender(sender)
            .addRecipients(recipients)
            .lastUpdated(lastUpdated)
            .errorMessage(errorMessage)
            .remoteHost(remoteHost)
            .remoteAddr(remoteAddr)
            .state(state)
            .addAllHeadersForRecipients(perRecipientHeaders)
            .addAttributes(toAttributes(rawAttributes))
            .build();
        EnqueuedItem enqueuedItem = EnqueuedItem.builder()
            .enqueueId(enqueueId)
            .mailQueueName(queueName)
            .mail(mail)
            .enqueuedTime(enqueuedTime)
            .mimeMessagePartsId(mimeMessagePartsId)
            .build();


        return EnqueuedItemWithSlicingContext.builder()
            .enqueuedItem(enqueuedItem)
            .slicingContext(EnqueuedItemWithSlicingContext.SlicingContext.of(bucketId, timeRangeStart))
            .build();
    }

    @VisibleForTesting
    static List<Attribute> toAttributes(Map<String, ByteBuffer> rowAttributes) {
        return rowAttributes.entrySet()
            .stream()
            .map(entry -> new Attribute(AttributeName.of(entry.getKey()), fromByteBuffer(entry.getValue())))
            .collect(ImmutableList.toImmutableList());
    }

    private static AttributeValue<?> fromByteBuffer(ByteBuffer byteBuffer) {
        try {
            return AttributeValue.fromJsonString(StandardCharsets.UTF_8.decode(byteBuffer).toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PerRecipientHeaders fromList(List<TupleValue> list) {
        PerRecipientHeaders result = new PerRecipientHeaders();

        list.forEach(tuple ->
            result.addHeaderForRecipient(
                PerRecipientHeaders.Header.builder()
                    .name(tuple.getString(HEADER_NAME_INDEX))
                    .value(tuple.getString(HEADER_VALUE_INDEX))
                    .build(),
                toMailAddress(tuple.getString(USER_INDEX))));
        return result;
    }

    private static MailAddress toMailAddress(String rawValue) {
        try {
            return new MailAddress(rawValue);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    static ImmutableList<String> asStringList(Collection<MailAddress> mailAddresses) {
        return mailAddresses.stream()
            .map(MailAddress::asString)
            .collect(ImmutableList.toImmutableList());
    }

    static ImmutableMap<String, ByteBuffer> toRawAttributeMap(Mail mail) {
        return mail.attributes()
            .flatMap(attribute -> toByteBuffer(attribute.getValue()).map(buffer -> Pair.of(attribute.getName().asString(), buffer)).stream())
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private static Optional<ByteBuffer> toByteBuffer(AttributeValue<?> attributeValue) {
        return attributeValue.toJson()
            .map(JsonNode::toString)
            .map(s -> s.getBytes(StandardCharsets.UTF_8))
            .map(ByteBuffer::wrap);
    }

    static ImmutableList<TupleValue> toTupleList(TupleType userHeaderNameHeaderValueTriple, PerRecipientHeaders perRecipientHeaders) {
        return perRecipientHeaders.getHeadersByRecipient()
            .entries()
            .stream()
            .map(entry -> userHeaderNameHeaderValueTriple.newValue(entry.getKey().asString(), entry.getValue().getName(), entry.getValue().getValue()))
            .collect(ImmutableList.toImmutableList());
    }
}
