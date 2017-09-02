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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.UpdateMessagePatch;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class SetMessagesUpdateProcessor implements SetMessagesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesUpdateProcessor.class);

    private final UpdateMessagePatchConverter updatePatchConverter;
    private final MessageIdManager messageIdManager;
    private final Factory mailboxIdFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting SetMessagesUpdateProcessor(
        UpdateMessagePatchConverter updatePatchConverter,
        MessageIdManager messageIdManager,
        Factory mailboxIdFactory, MetricFactory metricFactory) {
        this.updatePatchConverter = updatePatchConverter;
        this.messageIdManager = messageIdManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.metricFactory = metricFactory;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request,  MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "SetMessagesUpdateProcessor");

        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder();
        request.buildUpdatePatches(updatePatchConverter).forEach( (id, patch) -> {
            if (patch.isValid()) {
                update(id, patch, mailboxSession, responseBuilder);
            } else {
                handleInvalidRequest(responseBuilder, id, patch.getValidationErrors());
            }});

        timeMetric.stopAndPublish();
        return responseBuilder.build();
    }

    private void update(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession,
                        SetMessagesResponse.Builder builder) {
        try {
            List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, mailboxSession);

            if (messages.isEmpty()) {
                addMessageIdNotFoundToResponse(messageId, builder);
            } else {
                setInMailboxes(messageId, updateMessagePatch, mailboxSession);
                Optional<MailboxException> updateError = messages.stream()
                    .flatMap(message -> updateFlags(messageId, updateMessagePatch, mailboxSession, message))
                    .findAny();
                if (updateError.isPresent()) {
                    handleMessageUpdateException(messageId, builder, updateError.get());
                } else {
                    builder.updated(ImmutableList.of(messageId));
                }
            }
        } catch (MailboxException e) {
            handleMessageUpdateException(messageId, builder, e);
        } catch (IllegalArgumentException e) {
            ValidationResult invalidPropertyKeywords = ValidationResult.builder()
                    .property(MessageProperties.MessageProperty.keywords.asFieldName())
                    .message(e.getMessage())
                    .build();

            handleInvalidRequest(builder, messageId, ImmutableList.of(invalidPropertyKeywords));
        }

    }

    private Stream<MailboxException> updateFlags(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, MessageResult messageResult) {
        try {
            if (!updateMessagePatch.isFlagsIdentity()) {
                Flags newState = updateMessagePatch.applyToState(messageResult.getFlags());
                messageIdManager.setFlags(newState, MessageManager.FlagsUpdateMode.REPLACE, messageId, ImmutableList.of(messageResult.getMailboxId()), mailboxSession);
            }
            return Stream.of();
        } catch (MailboxException e) {
            return Stream.of(e);
        }
    }

    private void setInMailboxes(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession) throws MailboxException {
        Optional<List<String>> serializedMailboxIds = updateMessagePatch.getMailboxIds();
        if (serializedMailboxIds.isPresent()) {
            List<MailboxId> mailboxIds = serializedMailboxIds.get()
                .stream()
                .map(mailboxIdFactory::fromString)
                .collect(Guavate.toImmutableList());

            messageIdManager.setInMailboxes(messageId, mailboxIds, mailboxSession);
        }
    }

    private void addMessageIdNotFoundToResponse(MessageId messageId, SetMessagesResponse.Builder builder) {
        builder.notUpdated(ImmutableMap.of(messageId,
                SetError.builder()
                        .type("notFound")
                        .properties(ImmutableSet.of(MessageProperties.MessageProperty.id))
                        .description("message not found")
                        .build()));
    }

    private SetMessagesResponse.Builder handleMessageUpdateException(MessageId messageId,
                                              SetMessagesResponse.Builder builder,
                                              MailboxException e) {
        LOGGER.error("An error occurred when updating a message", e);
        return builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type("anErrorOccurred")
                .description("An error occurred when updating a message")
                .build()));
    }

    private void handleInvalidRequest(SetMessagesResponse.Builder responseBuilder, MessageId messageId,
                                      List<ValidationResult> validationErrors) {
        LOGGER.error("Invalid update request for message #", messageId.toString());

        String formattedValidationErrorMessage = validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining(", "));

        Set<MessageProperties.MessageProperty> properties = validationErrors.stream()
                .flatMap(err -> MessageProperties.MessageProperty.find(err.getProperty()))
                .collect(Collectors.toSet());

        responseBuilder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type("invalidProperties")
                .properties(properties)
                .description(formattedValidationErrorMessage)
                .build()));

    }
}
