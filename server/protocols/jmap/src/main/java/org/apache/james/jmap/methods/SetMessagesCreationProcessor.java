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

import org.apache.james.jmap.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.CreationMessage;
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
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.utils.SystemMailboxesProvider;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.predicates.ThrowingPredicate;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;


public class SetMessagesCreationProcessor implements SetMessagesProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetMailboxesCreationProcessor.class);
    private final MessageFactory messageFactory;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final AttachmentManager attachmentManager;
    private final MetricFactory metricFactory;
    private final MailboxManager mailboxManager;
    private final MailboxId.Factory mailboxIdFactory;
    private final MessageAppender messageAppender;
    private final MessageSender messageSender;
    
    @VisibleForTesting @Inject
    SetMessagesCreationProcessor(MessageFactory messageFactory, SystemMailboxesProvider systemMailboxesProvider,
                                 AttachmentManager attachmentManager,
                                 MetricFactory metricFactory,
                                 MailboxManager mailboxManager,
                                 MailboxId.Factory mailboxIdFactory, MessageAppender messageAppender, MessageSender messageSender) {
        this.messageFactory = messageFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.attachmentManager = attachmentManager;
        this.metricFactory = metricFactory;
        this.mailboxManager = mailboxManager;
        this.mailboxIdFactory = mailboxIdFactory;
        this.messageAppender = messageAppender;
        this.messageSender = messageSender;
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
            validateImplementedFeature(create, mailboxSession);
            validateArguments(create, mailboxSession);
            validateIsUserOwnerOfMailboxes(create, mailboxSession);
            MessageWithId created = handleOutboxMessages(create, mailboxSession);
            responseBuilder.created(created.getCreationId(), created.getValue());

        } catch (MailboxSendingNotAllowedException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type("invalidProperties")
                        .properties(MessageProperty.from)
                        .description("Invalid 'from' field. Must be one of " + 
                                Joiner.on(", ").join(e.getAllowedFroms()))
                        .build());

        } catch (AttachmentsNotFoundException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetMessagesError.builder()
                        .type("invalidProperties")
                        .properties(MessageProperty.mailboxIds)
                        .attachmentsNotFound(e.getAttachmentIds())
                        .description("Attachment not found")
                        .build());
            
        } catch (MailboxNotImplementedException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type("invalidProperties")
                        .properties(MessageProperty.mailboxIds)
                        .description("Not yet implemented")
                        .build());

        } catch (MailboxInvalidMessageCreationException e) {
            responseBuilder.notCreated(create.getCreationId(),
                    buildSetErrorFromValidationResult(create.getValue().validate()));

        } catch (MailboxNotFoundException e) {
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type("error")
                        .description(e.getMessage())
                        .build());

        } catch (MailboxRightsException e) {
            LOG.error("Appending message in an unknown mailbox", e);
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type("error")
                        .description("MailboxId invalid")
                        .build());

        } catch (MailboxException | MessagingException e) {
            LOG.error("Unexpected error while creating message", e);
            responseBuilder.notCreated(create.getCreationId(), 
                    SetError.builder()
                        .type("error")
                        .description("unexpected error")
                        .build());
        }
    }
    
    private void validateImplementedFeature(CreationMessageEntry entry, MailboxSession session) throws MailboxException, MailboxNotImplementedException {
        if (isAppendToMailboxWithRole(Role.DRAFTS, entry.getValue(), session)) {
            throw new MailboxNotImplementedException("Drafts saving is not implemented");
        }
        if (!isAppendToMailboxWithRole(Role.OUTBOX, entry.getValue(), session)) {
            throw new MailboxNotImplementedException("The only implemented feature is sending via outbox");
        }
    }
    
    private void validateArguments(CreationMessageEntry entry, MailboxSession session) throws MailboxInvalidMessageCreationException, AttachmentsNotFoundException, MailboxException {
        CreationMessage message = entry.getValue();
        if (!message.isValid()) {
            throw new MailboxInvalidMessageCreationException();
        }
        assertAttachmentsExist(entry, session);
    }
    
    @VisibleForTesting void assertAttachmentsExist(CreationMessageEntry entry, MailboxSession session) throws AttachmentsNotFoundException, MailboxException {
        List<Attachment> attachments = entry.getValue().getAttachments();
        if (!attachments.isEmpty()) {
            List<BlobId> notFounds = listAttachmentsNotFound(attachments, session);
            if (!notFounds.isEmpty()) {
                throw new AttachmentsNotFoundException(notFounds);
            }
        }
    }

    private List<BlobId> listAttachmentsNotFound(List<Attachment> attachments, MailboxSession session) throws MailboxException {
        ThrowingPredicate<Attachment> notExists = attachment -> {
            try {
                attachmentManager.getAttachment(getAttachmentId(attachment), session);
                return false;
            } catch (AttachmentNotFoundException e) {
                return true;
            }
        };
        return attachments.stream()
            .filter(Throwing.predicate(notExists).sneakyThrow())
            .map(Attachment::getBlobId)
            .collect(Guavate.toImmutableList());
    }

    private AttachmentId getAttachmentId(Attachment attachment) {
        return AttachmentId.from(attachment.getBlobId().getRawValue());
    }

    @VisibleForTesting void validateIsUserOwnerOfMailboxes(CreationMessageEntry entry, MailboxSession session) throws MailboxRightsException {
        if (containsMailboxNotOwn(entry.getValue().getMailboxIds(), session)) {
            throw new MailboxRightsException();
        }
    }

    private boolean containsMailboxNotOwn(List<String> mailboxIds, MailboxSession session) {
        return mailboxIds.stream()
            .map(mailboxIdFactory::fromString)
            .map(Throwing.function(mailboxId -> mailboxManager.getMailbox(mailboxId, session)))
            .map(Throwing.function(MessageManager::getMailboxPath))
            .anyMatch(path -> !session.getUser().isSameUser(path.getUser()));
    }

    private MessageWithId handleOutboxMessages(CreationMessageEntry entry, MailboxSession session) throws MailboxException, MessagingException {
        MessageManager outbox = getMailboxWithRole(session, Role.OUTBOX).orElseThrow(() -> new MailboxNotFoundException(Role.OUTBOX.serialize()));
        if (!isRequestForSending(entry.getValue(), session)) {
            throw new IllegalStateException("Messages for everything but outbox should have been filtered earlier");
        }
        MetaDataWithContent newMessage = messageAppender.createMessageInMailbox(entry, outbox, session);
        Message jmapMessage = messageFactory.fromMetaDataWithContent(newMessage);
        messageSender.sendMessage(jmapMessage, newMessage, session);
        return new ValueWithId.MessageWithId(entry.getCreationId(), jmapMessage);
    }
    
    private boolean isAppendToMailboxWithRole(Role role, CreationMessage entry, MailboxSession mailboxSession) throws MailboxException {
        return getMailboxWithRole(mailboxSession, role)
                .map(entry::isInOnly)
                .orElse(false);
    }

    private Optional<MessageManager> getMailboxWithRole(MailboxSession mailboxSession, Role role) throws MailboxException {
        return systemMailboxesProvider.getMailboxByRole(role, mailboxSession).findFirst();
    }
    
    private SetError buildSetErrorFromValidationResult(List<ValidationResult> validationErrors) {
        return SetError.builder()
                .type("invalidProperties")
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

    private boolean isRequestForSending(CreationMessage creationMessage, MailboxSession session) throws MailboxException {
        return isAppendToMailboxWithRole(Role.OUTBOX, creationMessage, session);
    }

}
