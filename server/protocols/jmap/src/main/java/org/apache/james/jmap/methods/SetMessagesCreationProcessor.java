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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.core.User;
import org.apache.james.jmap.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.exceptions.InvalidDraftKeywordsException;
import org.apache.james.jmap.exceptions.InvalidMailboxForCreationException;
import org.apache.james.jmap.exceptions.MailboxNotOwnedException;
import org.apache.james.jmap.exceptions.MessageHasNoMailboxException;
import org.apache.james.jmap.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.model.EnvelopeUtils;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageFactory.MetaDataWithContent;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.SetMessagesResponse.Builder;
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
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.server.core.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.FunctionChainer;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;


public class SetMessagesCreationProcessor implements SetMessagesProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetMailboxesCreationProcessor.class);
    private final MessageFactory messageFactory;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final AttachmentChecker attachmentChecker;
    private final MetricFactory metricFactory;
    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageAppender messageAppender;
    private final MessageSender messageSender;
    private final ReferenceUpdater referenceUpdater;
    
    @VisibleForTesting
    @Inject
    SetMessagesCreationProcessor(MessageFactory messageFactory,
                                 SystemMailboxesProvider systemMailboxesProvider,
                                 AttachmentChecker attachmentChecker,
                                 MetricFactory metricFactory,
                                 MailboxManager mailboxManager,
                                 MailboxId.Factory mailboxIdFactory,
                                 MessageAppender messageAppender,
                                 MessageSender messageSender,
                                 ReferenceUpdater referenceUpdater) {
        this.messageFactory = messageFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.attachmentChecker = attachmentChecker;
        this.metricFactory = metricFactory;
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageAppender = messageAppender;
        this.messageSender = messageSender;
        this.referenceUpdater = referenceUpdater;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "SetMessageCreationProcessor");

        Builder responseBuilder = SetMessagesResponse.builder();
        request.getCreate()
            .forEach(create -> handleCreate(create, responseBuilder, mailboxSession));

        timeMetric.stopAndPublish();
        return responseBuilder.build();
    }

    private void handleCreate(CreationMessageEntry create, Builder responseBuilder, MailboxSession mailboxSession) {
        try {
            List<MailboxId> mailboxIds = toMailboxIds(create);
            assertAtLeastOneMailbox(mailboxIds);
            assertIsUserOwnerOfMailboxes(mailboxIds, mailboxSession);
            performCreate(create, responseBuilder, mailboxSession);
        } catch (MailboxSendingNotAllowedException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type(SetError.Type.INVALID_PROPERTIES)
                        .properties(MessageProperty.from)
                        .description("Invalid 'from' field. Must be " +
                                e.getAllowedFrom())
                        .build());

        } catch (InvalidDraftKeywordsException e) {
            responseBuilder.notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.INVALID_PROPERTIES)
                    .properties(MessageProperty.keywords)
                    .description(e.getMessage())
                    .build());

        } catch (AttachmentsNotFoundException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetMessagesError.builder()
                        .type(SetError.Type.INVALID_PROPERTIES)
                        .properties(MessageProperty.attachments)
                        .attachmentsNotFound(e.getAttachmentIds())
                        .description("Attachment not found")
                        .build());
            
        } catch (InvalidMailboxForCreationException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type(SetError.Type.INVALID_PROPERTIES)
                        .properties(MessageProperty.mailboxIds)
                        .description("Message creation is only supported in mailboxes with role Draft and Outbox")
                        .build());

        } catch (MessageHasNoMailboxException e) {
            responseBuilder.notCreated(create.getCreationId(),
                    SetError.builder()
                        .type(SetError.Type.INVALID_PROPERTIES)
                        .properties(MessageProperty.mailboxIds)
                        .description("Message needs to be in at least one mailbox")
                        .build());

        } catch (MailboxInvalidMessageCreationException e) {
            responseBuilder.notCreated(create.getCreationId(),
                    buildSetErrorFromValidationResult(create.getValue().validate()));

        } catch (MailboxNotFoundException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type(SetError.Type.ERROR)
                        .description(e.getMessage())
                        .build());

        } catch (MailboxNotOwnedException e) {
            LOG.error("Appending message in an unknown mailbox", e);
            responseBuilder.notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.ERROR)
                    .properties(MessageProperty.mailboxIds)
                    .description("MailboxId invalid")
                    .build());

        } catch (OverQuotaException e) {
            responseBuilder.notCreated(create.getCreationId(),
                SetError.builder()
                    .type(SetError.Type.MAX_QUOTA_REACHED)
                    .description(e.getMessage())
                    .build());

        } catch (MailboxException | MessagingException e) {
            LOG.error("Unexpected error while creating message", e);
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type(SetError.Type.ERROR)
                        .description("unexpected error")
                        .build());
        }
    }

    private ImmutableList<MailboxId> toMailboxIds(CreationMessageEntry create) {
        return create.getValue().getMailboxIds()
            .stream()
            .distinct()
            .map(mailboxIdFactory::fromString)
            .collect(Guavate.toImmutableList());
    }

    private void performCreate(CreationMessageEntry entry, Builder responseBuilder, MailboxSession session) throws MailboxException, InvalidMailboxForCreationException, MessagingException, AttachmentsNotFoundException {
        if (isAppendToMailboxWithRole(Role.OUTBOX, entry.getValue(), session)) {
            sendMailViaOutbox(entry, responseBuilder, session);
        } else if (entry.getValue().isDraft()) {
            assertNoOutbox(entry, session);
            saveDraft(entry, responseBuilder, session);
        } else {
            if (isAppendToMailboxWithRole(Role.DRAFTS, entry.getValue(), session)) {
                throw new InvalidDraftKeywordsException("A draft message should be flagged as Draft");
            }
            throw new InvalidMailboxForCreationException("The only implemented feature is sending via outbox and draft saving");
        }
    }

    private void assertNoOutbox(CreationMessageEntry entry, MailboxSession session) throws MailboxException {
        if (isTargettingAMailboxWithRole(Role.OUTBOX, entry.getValue(), session)) {
            throw new InvalidMailboxForCreationException("Mailbox ids can combine Outbox with other mailbox");
        }
    }

    private void assertAtLeastOneMailbox(List<MailboxId> mailboxIds) throws MailboxException {
        if (mailboxIds.isEmpty()) {
            throw new MessageHasNoMailboxException();
        }
    }

    private void sendMailViaOutbox(CreationMessageEntry entry, Builder responseBuilder, MailboxSession session) throws AttachmentsNotFoundException, MailboxException, MessagingException {
        validateArguments(entry, session);
        MessageWithId created = handleOutboxMessages(entry, session);
        responseBuilder.created(created.getCreationId(), created.getValue());
    }

    private void saveDraft(CreationMessageEntry entry, Builder responseBuilder, MailboxSession session) throws AttachmentsNotFoundException, MailboxException, MessagingException {
        attachmentChecker.assertAttachmentsExist(entry, session);
        MessageWithId created = handleDraftMessages(entry, session);
        responseBuilder.created(created.getCreationId(), created.getValue());
    }

    private void validateArguments(CreationMessageEntry entry, MailboxSession session) throws MailboxInvalidMessageCreationException, AttachmentsNotFoundException, MailboxException {
        CreationMessage message = entry.getValue();
        if (!message.isValid()) {
            throw new MailboxInvalidMessageCreationException();
        }
        attachmentChecker.assertAttachmentsExist(entry, session);
    }

    @VisibleForTesting void assertIsUserOwnerOfMailboxes(List<MailboxId> mailboxIds, MailboxSession session) throws MailboxNotOwnedException {
        if (!allMailboxOwned(mailboxIds, session)) {
            throw new MailboxNotOwnedException();
        }
    }

    private boolean allMailboxOwned(List<MailboxId> mailboxIds, MailboxSession session) {
        FunctionChainer<MailboxId, MessageManager> findMailbox = Throwing.function(mailboxId -> mailboxManager.getMailbox(mailboxId, session));
        return mailboxIds.stream()
            .map(findMailbox.sneakyThrow())
            .map(Throwing.function(MessageManager::getMailboxPath))
            .allMatch(path -> path.belongsTo(session));
    }

    private MessageWithId handleOutboxMessages(CreationMessageEntry entry, MailboxSession session) throws MailboxException, MessagingException {
        assertUserIsSender(session, entry.getValue().getFrom());
        MetaDataWithContent newMessage = messageAppender.appendMessageInMailboxes(entry, toMailboxIds(entry), session);
        Message jmapMessage = messageFactory.fromMetaDataWithContent(newMessage);
        Envelope envelope = EnvelopeUtils.fromMessage(jmapMessage);
        messageSender.sendMessage(newMessage, envelope, session);
        referenceUpdater.updateReferences(entry.getValue().getHeaders(), session);
        return new ValueWithId.MessageWithId(entry.getCreationId(), jmapMessage);
    }

    private void assertUserIsSender(MailboxSession session, Optional<DraftEmailer> from) throws MailboxSendingNotAllowedException {
        if (!from.flatMap(DraftEmailer::getEmail)
                .filter(email -> session.getUser().equals(User.fromUsername(email)))
                .isPresent()) {
            String allowedSender = session.getUser().asString();
            throw new MailboxSendingNotAllowedException(allowedSender);
        }
    }

    private MessageWithId handleDraftMessages(CreationMessageEntry entry, MailboxSession session) throws MailboxException, MessagingException {
        MetaDataWithContent newMessage = messageAppender.appendMessageInMailboxes(entry, toMailboxIds(entry), session);
        Message jmapMessage = messageFactory.fromMetaDataWithContent(newMessage);
        return new ValueWithId.MessageWithId(entry.getCreationId(), jmapMessage);
    }
    
    private boolean isAppendToMailboxWithRole(Role role, CreationMessage entry, MailboxSession mailboxSession) throws MailboxException {
        return getMailboxWithRole(mailboxSession, role)
                .map(entry::isOnlyIn)
                .orElse(false);
    }

    private boolean isTargettingAMailboxWithRole(Role role, CreationMessage entry, MailboxSession mailboxSession) throws MailboxException {
        return getMailboxWithRole(mailboxSession, role)
                .map(entry::isIn)
                .orElse(false);
    }

    private Optional<MessageManager> getMailboxWithRole(MailboxSession mailboxSession, Role role) throws MailboxException {
        return systemMailboxesProvider.getMailboxByRole(role, mailboxSession.getUser()).findFirst();
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
                .flatMap(err -> propertiesSplitter.splitToList(err.getProperty()).stream())
                .flatMap(MessageProperty::find)
                .collect(Collectors.toSet());
    }

}
