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

package org.apache.james.jmap.postgres.projections;

import static org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionModule.MessageFastViewProjectionTable.HAS_ATTACHMENT;
import static org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionModule.MessageFastViewProjectionTable.MESSAGE_ID;
import static org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionModule.MessageFastViewProjectionTable.PREVIEW;
import static org.apache.james.jmap.postgres.projections.PostgresMessageFastViewProjectionModule.MessageFastViewProjectionTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.jooq.Record;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class PostgresMessageFastViewProjection implements MessageFastViewProjection {
    public static final Logger LOGGER = LoggerFactory.getLogger(PostgresMessageFastViewProjection.class);

    private final PostgresExecutor postgresExecutor;
    private final Metric metricRetrieveHitCount;
    private final Metric metricRetrieveMissCount;

    @Inject
    public PostgresMessageFastViewProjection(PostgresExecutor postgresExecutor, MetricFactory metricFactory) {
        this.postgresExecutor = postgresExecutor;
        this.metricRetrieveHitCount = metricFactory.generate(METRIC_RETRIEVE_HIT_COUNT);
        this.metricRetrieveMissCount = metricFactory.generate(METRIC_RETRIEVE_MISS_COUNT);
    }

    @Override
    public Publisher<Void> store(MessageId messageId, MessageFastViewPrecomputedProperties precomputedProperties) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(MESSAGE_ID, ((PostgresMessageId) messageId).asUuid())
            .set(PREVIEW, precomputedProperties.getPreview().getValue())
            .set(HAS_ATTACHMENT, precomputedProperties.hasAttachment())
            .onConflict(MESSAGE_ID)
            .doUpdate()
            .set(PREVIEW, precomputedProperties.getPreview().getValue())
            .set(HAS_ATTACHMENT, precomputedProperties.hasAttachment())));
    }

    @Override
    public Publisher<MessageFastViewPrecomputedProperties> retrieve(MessageId messageId) {
        Preconditions.checkNotNull(messageId);

        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(PREVIEW, HAS_ATTACHMENT)
            .from(TABLE_NAME)
            .where(MESSAGE_ID.eq(((PostgresMessageId) messageId).asUuid()))))
            .doOnNext(preview -> metricRetrieveHitCount.increment())
            .switchIfEmpty(Mono.fromRunnable(metricRetrieveMissCount::increment))
            .map(this::toMessageFastViewPrecomputedProperties)
            .onErrorResume(e -> {
                LOGGER.error("Error while retrieving MessageFastView projection item for {}", messageId, e);
                return Mono.empty();
            });
    }

    private MessageFastViewPrecomputedProperties toMessageFastViewPrecomputedProperties(Record record) {
        return MessageFastViewPrecomputedProperties.builder()
            .preview(Preview.from(record.get(PREVIEW)))
            .hasAttachment(record.get(HAS_ATTACHMENT))
            .build();
    }

    @Override
    public Publisher<Void> delete(MessageId messageId) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(MESSAGE_ID.eq(((PostgresMessageId) messageId).asUuid()))));
    }

    @Override
    public Publisher<Void> clear() {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.truncate(TABLE_NAME)));
    }
}
