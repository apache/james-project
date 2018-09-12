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
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.BUCKET_ID;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ENQUEUED_TIME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.ERROR_MESSAGE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.HEADER_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.HEADER_TYPE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.HEADER_VALUE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.LAST_UPDATED;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.MAIL_KEY;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.PER_RECIPIENT_SPECIFIC_HEADERS;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.QUEUE_NAME;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.RECIPIENTS;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.REMOTE_ADDR;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.REMOTE_HOST;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.SENDER;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.STATE;
import static org.apache.james.queue.rabbitmq.view.cassandra.CassandraMailQueueViewModule.EnqueuedMailsTable.TIME_RANGE_START;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.core.MailAddress;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.model.BucketedSlices;
import org.apache.james.queue.rabbitmq.view.cassandra.model.EnqueuedMail;
import org.apache.james.queue.rabbitmq.view.cassandra.model.MailKey;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.streams.Iterators;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.UDTValue;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EnqueuedMailsDaoUtil {

    static EnqueuedMail toEnqueuedMail(Row row) {
        MailQueueName queueName = MailQueueName.fromString(row.getString(QUEUE_NAME));
        Instant timeRangeStart = row.getTimestamp(TIME_RANGE_START).toInstant();
        BucketedSlices.BucketId bucketId = BucketedSlices.BucketId.of(row.getInt(BUCKET_ID));
        Instant enqueuedTime = row.getTimestamp(ENQUEUED_TIME).toInstant();

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
        String name = row.getString(MAIL_KEY);
        Date lastUpdated = row.getTimestamp(LAST_UPDATED);
        Map<String, ByteBuffer> rawAttributes = row.getMap(ATTRIBUTES, String.class, ByteBuffer.class);
        PerRecipientHeaders perRecipientHeaders = fromHeaderMap(row.getMap(PER_RECIPIENT_SPECIFIC_HEADERS, String.class, UDTValue.class));

        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender(sender)
            .recipients(recipients)
            .lastUpdated(lastUpdated)
            .errorMessage(errorMessage)
            .remoteHost(remoteHost)
            .remoteAddr(remoteAddr)
            .state(state)
            .addAllHeadersForRecipients(perRecipientHeaders)
            .attributes(toAttributes(rawAttributes))
            .build();

        return EnqueuedMail.builder()
            .mail(mail)
            .bucketId(bucketId)
            .timeRangeStart(timeRangeStart)
            .enqueuedTime(enqueuedTime)
            .mailKey(MailKey.of(name))
            .mailQueueName(queueName)
            .build();
    }

    private static Map<String, Serializable> toAttributes(Map<String, ByteBuffer> rowAttributes) {
        return rowAttributes.entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                entry -> fromByteBuffer(entry.getValue())));
    }

    private static Serializable fromByteBuffer(ByteBuffer byteBuffer) {
        try {
            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data);
            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(data));
            return (Serializable) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static PerRecipientHeaders fromHeaderMap(Map<String, UDTValue> rawMap) {
        PerRecipientHeaders result = new PerRecipientHeaders();

        rawMap.forEach((key, value) -> result.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(value.getString(HEADER_NAME))
                .value(value.getString(HEADER_VALUE))
                .build(),
            toMailAddress(key)));
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
        return Iterators.toStream(mail.getAttributeNames())
            .map(name -> Pair.of(name, mail.getAttribute(name)))
            .map(pair -> Pair.of(pair.getLeft(), toByteBuffer(pair.getRight())))
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private static ByteBuffer toByteBuffer(Serializable serializable) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new ObjectOutputStream(outputStream).writeObject(serializable);
            return ByteBuffer.wrap(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static ImmutableMap<String, UDTValue> toHeaderMap(CassandraTypesProvider cassandraTypesProvider,
                                                      PerRecipientHeaders perRecipientHeaders) {
        return perRecipientHeaders.getHeadersByRecipient()
            .asMap()
            .entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().stream().map(value -> Pair.of(entry.getKey(), value)))
            .map(entry -> Pair.of(entry.getKey().asString(),
                cassandraTypesProvider.getDefinedUserType(HEADER_TYPE)
                    .newValue()
                    .setString(HEADER_NAME, entry.getRight().getName())
                    .setString(HEADER_VALUE, entry.getRight().getValue())))
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
    }
}
