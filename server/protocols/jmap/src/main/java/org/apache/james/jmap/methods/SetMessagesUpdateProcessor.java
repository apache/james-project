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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.exceptions.DraftMessageMailboxUpdateException;
import org.apache.james.jmap.exceptions.InvalidOutboxMoveException;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.UpdateMessagePatch;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.utils.SystemMailboxesProvider;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.OptionalUtils;
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
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final Factory mailboxIdFactory;
    private final MetricFactory metricFactory;
    private final MessageSender messageSender;

    @Inject
    @VisibleForTesting SetMessagesUpdateProcessor(
            UpdateMessagePatchConverter updatePatchConverter,
            MessageIdManager messageIdManager,
            SystemMailboxesProvider systemMailboxesProvider,
            Factory mailboxIdFactory,
            MessageSender messageSender,
            MetricFactory metricFactory) {
        this.updatePatchConverter = updatePatchConverter;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.mailboxIdFactory = mailboxIdFactory;
        this.metricFactory = metricFactory;
        this.messageSender = messageSender;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request,  MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "SetMessagesUpdateProcessor");

        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder();
        request.buildUpdatePatches(updatePatchConverter).forEach((id, patch) -> {
                if (patch.isValid()) {
                    update(id, patch, mailboxSession, responseBuilder);
                } else {
                    handleInvalidRequest(responseBuilder, id, patch.getValidationErrors());
                }
            }
        );

        timeMetric.stopAndPublish();
        return responseBuilder.build();
    }

    private void update(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession,
                        SetMessagesResponse.Builder builder) {
        try {
            List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.MINIMAL, mailboxSession);
            assertValidUpdate(messages, updateMessagePatch, mailboxSession);

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
                sendMessageWhenOutboxInTargetMailboxIds(messageId, updateMessagePatch, mailboxSession, builder);
            }
        } catch (DraftMessageMailboxUpdateException e) {
            handleDraftMessageMailboxUpdateException(messageId, builder, e);
        } catch (InvalidOutboxMoveException e) {
            ValidationResult invalidPropertyMailboxIds = ValidationResult.builder()
                .property(MessageProperties.MessageProperty.mailboxIds.asFieldName())
                .message(e.getMessage())
                .build();

            handleInvalidRequest(builder, messageId, ImmutableList.of(invalidPropertyMailboxIds));
        } catch (MailboxException | IOException | MessagingException e) {
            handleMessageUpdateException(messageId, builder, e);
        } catch (IllegalArgumentException e) {
            ValidationResult invalidPropertyKeywords = ValidationResult.builder()
                    .property(MessageProperties.MessageProperty.keywords.asFieldName())
                    .message(e.getMessage())
                    .build();

            handleInvalidRequest(builder, messageId, ImmutableList.of(invalidPropertyKeywords));
        }
    }

    private void sendMessageWhenOutboxInTargetMailboxIds(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, SetMessagesResponse.Builder builder) throws MailboxException, MessagingException, IOException {
        if (isTargetingOutbox(mailboxSession, listTargetMailboxIds(updateMessagePatch))) {
            Optional<MessageResult> messagesToSend =
                messageIdManager.getMessages(
                    ImmutableList.of(messageId), FetchGroupImpl.FULL_CONTENT, mailboxSession)
                    .stream()
                    .findFirst();
            if (messagesToSend.isPresent()) {
                MailImpl mail = buildMailFromMessage(messagesToSend.get());
                assertUserIsSender(mailboxSession, mail.getSender());
                messageSender.sendMessage(messageId, mail, mailboxSession);
            } else {
                addMessageIdNotFoundToResponse(messageId, builder);
            }
        }
    }

    private void assertUserIsSender(MailboxSession session, MailAddress sender) throws MailboxSendingNotAllowedException {
        if (!session.getUser().isSameUser(sender.asString())) {
            String allowedSender = session.getUser().getUserName();
            throw new MailboxSendingNotAllowedException(allowedSender);
        }
    }

    private void assertValidUpdate(List<MessageResult> messagesToBeUpdated, UpdateMessagePatch updateMessagePatch, MailboxSession session) throws MailboxException {
        List<MailboxId> outboxMailboxes = mailboxIdFor(Role.OUTBOX, session);

        ImmutableList<MailboxId> previousMailboxes = messagesToBeUpdated.stream()
            .map(MessageResult::getMailboxId)
            .collect(Guavate.toImmutableList());
        List<MailboxId> targetMailboxes = getTargetedMailboxes(previousMailboxes, updateMessagePatch);

        boolean allMessagesWereDrafts = messagesToBeUpdated.stream()
            .map(MessageResult::getFlags)
            .allMatch(flags -> flags.contains(Flags.Flag.DRAFT));

        boolean targetContainsOutbox = targetMailboxes.stream().anyMatch(outboxMailboxes::contains);
        boolean targetIsOnlyOutbox = targetMailboxes.stream().allMatch(outboxMailboxes::contains);

        assertOutboxMoveTargetsOnlyOutBox(targetContainsOutbox, targetIsOnlyOutbox);
        assertOutboxMoveOriginallyHasDraftKeywordSet(targetContainsOutbox, allMessagesWereDrafts);
    }

    private void assertOutboxMoveTargetsOnlyOutBox(boolean targetContainsOutbox, boolean targetIsOnlyOutbox) {
        if (targetContainsOutbox && !targetIsOnlyOutbox) {
            throw new InvalidOutboxMoveException("When moving a message to Outbox, only Outboxes mailboxes should be targeted.");
        }
    }

    private void assertOutboxMoveOriginallyHasDraftKeywordSet(boolean targetIsOutbox, boolean allMessagesWereDrafts) {
        if (targetIsOutbox && !allMessagesWereDrafts) {
            throw new InvalidOutboxMoveException("Only message with `$Draft` keyword can be moved to Outbox");
        }
    }

    private List<MailboxId> getTargetedMailboxes(ImmutableList<MailboxId> previousMailboxes, UpdateMessagePatch updateMessagePatch) {
        return updateMessagePatch.getMailboxIds()
            .map(ids -> ids.stream().map(mailboxIdFactory::fromString).collect(Guavate.toImmutableList()))
            .orElse(previousMailboxes);
    }

    private List<MailboxId> mailboxIdFor(Role role, MailboxSession session) throws MailboxException {
        return systemMailboxesProvider.getMailboxByRole(role, session)
            .map(MessageManager::getId)
            .collect(Guavate.toImmutableList());
    }

    private MailImpl buildMailFromMessage(MessageResult message) throws MessagingException, IOException, MailboxException {
        return MailImpl.fromMimeMessage(message.getMessageId().serialize(),
            new MimeMessage(
                Session.getDefaultInstance(new Properties()),
                message.getFullContent().getInputStream()));
    }

    private Set<MailboxId> listTargetMailboxIds(UpdateMessagePatch updateMessagePatch) {
        return OptionalUtils.toStream(updateMessagePatch.getMailboxIds())
            .flatMap(Collection::stream)
            .map(mailboxIdFactory::fromString)
            .collect(Guavate.toImmutableSet());
    }

    private boolean isTargetingOutbox(MailboxSession mailboxSession, Set<MailboxId> targetMailboxIds) throws MailboxException {
        Set<MailboxId> outboxes = listMailboxIdsForRole(mailboxSession, Role.OUTBOX);
        if (outboxes.isEmpty()) {
            throw new MailboxNotFoundException("At least one outbox should be accessible");
        }
        return targetMailboxIds.stream().anyMatch(outboxes::contains);
    }

    private Set<MailboxId> listMailboxIdsForRole(MailboxSession session, Role role) throws MailboxException {
        return systemMailboxesProvider.getMailboxByRole(role, session)
            .map(MessageManager::getId)
            .collect(Guavate.toImmutableSet());
    }

    private Stream<MailboxException> updateFlags(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, MessageResult messageResult) {
        try {
            if (!updateMessagePatch.isFlagsIdentity()) {
                messageIdManager.setFlags(
                    updateMessagePatch.applyToState(messageResult.getFlags()),
                    FlagsUpdateMode.REPLACE, messageId, ImmutableList.of(messageResult.getMailboxId()), mailboxSession);
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

    private SetMessagesResponse.Builder handleDraftMessageMailboxUpdateException(MessageId messageId,
                                                                     SetMessagesResponse.Builder builder,
                                                                     DraftMessageMailboxUpdateException e) {
        return builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
            .type("invalidArguments")
            .properties(MessageProperties.MessageProperty.mailboxIds)
            .description(e.getMessage())
            .build()));
    }

    private SetMessagesResponse.Builder handleMessageUpdateException(MessageId messageId,
                                              SetMessagesResponse.Builder builder,
                                              Exception e) {
        LOGGER.error("An error occurred when updating a message", e);
        return builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type("anErrorOccurred")
                .description("An error occurred when updating a message")
                .build()));
    }

    private void handleInvalidRequest(SetMessagesResponse.Builder responseBuilder, MessageId messageId,
                                      List<ValidationResult> validationErrors) {
        LOGGER.error("Invalid update request for message #{}", messageId);

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
