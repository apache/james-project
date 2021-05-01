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
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        return metricFactory.decorateSupplierWithTimerMetric(JMAP_PREFIX + "SetMessageDestructionProcessor",
            () -> delete(request.getDestroy(), mailboxSession)
                .reduce(SetMessagesResponse.builder(),
                    SetMessagesResponse.Builder::accumulator,
                    SetMessagesResponse.Builder::combiner)
                .build());
    }


    private Stream<SetMessagesResponse> delete(List<MessageId> toBeDestroyed, MailboxSession mailboxSession) {
        try {
            if (toBeDestroyed.isEmpty()) {
                return Stream.empty();
            }
            DeleteResult deleteResult = Mono.from(messageIdManager.delete(toBeDestroyed, mailboxSession))
                .subscribeOn(Schedulers.elastic())
                .block();

            Stream<SetMessagesResponse> destroyed = deleteResult.getDestroyed().stream()
                .map(messageId -> SetMessagesResponse.builder().destroyed(messageId).build());
            Stream<SetMessagesResponse> notFound = deleteResult.getNotFound().stream()
                .map(messageId -> SetMessagesResponse.builder().notDestroyed(messageId,
                    SetError.builder()
                        .type(SetError.Type.NOT_FOUND)
                        .description("The message " + messageId.serialize() + " can't be found")
                        .build())
                    .build());
            return Stream.concat(destroyed, notFound);
        } catch (MailboxException e) {
            LOGGER.error("An error occurred when deleting a message", e);
            return toBeDestroyed.stream()
                .map(messageId -> SetMessagesResponse.builder().notDestroyed(messageId,
                    SetError.builder()
                        .type(SetError.Type.ERROR)
                        .description("An error occurred while deleting messages " + messageId.serialize())
                        .build())
                    .build());
        }
    }
}
