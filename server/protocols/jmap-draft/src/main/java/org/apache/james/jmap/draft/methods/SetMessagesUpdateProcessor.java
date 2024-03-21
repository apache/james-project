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
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.InvalidOutboxMoveException;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.jmap.draft.model.UpdateMessagePatch;
import org.apache.james.jmap.draft.utils.KeywordsCombiner;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SetMessagesUpdateProcessor implements SetMessagesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMessagesUpdateProcessor.class);

    private final UpdateMessagePatchConverter updatePatchConverter;
    private final MessageIdManager messageIdManager;
    private final MailboxManager mailboxManager;
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
            MailboxManager mailboxManager,
            SystemMailboxesProvider systemMailboxesProvider,
            Factory mailboxIdFactory,
            MessageSender messageSender,
            MetricFactory metricFactory,
            ReferenceUpdater referenceUpdater,
            CanSendFrom canSendFrom) {
        this.updatePatchConverter = updatePatchConverter;
        this.messageIdManager = messageIdManager;
        this.mailboxManager = mailboxManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.mailboxIdFactory = mailboxIdFactory;
        this.metricFactory = metricFactory;
        this.messageSender = messageSender;
        this.referenceUpdater = referenceUpdater;
        this.canSendFrom = canSendFrom;
    }

    @Override
    public Mono<SetMessagesResponse> processReactive(SetMessagesRequest request, MailboxSession mailboxSession) {
        if (!request.hasUpdates()) {
            return Mono.just(SetMessagesResponse.builder().build());
        }
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + "SetMessagesUpdateProcessor",
            listMailboxIdsForRole(mailboxSession, Role.OUTBOX)
                .flatMap(outboxIds -> prepareResponse(request, mailboxSession, outboxIds).map(SetMessagesResponse.Builder::build))
                .onErrorResume(e ->
                    Mono.just(request.buildUpdatePatches(updatePatchConverter).entrySet().stream()
                        .map(entry -> prepareResponseIfCantReadOutboxes(e, entry.getKey(), entry.getValue()))
                        .reduce(SetMessagesResponse.Builder::mergeWith)
                        .orElse(SetMessagesResponse.builder())
                        .build()))));
    }

    private SetMessagesResponse.Builder prepareResponseIfCantReadOutboxes(Throwable e, MessageId id, UpdateMessagePatch patch) {
        if (patch.isValid()) {
            return handleMessageUpdateException(id, e);
        } else {
            return handleInvalidRequest(id, patch.getValidationErrors(), patch);
        }
    }

    private Mono<SetMessagesResponse.Builder> prepareResponse(SetMessagesRequest request, MailboxSession mailboxSession, Set<MailboxId> outboxes) {
        Map<MessageId, UpdateMessagePatch> patches = request.buildUpdatePatches(updatePatchConverter);

        return Flux.from(messageIdManager.messagesMetadata(patches.keySet(), mailboxSession))
            .collect(ImmutableListMultimap.toImmutableListMultimap(metaData -> metaData.getComposedMessageId().getMessageId(), Function.identity()))
            .flatMap(messages -> {
                if (isAMassiveFlagUpdate(patches, messages)) {
                    return Mono.fromCallable(() -> applyRangedFlagUpdate(patches, messages, mailboxSession))
                        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
                } else if (isAMassiveMove(patches, messages)) {
                    return Mono.fromCallable(() -> applyMove(patches, messages, mailboxSession))
                        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
                } else {
                    return Flux.fromIterable(patches.entrySet())
                        .flatMap(entry -> {
                            if (entry.getValue().isValid()) {
                                return update(outboxes, entry.getKey(), entry.getValue(), mailboxSession, messages);
                            } else {
                                return Mono.just(handleInvalidRequest(entry.getKey(), entry.getValue().getValidationErrors(), entry.getValue()));
                            }

                        }).reduce(SetMessagesResponse.Builder::mergeWith)
                        .switchIfEmpty(Mono.just(SetMessagesResponse.builder()));
                }
            });
    }

    private boolean isAMassiveFlagUpdate(Map<MessageId, UpdateMessagePatch> patches, Multimap<MessageId, ComposedMessageIdWithMetaData> messages) {
        // The same patch, that only represents a flag update, is applied to messages within a single mailbox
        return StreamUtils.isSingleValued(patches.values().stream())
            && StreamUtils.isSingleValued(messages.values().stream().map(metaData -> metaData.getComposedMessageId().getMailboxId()))
            && patches.values().iterator().next().isOnlyAFlagUpdate()
            && messages.size() > 3;
    }

    private boolean isAMassiveMove(Map<MessageId, UpdateMessagePatch> patches, Multimap<MessageId, ComposedMessageIdWithMetaData> messages) {
        // The same patch, that only represents a flag update, is applied to messages within a single mailbox
        return StreamUtils.isSingleValued(patches.values().stream())
            && StreamUtils.isSingleValued(messages.values().stream().map(metaData -> metaData.getComposedMessageId().getMailboxId()))
            && patches.values().iterator().next().isOnlyAMove()
            && messages.size() > 3;
    }

    private SetMessagesResponse.Builder applyRangedFlagUpdate(Map<MessageId, UpdateMessagePatch> patches, Multimap<MessageId, ComposedMessageIdWithMetaData> messages, MailboxSession mailboxSession) {
        MailboxId mailboxId = messages.values()
            .iterator()
            .next()
            .getComposedMessageId()
            .getMailboxId();
        UpdateMessagePatch patch = patches.values().iterator().next();
        List<MessageRange> uidRanges = MessageRange.toRanges(messages.values().stream().map(metaData -> metaData.getComposedMessageId().getUid())
            .distinct()
            .collect(ImmutableList.toImmutableList()));

        if (patch.isValid()) {
            return uidRanges.stream().map(range -> {
                ImmutableList<MessageId> messageIds = messages.entries()
                    .stream()
                    .filter(entry -> range.includes(entry.getValue().getComposedMessageId().getUid()))
                    .map(Map.Entry::getKey)
                    .distinct()
                    .collect(ImmutableList.toImmutableList());
                try {
                    mailboxManager.getMailbox(mailboxId, mailboxSession)
                        .setFlags(patch.applyToState(new Flags()), FlagsUpdateMode.REPLACE, range, mailboxSession);
                    return SetMessagesResponse.builder().updated(messageIds);
                } catch (MailboxException e) {
                    return messageIds.stream()
                        .map(messageId -> handleMessageUpdateException(messageId, e))
                        .reduce(SetMessagesResponse.Builder::mergeWith)
                        .orElse(SetMessagesResponse.builder());
                } catch (IllegalArgumentException e) {
                    ValidationResult invalidPropertyKeywords = ValidationResult.builder()
                        .property(MessageProperties.MessageProperty.keywords.asFieldName())
                        .message(e.getMessage())
                        .build();

                    return messageIds.stream()
                        .map(messageId -> handleInvalidRequest(messageId, ImmutableList.of(invalidPropertyKeywords), patch))
                        .reduce(SetMessagesResponse.Builder::mergeWith)
                        .orElse(SetMessagesResponse.builder());
                }
            }).reduce(SetMessagesResponse.Builder::mergeWith)
                .orElse(SetMessagesResponse.builder());
        } else {
            return messages.keySet().stream()
                .map(messageId -> handleInvalidRequest(messageId, patch.getValidationErrors(), patch))
                .reduce(SetMessagesResponse.Builder::mergeWith)
                .orElse(SetMessagesResponse.builder());
        }
    }

    private SetMessagesResponse.Builder applyMove(Map<MessageId, UpdateMessagePatch> patches, Multimap<MessageId, ComposedMessageIdWithMetaData> messages, MailboxSession mailboxSession) {
        MailboxId mailboxId = messages.values()
            .iterator()
            .next()
            .getComposedMessageId()
            .getMailboxId();
        UpdateMessagePatch patch = patches.values().iterator().next();
        List<MessageRange> uidRanges = MessageRange.toRanges(messages.values().stream().map(metaData -> metaData.getComposedMessageId().getUid())
            .distinct()
            .collect(ImmutableList.toImmutableList()));

        if (patch.isValid()) {
            return uidRanges.stream().map(range -> {
                ImmutableList<MessageId> messageIds = messages.entries()
                    .stream()
                    .filter(entry -> range.includes(entry.getValue().getComposedMessageId().getUid()))
                    .map(Map.Entry::getKey)
                    .distinct()
                    .collect(ImmutableList.toImmutableList());
                try {
                    MailboxId targetId = mailboxIdFactory.fromString(patch.getMailboxIds().get().iterator().next());
                    mailboxManager.moveMessages(range, mailboxId, targetId, mailboxSession);
                    return SetMessagesResponse.builder().updated(messageIds);
                } catch (OverQuotaException e) {
                    return messageIds.stream()
                        .map(messageId -> handleMessageUpdateQuotaException(messageId, e))
                        .reduce(SetMessagesResponse.Builder::mergeWith)
                        .orElse(SetMessagesResponse.builder());
                } catch (MailboxException e) {
                    return messageIds.stream()
                        .map(messageId -> handleMessageUpdateException(messageId, e))
                        .reduce(SetMessagesResponse.Builder::mergeWith)
                        .orElse(SetMessagesResponse.builder());
                } catch (IllegalArgumentException e) {
                    ValidationResult invalidPropertyKeywords = ValidationResult.builder()
                        .property(MessageProperties.MessageProperty.keywords.asFieldName())
                        .message(e.getMessage())
                        .build();

                    return messageIds.stream()
                        .map(messageId -> handleInvalidRequest(messageId, ImmutableList.of(invalidPropertyKeywords), patch))
                        .reduce(SetMessagesResponse.Builder::mergeWith)
                        .orElse(SetMessagesResponse.builder());
                }
            }).reduce(SetMessagesResponse.Builder::mergeWith)
                .orElse(SetMessagesResponse.builder());
        } else {
            return messages.keySet().stream()
                .map(messageId -> handleInvalidRequest(messageId, patch.getValidationErrors(), patch))
                .reduce(SetMessagesResponse.Builder::mergeWith)
                .orElse(SetMessagesResponse.builder());
        }
    }

    private Mono<SetMessagesResponse.Builder> update(Set<MailboxId> outboxes, MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, Multimap<MessageId, ComposedMessageIdWithMetaData> metadata) {
        try {
            List<ComposedMessageIdWithMetaData> messages = Optional.ofNullable(metadata.get(messageId))
                .map(ImmutableList::copyOf)
                .orElse(ImmutableList.of());
            assertValidUpdate(messages, updateMessagePatch, outboxes);

            if (messages.isEmpty()) {
                return Mono.just(SetMessagesResponse.builder().mergeWith(addMessageIdNotFoundToResponse(messageId)));
            } else {
                return setInMailboxes(messageId, updateMessagePatch, mailboxSession)
                    .then(Flux.fromIterable(messages)
                        .flatMap(message -> updateFlags(messageId, updateMessagePatch, mailboxSession, message))
                        .then())
                    .then(Mono.just(SetMessagesResponse.builder().updated(ImmutableList.of(messageId))))
                    .flatMap(builder -> sendMessageWhenOutboxInTargetMailboxIds(outboxes, messageId, updateMessagePatch, mailboxSession)
                        .map(builder::mergeWith))
                    .onErrorResume(OverQuotaException.class, e -> Mono.just(handleMessageUpdateQuotaException(messageId, e)))
                    .onErrorResume(MailboxException.class, e -> Mono.just(handleMessageUpdateException(messageId, e)))
                    .onErrorResume(IOException.class, e -> Mono.just(handleMessageUpdateException(messageId, e)))
                    .onErrorResume(MessagingException.class, e -> Mono.just(handleMessageUpdateException(messageId, e)))
                    .onErrorResume(IllegalArgumentException.class, e -> {
                        ValidationResult invalidPropertyKeywords = ValidationResult.builder()
                            .property(MessageProperties.MessageProperty.keywords.asFieldName())
                            .message(e.getMessage())
                            .build();

                        return Mono.just(handleInvalidRequest(messageId, ImmutableList.of(invalidPropertyKeywords), updateMessagePatch));
                    });
            }
        } catch (InvalidOutboxMoveException e) {
            ValidationResult invalidPropertyMailboxIds = ValidationResult.builder()
                .property(MessageProperties.MessageProperty.mailboxIds.asFieldName())
                .message(e.getMessage())
                .build();

            return Mono.just(handleInvalidRequest(messageId, ImmutableList.of(invalidPropertyMailboxIds), updateMessagePatch));
        }
    }

    private Mono<SetMessagesResponse.Builder> sendMessageWhenOutboxInTargetMailboxIds(Set<MailboxId> outboxes, MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession) {
        if (isTargetingOutbox(outboxes, listTargetMailboxIds(updateMessagePatch))) {
            return Mono.from(messageIdManager.getMessagesReactive(ImmutableList.of(messageId), FetchGroup.FULL_CONTENT, mailboxSession))
                .flatMap(messageToSend -> Mono.fromCallable(() -> buildMailFromMessage(messageToSend))
                    .flatMap(mail -> assertUserCanSendFrom(mailboxSession.getUser(), mail.getMaybeSender().asOptional().map(Username::fromMailAddress))
                        .then(Mono.just(Pair.of(messageToSend, mail)))))
                .flatMap(Throwing.<Pair<MessageResult, MailImpl>, Mono<SetMessagesResponse.Builder>>function(
                    pair -> messageSender.sendMessage(messageId, pair.getRight(), mailboxSession)
                        .then(referenceUpdater.updateReferences(pair.getKey().getHeaders(), mailboxSession))
                        .thenReturn(SetMessagesResponse.builder())).sneakyThrow())
                .switchIfEmpty(Mono.just(addMessageIdNotFoundToResponse(messageId)));
        }
        return Mono.just(SetMessagesResponse.builder());
    }

    @VisibleForTesting
    Mono<Void> assertUserCanSendFrom(Username connectedUser, Optional<Username> maybeFromUser) {
        return Mono.justOrEmpty(maybeFromUser)
            .flatMap(fromUser -> Mono.from(canSendFrom.userCanSendFromReactive(connectedUser, fromUser)))
            .filter(Boolean::booleanValue)
            .doOnNext(bool -> LOGGER.debug("{} is allowed to send a mail using {} identity", connectedUser.asString(), maybeFromUser))
            .switchIfEmpty(Mono.error(() -> new MailboxSendingNotAllowedException(connectedUser, maybeFromUser)))
            .then();
    }

    private void assertValidUpdate(List<ComposedMessageIdWithMetaData> messagesToBeUpdated,
                                   UpdateMessagePatch updateMessagePatch,
                                   Set<MailboxId> outboxMailboxes) {
        ImmutableList<MailboxId> previousMailboxes = messagesToBeUpdated.stream()
            .map(metaData -> metaData.getComposedMessageId().getMailboxId())
            .collect(ImmutableList.toImmutableList());
        List<MailboxId> targetMailboxes = getTargetedMailboxes(previousMailboxes, updateMessagePatch);

        boolean isDraft = messagesToBeUpdated.stream()
            .map(ComposedMessageIdWithMetaData::getFlags)
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
            .map(ids -> ids.stream().map(mailboxIdFactory::fromString).collect(ImmutableList.toImmutableList()))
            .orElse(previousMailboxes);
    }

    private MailImpl buildMailFromMessage(MessageResult message) throws MessagingException, IOException, MailboxException {
        return MailImpl.fromMimeMessage(message.getMessageId().serialize(),
            new MimeMessage(
                Session.getDefaultInstance(new Properties()),
                message.getFullContent().getInputStream()));
    }

    private Set<MailboxId> listTargetMailboxIds(UpdateMessagePatch updateMessagePatch) {
        return updateMessagePatch.getMailboxIds()
            .stream()
            .flatMap(Collection::stream)
            .map(mailboxIdFactory::fromString)
            .collect(ImmutableSet.toImmutableSet());
    }

    private boolean isTargetingOutbox(Set<MailboxId> outboxes, Set<MailboxId> targetMailboxIds) {
        return targetMailboxIds.stream().anyMatch(outboxes::contains);
    }

    private Mono<Set<MailboxId>> listMailboxIdsForRole(MailboxSession session, Role role) {
        return Flux.from(systemMailboxesProvider.getMailboxByRole(role, session.getUser()))
            .map(MessageManager::getId)
            .collect(ImmutableSet.toImmutableSet());
    }

    private Mono<Void> updateFlags(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession, ComposedMessageIdWithMetaData message) {
        if (!updateMessagePatch.isFlagsIdentity()) {
            return Mono.from(messageIdManager.setFlagsReactive(
                updateMessagePatch.applyToState(message.getFlags()),
                FlagsUpdateMode.REPLACE, messageId, ImmutableList.of(message.getComposedMessageId().getMailboxId()), mailboxSession));
        }
        return Mono.empty();
    }

    private Mono<Void> setInMailboxes(MessageId messageId, UpdateMessagePatch updateMessagePatch, MailboxSession mailboxSession) {
        Optional<List<String>> serializedMailboxIds = updateMessagePatch.getMailboxIds();
        if (serializedMailboxIds.isPresent()) {
            List<MailboxId> mailboxIds = serializedMailboxIds.get()
                .stream()
                .map(mailboxIdFactory::fromString)
                .collect(ImmutableList.toImmutableList());

            return Mono.from(messageIdManager.setInMailboxesReactive(messageId, mailboxIds, mailboxSession));
        }
        return Mono.empty();
    }

    private SetMessagesResponse.Builder addMessageIdNotFoundToResponse(MessageId messageId) {
        return SetMessagesResponse.builder().notUpdated(ImmutableMap.of(messageId,
            SetError.builder()
                .type(SetError.Type.NOT_FOUND)
                .properties(ImmutableSet.of(MessageProperties.MessageProperty.id))
                .description("message not found")
                .build()));
    }

    private SetMessagesResponse.Builder handleMessageUpdateException(MessageId messageId,
                                                                     Throwable e) {
        LOGGER.error("An error occurred when updating a message", e);
        return SetMessagesResponse.builder().notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type(SetError.Type.ERROR)
                .description("An error occurred when updating a message")
                .build()));
    }

    private SetMessagesResponse.Builder handleMessageUpdateQuotaException(MessageId messageId,
                                                                     Throwable e) {
        LOGGER.error("A quota restriction error occurred when updating a message", e);
        return SetMessagesResponse.builder().notUpdated(messageId,
                SetError.builder()
                        .type(SetError.Type.MAX_QUOTA_REACHED)
                        .description(e.getMessage())
                        .build());
    }

    private SetMessagesResponse.Builder handleInvalidRequest(MessageId messageId,
                                      ImmutableList<ValidationResult> validationErrors, UpdateMessagePatch patch) {
        LOGGER.warn("Invalid update request with patch {} for message #{}: {}", patch, messageId, validationErrors);

        String formattedValidationErrorMessage = validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining(", "));

        Set<MessageProperties.MessageProperty> properties = validationErrors.stream()
                .flatMap(err -> MessageProperties.MessageProperty.find(err.getProperty()))
                .collect(Collectors.toSet());

        return SetMessagesResponse.builder().notUpdated(ImmutableMap.of(messageId, SetError.builder()
                .type(SetError.Type.INVALID_PROPERTIES)
                .properties(properties)
                .description(formattedValidationErrorMessage)
                .build()));

    }
}
