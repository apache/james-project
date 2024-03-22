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

package org.apache.james.jmap.draft.methods;

import static org.apache.james.jmap.draft.methods.Method.JMAP_PREFIX;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class SetMessagesDestructionProcessor implements SetMessagesProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesCreationProcessor.class);

    private final MessageIdManager messageIdManager;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    SetMessagesDestructionProcessor(MessageIdManager messageIdManager, MetricFactory metricFactory) {
        this.messageIdManager = messageIdManager;
        this.metricFactory = metricFactory;
    }

    @Override
    public Mono<SetMessagesResponse> processReactive(SetMessagesRequest request, MailboxSession mailboxSession) {
        if (request.getDestroy().isEmpty()) {
            return Mono.just(SetMessagesResponse.builder().build());
        }
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + "SetMessageDestructionProcessor",
            delete(request.getDestroy(), mailboxSession)));
    }

    private Mono<SetMessagesResponse> delete(List<MessageId> toBeDestroyed, MailboxSession mailboxSession) {
        if (toBeDestroyed.isEmpty()) {
            return Mono.just(SetMessagesResponse.builder().build());
        }
        return Mono.from(messageIdManager.delete(ImmutableSet.copyOf(toBeDestroyed), mailboxSession))
            .map(deleteResult -> SetMessagesResponse.builder()
                .destroyed(ImmutableList.copyOf(deleteResult.getDestroyed()))
                .notDestroyed(deleteResult.getNotFound().stream()
                    .map(messageId -> Pair.of(messageId,
                        SetError.builder()
                            .type(SetError.Type.NOT_FOUND)
                            .description("The message " + messageId.serialize() + " can't be found")
                            .build()))
                    .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)))
                .build())
            .onErrorResume(e -> {
                LOGGER.error("An error occurred when deleting a message", e);
                return Mono.just(
                    SetMessagesResponse.builder()
                        .notDestroyed(toBeDestroyed.stream()
                            .map(messageId -> Pair.of(messageId,
                                SetError.builder()
                                    .type(SetError.Type.ERROR)
                                    .description("An error occurred while deleting messages " + messageId.serialize())
                                    .build()))
                            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue)))
                        .build());
            });
    }
}
