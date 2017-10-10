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

package org.apache.james.jmap.methods;

import static org.apache.james.jmap.methods.Method.JMAP_PREFIX;

import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.jmap.exceptions.MessageNotFoundException;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

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
        return TimeMetric.withMetric(
            metricFactory.timer(JMAP_PREFIX + "SetMessageDestructionProcessor"),
            () -> request.getDestroy().stream()
                .map(delete(mailboxSession))
                .reduce(SetMessagesResponse.builder(),
                    SetMessagesResponse.Builder::accumulator,
                    SetMessagesResponse.Builder::combiner)
                .build());
    }

    private Function<? super MessageId, SetMessagesResponse> delete(MailboxSession mailboxSession) {
        return (messageId) -> {
            try {
                List<MailboxId> mailboxes = listContainingMailboxes(messageId, mailboxSession);
                messageIdManager.delete(messageId, mailboxes, mailboxSession);
                return SetMessagesResponse.builder().destroyed(messageId).build();
            } catch (MessageNotFoundException e) {
                return SetMessagesResponse.builder().notDestroyed(messageId,
                        SetError.builder()
                                .type("notFound")
                                .description("The message " + messageId.serialize() + " can't be found")
                                .build())
                        .build();
            } catch (MailboxException e) {
                LOGGER.error("An error occurred when deleting a message", e);
                return SetMessagesResponse.builder().notDestroyed(messageId,
                        SetError.builder()
                                .type("anErrorOccurred")
                                .description("An error occurred while deleting message " + messageId.serialize())
                                .build())
                        .build();
            }
        };
    }

    private List<MailboxId> listContainingMailboxes(MessageId messageId, MailboxSession mailboxSession) throws MailboxException, MessageNotFoundException {
        List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, mailboxSession);
        if (messages.isEmpty()) {
            throw new MessageNotFoundException();
        }
        return messages.stream()
                .map(MessageResult::getMailboxId)
                .collect(Guavate.toImmutableList());
    }
}
