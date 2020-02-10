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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.DraftMessageMailboxUpdateException;
import org.apache.james.jmap.draft.exceptions.InvalidOutboxMoveException;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.jmap.draft.model.UpdateMessagePatch;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.rrt.api.CanSendFrom;
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

    private final ReferenceUpdater referenceUpdater;
    private final CanSendFrom canSendFrom;

    @Inject
    @VisibleForTesting SetMessagesUpdateProcessor(
            UpdateMessagePatchConverter updatePatchConverter,
            MessageIdManager messageIdManager,
            SystemMailboxesProvider systemMailboxesProvider,
            Factory mailboxIdFactory,
            MessageSender messageSender,
            MetricFactory metricFactory,
            ReferenceUpdater referenceUpdater,
            CanSendFrom canSendFrom) {
        this.updatePatchConverter = updatePatchConverter;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.mailboxIdFactory = mailboxIdFactory;
        this.metricFactory = metricFactory;
        this.messageSender = messageSender;
        this.referenceUpdater = referenceUpdater;
        this.canSendFrom = canSendFrom;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request,  MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "SetMessagesUpdateProcessor");

        SetMessagesResponse.Builder responseBuilder = SetMessagesResponse.builder();
        request.buildUpdatePatches(updatePatchConverter).forEach((id, patch) -> {
                if (patch.isValid()) {
                    update(id, patch, mailboxSession, responseBuilder);
                } else {
                    handleInvalidRequest(responseBuilder, id, patch.getValidationErrors(), patch);
                }
            }
        );

        timeMetric.stopAndPublish();
        return responseBuilder.build();
    }

    private void update(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession,
                        SetMessagesResponse.Builder builder) {
        try {
            List<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.MINIMAL, mailboxSession);
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

            handleInvalidRequest(builder, messageId, ImmutableList.of(invalidPropertyMailboxIds), updateMessagePatch);
        } catch (OverQuotaException e) {
            builder.notUpdated(messageId,
                SetError.builder()
                    .type(SetError.Type.MAX_QUOTA_REACHED)
                    .description(e.getMessage())
                    .build());
        } catch (MailboxException | IOException | MessagingException e) {
            handleMessageUpdateException(messageId, builder, e);
        } catch (IllegalArgumentException e) {
            ValidationResult invalidPropertyKeywords = ValidationResult.builder()
                    .property(MessageProperties.MessageProperty.keywords.asFieldName())
                    .message(e.getMessage())
                    .build();

            handleInvalidRequest(builder, messageId, ImmutableList.of(invalidPropertyKeywords), updateMessagePatch);
        }
    }

    private void sendMessageWhenOutboxInTargetMailboxIds(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, SetMessagesResponse.Builder builder) throws MailboxException, MessagingException, IOException {
        if (isTargetingOutbox(mailboxSession, listTargetMailboxIds(updateMessagePatch))) {
            Optional<MessageResult> maybeMessageToSend =
                messageIdManager.getMessage(messageId, FetchGroup.FULL_CONTENT, mailboxSession)
                    .stream()
                    .findFirst();
            if (maybeMessageToSend.isPresent()) {
                MessageResult messageToSend = maybeMessageToSend.get();
                MailImpl mail = buildMailFromMessage(messageToSend);
                Optional<Username> fromUser = mail.getMaybeSender()
                    .asOptional()
                    .map(Username::fromMailAddress);
                assertUserCanSendFrom(mailboxSession.getUser(), fromUser);
                messageSender.sendMessage(messageId, mail, mailboxSession);
                referenceUpdater.updateReferences(messageToSend.getHeaders(), mailboxSession);
            } else {
                addMessageIdNotFoundToResponse(messageId, builder);
            }
        }
    }

    @VisibleForTesting
    void assertUserCanSendFrom(Username connectedUser, Optional<Username> fromUser) throws MailboxSendingNotAllowedException {
        if (!fromUser.filter(from -> canSendFrom.userCanSendFrom(connectedUser, from))
            .isPresent()) {
            String allowedSender = connectedUser.asString();
            throw new MailboxSendingNotAllowedException(allowedSender);
        }
    }

    private void assertValidUpdate(List<MessageResult> messagesToBeUpdated, UpdateMessagePatch updateMessagePatch, MailboxSession session) throws MailboxException {
        List<MailboxId> outboxMailboxes = mailboxIdFor(Role.OUTBOX, session);

        ImmutableList<MailboxId> previousMailboxes = messagesToBeUpdated.stream()
            .map(MessageResult::getMailboxId)
            .collect(Guavate.toImmutableList());
        List<MailboxId> targetMailboxes = getTargetedMailboxes(previousMailboxes, updateMessagePatch);

        boolean isDraft = messagesToBeUpdated.stream()
            .map(MessageResult::getFlags)
            .map(Keywords.lenientFactory()::fromFlags)
            .reduce(new KeywordsCombiner())
            .orElse(Keywords.DEFAULT_VALUE)
            .contains(Keyword.DRAFT);

        MessageMoves messageMoves = MessageMoves.builder()
            .previousMailboxIds(previousMailboxes)
            .targetMailboxIds(targetMailboxes)
            .build();

        boolean targetContainsOutbox = messageMoves.addedMailboxIds().stream().anyMatch(outboxMailboxes::contains);
        boolean targetIsOnlyOutbox = targetMailboxes.stream().allMatch(outboxMailboxes::contains);

        assertOutboxMoveTargetsOnlyOutBox(targetContainsOutbox, targetIsOnlyOutbox);
        assertOutboxMoveOriginallyHasDraftKeywordSet(targetContainsOutbox, isDraft);
    }

    private void assertOutboxMoveTargetsOnlyOutBox(boolean targetContainsOutbox, boolean targetIsOnlyOutbox) {
        if (targetContainsOutbox && !targetIsOnlyOutbox) {
            throw new InvalidOutboxMoveException("When moving a message to Outbox, only Outboxes mailboxes should be targeted.");
        }
    }

    private void assertOutboxMoveOriginallyHasDraftKeywordSet(boolean targetIsOutbox, boolean isDraft) {
        if (targetIsOutbox && !isDraft) {
            throw new InvalidOutboxMoveException("Only message with `$Draft` keyword can be moved to Outbox");
        }
    }

    private List<MailboxId> getTargetedMailboxes(ImmutableList<MailboxId> previousMailboxes, UpdateMessagePatch updateMessagePatch) {
        return updateMessagePatch.getMailboxIds()
            .map(ids -> ids.stream().map(mailboxIdFactory::fromString).collect(Guavate.toImmutableList()))
            .orElse(previousMailboxes);
    }

    private List<MailboxId> mailboxIdFor(Role role, MailboxSession session) throws MailboxException {
        return systemMailboxesProvider.getMailboxByRole(role, session.getUser())
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
        return systemMailboxesProvider.getMailboxByRole(role, session.getUser())
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
                        .type(SetError.Type.NOT_FOUND)
                        .properties(ImmutableSet.of(MessageProperties.MessageProperty.id))
                        .description("message not found")
                        .build()));
    }

    private SetMessagesResponse.Builder handleDraftMessageMailboxUpdateException(MessageId messageId,
                                                                     SetMessagesResponse.Builder builder,
                                                                     DraftMessageMailboxUpdateException e) {
        return builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
            .type(SetError.Type.INVALID_ARGUMENTS)
            .properties(MessageProperties.MessageProperty.mailboxIds)
            .description(e.getMessage())
            .build()));
    }

    private SetMessagesResponse.Builder handleMessageUpdateException(MessageId messageId,
                                              SetMessagesResponse.Builder builder,
                                              Exception e) {
        LOGGER.error("An error occurred when updating a message", e);
        return builder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type(SetError.Type.ERROR)
                .description("An error occurred when updating a message")
                .build()));
    }

    private void handleInvalidRequest(SetMessagesResponse.Builder responseBuilder, MessageId messageId,
                                      ImmutableList<ValidationResult> validationErrors, UpdateMessagePatch patch) {
        LOGGER.warn("Invalid update request with patch {} for message #{}: {}", patch, messageId, validationErrors);

        String formattedValidationErrorMessage = validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining(", "));

        Set<MessageProperties.MessageProperty> properties = validationErrors.stream()
                .flatMap(err -> MessageProperties.MessageProperty.find(err.getProperty()))
                .collect(Collectors.toSet());

        responseBuilder.notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type(SetError.Type.INVALID_PROPERTIES)
                .properties(properties)
                .description(formattedValidationErrorMessage)
                .build()));

    }
}
