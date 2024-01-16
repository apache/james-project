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

package org.apache.james.jmap.api.projections;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageFastViewProjection {

    String MESSAGE_FAST_VIEW_PROJECTION = "MessageFastViewProjection";
    String METRIC_RETRIEVE_HIT_COUNT = MESSAGE_FAST_VIEW_PROJECTION + ":retrieveHitCount";
    String METRIC_RETRIEVE_MISS_COUNT = MESSAGE_FAST_VIEW_PROJECTION + ":retrieveMissCount";

    Publisher<Void> store(MessageId messageId, MessageFastViewPrecomputedProperties preview);

    Publisher<MessageFastViewPrecomputedProperties> retrieve(MessageId messageId);

    Publisher<Void> delete(MessageId messageId);

    @VisibleForTesting
    Publisher<Void> clear();

    default Publisher<Map<MessageId, MessageFastViewPrecomputedProperties>> retrieve(Collection<MessageId> messageIds) {
        Preconditions.checkNotNull(messageIds);

        return Flux.fromIterable(messageIds)
            .flatMap(messageId -> Mono.from(this.retrieve(messageId))
                .map(preview -> Pair.of(messageId, preview)), DEFAULT_CONCURRENCY)
            .collectMap(Pair::getLeft, Pair::getRight);
    }
}
