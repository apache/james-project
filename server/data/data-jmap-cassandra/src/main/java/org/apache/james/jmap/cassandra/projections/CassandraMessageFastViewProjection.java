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

package org.apache.james.jmap.cassandra.projections;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.HAS_ATTACHMENT;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.MESSAGE_ID;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.PREVIEW;
import static org.apache.james.jmap.cassandra.projections.table.CassandraMessageFastViewProjectionTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CassandraMessageFastViewProjection implements MessageFastViewProjection {

    public static final Logger LOGGER = LoggerFactory.getLogger(CassandraMessageFastViewProjection.class);
    private final Metric metricRetrieveHitCount;
    private final Metric metricRetrieveMissCount;

    private final CassandraAsyncExecutor cassandraAsyncExecutor;

    private final PreparedStatement storeStatement;
    private final PreparedStatement retrieveStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement truncateStatement;
    private final DriverExecutionProfile cachingProfile;

    @Inject
    CassandraMessageFastViewProjection(MetricFactory metricFactory, CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());

        this.storeStatement = session.prepare(insertInto(TABLE_NAME)
            .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
            .value(PREVIEW, bindMarker(PREVIEW))
            .value(HAS_ATTACHMENT, bindMarker(HAS_ATTACHMENT))
            .build());

        this.retrieveStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
            .build());

        this.truncateStatement = session.prepare(QueryBuilder.truncate(TABLE_NAME).build());

        cachingProfile = JamesExecutionProfiles.getCachingProfile(session);

        this.metricRetrieveHitCount = metricFactory.generate(METRIC_RETRIEVE_HIT_COUNT);
        this.metricRetrieveMissCount = metricFactory.generate(METRIC_RETRIEVE_MISS_COUNT);
    }

    @Override
    public Mono<Void> store(MessageId messageId, MessageFastViewPrecomputedProperties precomputedProperties) {
        checkMessage(messageId);

        return cassandraAsyncExecutor.executeVoid(storeStatement.bind()
            .setUuid(MESSAGE_ID, ((CassandraMessageId) messageId).get())
            .setString(PREVIEW, precomputedProperties.getPreview().getValue())
            .setBoolean(HAS_ATTACHMENT, precomputedProperties.hasAttachment())
            .setExecutionProfile(cachingProfile));
    }

    @Override
    public Mono<MessageFastViewPrecomputedProperties> retrieve(MessageId messageId) {
        checkMessage(messageId);

        return cassandraAsyncExecutor.executeSingleRow(retrieveStatement.bind()
                .set(MESSAGE_ID, ((CassandraMessageId) messageId).get(), TypeCodecs.UUID)
                .setExecutionProfile(cachingProfile))
            .map(this::fromRow)
            .doOnNext(preview -> metricRetrieveHitCount.increment())
            .switchIfEmpty(Mono.fromRunnable(metricRetrieveMissCount::increment))
            .onErrorResume(e -> {
                LOGGER.error("Error while retrieving MessageFastView projection item for {}", messageId, e);
                return Mono.empty();
            });
    }

    @Override
    public Mono<Void> delete(MessageId messageId) {
        checkMessage(messageId);

        return cassandraAsyncExecutor.executeVoid(deleteStatement.bind()
            .setUuid(MESSAGE_ID, ((CassandraMessageId) messageId).get()));
    }

    @Override
    public Mono<Void> clear() {
        return cassandraAsyncExecutor.executeVoid(truncateStatement.bind());
    }

    private void checkMessage(MessageId messageId) {
        Preconditions.checkNotNull(messageId);
        Preconditions.checkArgument(messageId instanceof CassandraMessageId,
            "MessageId type is required to be CassandraMessageId");
    }

    private MessageFastViewPrecomputedProperties fromRow(Row row) {
        return MessageFastViewPrecomputedProperties.builder()
            .preview(Preview.from(row.get(PREVIEW, TypeCodecs.TEXT)))
            .hasAttachment(row.getBoolean(HAS_ATTACHMENT))
            .build();
    }
}
