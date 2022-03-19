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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import jakarta.mail.MessagingException;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.draft.exceptions.InvalidDraftKeywordsException;
import org.apache.james.jmap.draft.exceptions.InvalidMailboxForCreationException;
import org.apache.james.jmap.draft.exceptions.MailboxNotOwnedException;
import org.apache.james.jmap.draft.exceptions.SizeExceededException;
import org.apache.james.jmap.draft.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.draft.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.draft.model.EnvelopeUtils;
import org.apache.james.jmap.draft.model.MessageProperties;
import org.apache.james.jmap.draft.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMessagesError;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.jmap.draft.model.SetMessagesResponse.Builder;
import org.apache.james.jmap.draft.model.message.view.MessageFullView;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.server.core.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public class SetMessagesCreationProcessor implements SetMessagesProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetMailboxesCreationProcessor.class);
    private final MessageFullViewFactory messageFullViewFactory;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final MetricFactory metricFactory;
    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageAppender messageAppender;
    private final MessageSender messageSender;
    private final ReferenceUpdater referenceUpdater;
    private final CanSendFrom canSendFrom;

    @VisibleForTesting
    @Inject
    SetMessagesCreationProcessor(MessageFullViewFactory messageFullViewFactory,
                                 SystemMailboxesProvider systemMailboxesProvider,
                                 MetricFactory metricFactory,
                                 MailboxManager mailboxManager,
                                 MailboxId.Factory mailboxIdFactory,
                                 MessageAppender messageAppender,
                                 MessageSender messageSender,
                                 ReferenceUpdater referenceUpdater,
                                 CanSendFrom canSendFrom) {
        this.messageFullViewFactory = messageFullViewFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.metricFactory = metricFactory;
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageAppender = messageAppender;
        this.messageSender = messageSender;
        this.referenceUpdater = referenceUpdater;
        this.canSendFrom = canSendFrom;
    }

    @Override
    public Mono<SetMessagesResponse> processReactive(SetMessagesRequest request, MailboxSession mailboxSession) {
        if (request.getCreate().isEmpty()) {
            return Mono.just(SetMessagesResponse.builder().build());
        }
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + "SetMessageCreationProcessor",
            Flux.fromIterable(request.getCreate())
                .flatMap(create -> handleCreate(create, mailboxSession))
                .reduce(Builder::mergeWith)
                .switchIfEmpty(Mono.just(SetMessagesResponse.builder()))
                .map(Builder::build)));
    }

    private Mono<Builder> handleCreate(CreationMessageEntry create, MailboxSession mailboxSession) {
        List<MailboxId> mailboxIds = toMailboxIds(create);

        if (mailboxIds.isEmpty()) {
            return Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.INVALID_PROPERTIES)
                    .properties(MessageProperty.mailboxIds)
                    .description("Message needs to be in at least one mailbox")
                    .build()));
        }

        return assertIsUserOwnerOfMailboxes(mailboxIds, mailboxSession)
            .then(performCreate(create, mailboxSession))
            .onErrorResume(MailboxSendingNotAllowedException.class, e -> {
                LOG.debug("{} is not allowed to send a mail using {} identity", e.getConnectedUser().asString(), e.getFromField());

                return Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                    SetError.builder()
                        .type(SetError.Type.INVALID_PROPERTIES)
                        .properties(MessageProperty.from)
                        .description("Invalid 'from' field. One accepted value is " +
                            e.getConnectedUser().asString())
                        .build()));
            })
            .onErrorResume(InvalidDraftKeywordsException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.INVALID_PROPERTIES)
                    .properties(MessageProperty.keywords)
                    .description(e.getMessage())
                    .build())))
            .onErrorResume(SizeExceededException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description(e.getMessage())
                    .build())))
            .onErrorResume(AttachmentsNotFoundException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetMessagesError.builder()
                    .type(SetError.Type.INVALID_PROPERTIES)
                    .properties(MessageProperty.attachments)
                    .attachmentsNotFound(e.getAttachmentIds())
                    .description("Attachment not found")
                    .build())))
            .onErrorResume(InvalidMailboxForCreationException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.INVALID_PROPERTIES)
                    .properties(MessageProperty.mailboxIds)
                    .description("Message creation is only supported in mailboxes with role Draft and Outbox")
                    .build())))
            .onErrorResume(MailboxInvalidMessageCreationException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                buildSetErrorFromValidationResult(create.getValue().validate()))))
            .onErrorResume(MailboxNotFoundException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description(e.getMessage())
                    .build())))
            .onErrorResume(MailboxNotOwnedException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .properties(MessageProperty.mailboxIds)
                    .description("MailboxId invalid")
                    .build())))
            .onErrorResume(OverQuotaException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.MAX_QUOTA_REACHED)
                    .description(e.getMessage())
                    .build())))
            .onErrorResume(MailboxException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description("unexpected error")
                    .build())))
            .onErrorResume(MessagingException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description("unexpected error")
                    .build())))
            .onErrorResume(IOException.class, e -> Mono.just(SetMessagesResponse.builder().notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description("unexpected error")
                    .build())));
    }

    private ImmutableList<MailboxId> toMailboxIds(CreationMessageEntry create) {
        return create.getValue().getMailboxIds()
            .stream()
            .distinct()
            .map(mailboxIdFactory::fromString)
            .collect(ImmutableList.toImmutableList());
    }

    private Mono<Builder> performCreate(CreationMessageEntry entry, MailboxSession session) {
        return isAppendToMailboxWithRole(Role.OUTBOX, entry.getValue(), session)
            .flatMap(isAppendToMailboxWithRole -> {
                if (isAppendToMailboxWithRole) {
                    return sendMailViaOutbox(entry, session);
                } else if (entry.getValue().isDraft()) {
                    return assertNoOutbox(entry, session)
                        .then(saveDraft(entry, session));
                } else {
                    return isAppendToMailboxWithRole(Role.DRAFTS, entry.getValue(), session)
                        .handle((isAppendedToDraft, sink) -> {
                            if (isAppendedToDraft) {
                                sink.error(new InvalidDraftKeywordsException("A draft message should be flagged as Draft"));
                            } else {
                                sink.error(new InvalidMailboxForCreationException("The only implemented feature is sending via outbox and draft saving"));
                            }
                        });
                }
            });
    }

    private Mono<Void> assertNoOutbox(CreationMessageEntry entry, MailboxSession session) {
        return isTargettingAMailboxWithRole(Role.OUTBOX, entry.getValue(), session)
            .handle((targetsOutbox, sink) -> {
                if (targetsOutbox) {
                    sink.error(new InvalidMailboxForCreationException("Mailbox ids can combine Outbox with other mailbox"));
                }
            });
    }

    private Mono<Builder> sendMailViaOutbox(CreationMessageEntry entry, MailboxSession session) {
        if (!entry.getValue().isValid()) {
            return Mono.error(new MailboxInvalidMessageCreationException());
        }
        return handleOutboxMessages(entry, session)
                .map(created -> SetMessagesResponse.builder().created(created.getCreationId(), created.getValue()));
    }

    private Mono<Builder> saveDraft(CreationMessageEntry entry, MailboxSession session) {
        return handleDraftMessages(entry, session)
                .map(created -> SetMessagesResponse.builder().created(created.getCreationId(), created.getValue()));
    }

    @VisibleForTesting Mono<Void> assertIsUserOwnerOfMailboxes(List<MailboxId> mailboxIds, MailboxSession session) {
        return allMailboxOwned(mailboxIds, session)
            .handle((allOwned, sink) -> {
                if (!allOwned) {
                    sink.error(new MailboxNotOwnedException());
                }
            });
    }

    private Mono<Boolean> allMailboxOwned(List<MailboxId> mailboxIds, MailboxSession session) {
        return Flux.fromIterable(mailboxIds)
            .concatMap(id ->  mailboxManager.getMailboxReactive(id, session))
            .map(Throwing.function(MessageManager::getMailboxPath))
            .all(path -> path.belongsTo(session));
    }

    private Mono<MessageWithId> handleOutboxMessages(CreationMessageEntry entry, MailboxSession session) {
        return assertUserCanSendFrom(session.getUser(), entry.getValue().getFrom())
            .then(messageAppender.appendMessageInMailboxes(entry, toMailboxIds(entry), session))
            .flatMap(newMessage ->
                messageFullViewFactory.fromMetaDataWithContent(newMessage)
                    .flatMap(Throwing.function((MessageFullView jmapMessage) -> {
                        Envelope envelope = EnvelopeUtils.fromMessage(jmapMessage);
                        return messageSender.sendMessage(newMessage, envelope, session)
                            .then(referenceUpdater.updateReferences(entry.getValue().getHeaders(), session))
                            .thenReturn(new ValueWithId.MessageWithId(entry.getCreationId(), jmapMessage));
                    }).sneakyThrow()));
    }

    @VisibleForTesting
    Mono<Void> assertUserCanSendFrom(Username connectedUser, Optional<DraftEmailer> from) {
        Optional<Username> maybeFromUser = from.flatMap(DraftEmailer::getEmail)
            .map(Username::of);

        return Mono.from(canSendMailUsingIdentity(connectedUser, maybeFromUser))
            .filter(Boolean::booleanValue)
            .doOnNext(bool -> LOG.debug("{} is allowed to send a mail using {} identity", connectedUser.asString(), from))
            .switchIfEmpty(Mono.error(() -> new MailboxSendingNotAllowedException(connectedUser, maybeFromUser)))
            .then();
    }

    private Mono<Boolean> canSendMailUsingIdentity(Username connectedUser, Optional<Username> maybeFromUser) {
        return Mono.justOrEmpty(maybeFromUser)
            .flatMap(fromUser -> Mono.from(canSendFrom.userCanSendFromReactive(connectedUser, fromUser)));
    }

    private Mono<MessageWithId> handleDraftMessages(CreationMessageEntry entry, MailboxSession session) {
        return messageAppender.appendMessageInMailboxes(entry, toMailboxIds(entry), session)
            .flatMap(messageFullViewFactory::fromMetaDataWithContent)
            .map(jmapMessage -> new ValueWithId.MessageWithId(entry.getCreationId(), jmapMessage));
    }

    private Mono<Boolean> isAppendToMailboxWithRole(Role role, CreationMessage entry, MailboxSession mailboxSession) {
        return getMailboxWithRole(mailboxSession, role)
            .map(entry::isOnlyIn)
            .switchIfEmpty(Mono.just(false));
    }

    private Mono<Boolean> isTargettingAMailboxWithRole(Role role, CreationMessage entry, MailboxSession mailboxSession) {
        return getMailboxWithRole(mailboxSession, role)
            .map(entry::isIn)
            .switchIfEmpty(Mono.just(false));
    }

    private Mono<MessageManager> getMailboxWithRole(MailboxSession mailboxSession, Role role) {
        return Flux.from(systemMailboxesProvider.getMailboxByRole(role, mailboxSession.getUser()))
            .next();
    }

    private SetError buildSetErrorFromValidationResult(List<ValidationResult> validationErrors) {
        return SetError.builder()
                .type(SetError.Type.INVALID_PROPERTIES)
                .properties(collectMessageProperties(validationErrors))
                .description(formatValidationErrorMessge(validationErrors))
                .build();
    }

    private String formatValidationErrorMessge(List<ValidationResult> validationErrors) {
        return validationErrors.stream()
                .map(err -> err.getProperty() + ": " + err.getErrorMessage())
                .collect(Collectors.joining("\\n"));
    }

    private Set<MessageProperties.MessageProperty> collectMessageProperties(List<ValidationResult> validationErrors) {
        Splitter propertiesSplitter = Splitter.on(',').trimResults().omitEmptyStrings();
        return validationErrors.stream()
                .flatMap(err -> propertiesSplitter.splitToStream(err.getProperty()))
                .flatMap(MessageProperty::find)
                .collect(Collectors.toSet());
    }

}
