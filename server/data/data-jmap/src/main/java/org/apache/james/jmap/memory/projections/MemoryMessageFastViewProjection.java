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

package org.apache.james.jmap.memory.projections;

import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryMessageFastViewProjection implements MessageFastViewProjection {

    private final ConcurrentHashMap<MessageId, MessageFastViewPrecomputedProperties> previews;
    private final Metric metricRetrieveHitCount;
    private final Metric metricRetrieveMissCount;

    @Inject
    public MemoryMessageFastViewProjection(MetricFactory metricFactory) {
        this.previews = new ConcurrentHashMap<>();
        this.metricRetrieveHitCount = metricFactory.generate(METRIC_RETRIEVE_HIT_COUNT);
        this.metricRetrieveMissCount = metricFactory.generate(METRIC_RETRIEVE_MISS_COUNT);
    }

    @Override
    public Mono<Void> store(MessageId messageId, MessageFastViewPrecomputedProperties precomputedProperties) {
        Preconditions.checkNotNull(messageId);
        Preconditions.checkNotNull(precomputedProperties);

        return Mono.fromRunnable(() -> previews.put(messageId, precomputedProperties));
    }

    @Override
    public Mono<MessageFastViewPrecomputedProperties> retrieve(MessageId messageId) {
        Preconditions.checkNotNull(messageId);

        return Mono.fromSupplier(() -> previews.get(messageId))
            .doOnNext(preview -> metricRetrieveHitCount.increment())
            .switchIfEmpty(Mono.fromRunnable(metricRetrieveMissCount::increment));
    }

    @Override
    public Mono<Void> delete(MessageId messageId) {
        Preconditions.checkNotNull(messageId);

        return Mono.fromRunnable(() -> previews.remove(messageId));
    }
}
