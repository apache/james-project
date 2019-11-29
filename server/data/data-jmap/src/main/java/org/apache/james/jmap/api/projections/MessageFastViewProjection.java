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

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface MessageFastViewProjection {

    Publisher<Void> store(MessageId messageId, MessageFastViewPrecomputedProperties preview);

    Publisher<MessageFastViewPrecomputedProperties> retrieve(MessageId messageId);

    Publisher<Void> delete(MessageId messageId);

    default Publisher<Map<MessageId, MessageFastViewPrecomputedProperties>> retrieve(List<MessageId> messageIds) {
        Preconditions.checkNotNull(messageIds);

        return Flux.fromIterable(messageIds)
            .flatMap(messageId -> Mono.from(this.retrieve(messageId))
                .map(preview -> Pair.of(messageId, preview)))
            .collectMap(Pair::getLeft, Pair::getRight)
            .subscribeOn(Schedulers.boundedElastic());
    }
}
