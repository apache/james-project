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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.model.CreationMessage;
import org.apache.james.jmap.model.CreationMessageId;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessageProperties;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.jmap.model.SetMessagesResponse.Builder;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.send.MailFactory;
import org.apache.james.jmap.send.MailMetadata;
import org.apache.james.jmap.send.MailSpool;
import org.apache.james.jmap.utils.SystemMailboxesProvider;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.MessageAttachment;
import org.apache.james.mailbox.store.mail.model.impl.Cid;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.util.streams.ImmutableCollectors;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class SetMessagesCreationProcessor implements SetMessagesProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SetMailboxesCreationProcessor.class);
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final MIMEMessageConverter mimeMessageConverter;
    private final MailSpool mailSpool;
    private final MailFactory mailFactory;
    private final MessageFactory messageFactory;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private AttachmentMapperFactory attachmentMapperFactory;

    
    @VisibleForTesting @Inject
    SetMessagesCreationProcessor(MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                 MIMEMessageConverter mimeMessageConverter,
                                 MailSpool mailSpool,
                                 MailFactory mailFactory,
                                 MessageFactory messageFactory,
                                 SystemMailboxesProvider systemMailboxesProvider,
                                 AttachmentMapperFactory attachmentMapperFactory) {
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.mimeMessageConverter = mimeMessageConverter;
        this.mailSpool = mailSpool;
        this.mailFactory = mailFactory;
        this.messageFactory = messageFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.attachmentMapperFactory = attachmentMapperFactory;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        Builder responseBuilder = SetMessagesResponse.builder();
        request.getCreate()
            .stream()
            .forEach(create -> handleCreate(create, responseBuilder, mailboxSession));
        return responseBuilder.build();
    }

    private void handleCreate(CreationMessageEntry create, Builder responseBuilder, MailboxSession mailboxSession) {
        try {
            validateImplementedFeature(create, mailboxSession);
            validateArguments(create, mailboxSession);
            validateRights(create, mailboxSession);
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
                        .description(e.getMailboxName() + " can't be found")
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
    
    private void validateImplementedFeature(CreationMessageEntry entry, MailboxSession session) throws MailboxNotImplementedException {
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
            AttachmentMapper attachmentMapper = attachmentMapperFactory.getAttachmentMapper(session);
            List<BlobId> notFounds = listAttachmentsNotFound(attachments, attachmentMapper);
            if (!notFounds.isEmpty()) {
                throw new AttachmentsNotFoundException(notFounds);
            }
        }
    }

    private List<BlobId> listAttachmentsNotFound(List<Attachment> attachments, AttachmentMapper attachmentMapper) {
        return attachments.stream()
            .flatMap(attachment -> {
                try {
                    attachmentMapper.getAttachment(getAttachmentId(attachment));
                    return Stream.of();
                } catch (AttachmentNotFoundException e) {
                    return Stream.of(attachment.getBlobId());
                }
            }).collect(ImmutableCollectors.toImmutableList());
    }

    private AttachmentId getAttachmentId(Attachment attachment) {
        return AttachmentId.from(attachment.getBlobId().getRawValue());
    }

    private void validateRights(CreationMessageEntry entry, MailboxSession session) throws MailboxSendingNotAllowedException {
        List<String> allowedSenders = ImmutableList.of(session.getUser().getUserName());
        if (!isAllowedFromAddress(entry.getValue(), allowedSenders)) {
            throw new MailboxSendingNotAllowedException(allowedSenders);
        }
    }
    
    private boolean isAllowedFromAddress(CreationMessage creationMessage, List<String> allowedFromMailAddresses) {
        return creationMessage.getFrom()
                .map(draftEmailer -> draftEmailer.getEmail()
                        .map(allowedFromMailAddresses::contains)
                        .orElse(false))
                .orElse(false);
    }

    
    private MessageWithId handleOutboxMessages(CreationMessageEntry entry, MailboxSession session) throws MailboxException, MessagingException {
        Mailbox outbox = getMailboxWithRole(session, Role.OUTBOX).orElseThrow(() -> new MailboxNotFoundException(Role.OUTBOX.serialize()));
        if (!isRequestForSending(entry.getValue(), session)) {
            throw new IllegalStateException("Messages for everything but outbox should have been filtered earlier");
        }
        Function<Long, MessageId> idGenerator = uid -> generateMessageId(session, outbox, uid);
        return createMessageInOutboxAndSend(entry, session, outbox, idGenerator);
    }
    
    @VisibleForTesting
    protected MessageWithId createMessageInOutboxAndSend(CreationMessageEntry createdEntry,
                                                           MailboxSession session,
                                                           Mailbox outbox, Function<Long, MessageId> buildMessageIdFromUid) throws MailboxException, MessagingException {
        
        CreationMessageId creationId = createdEntry.getCreationId();
        MessageMapper messageMapper = mailboxSessionMapperFactory.createMessageMapper(session);
        MailboxMessage newMailboxMessage = buildMailboxMessage(session, createdEntry, outbox);
        messageMapper.add(outbox, newMailboxMessage);
        Message jmapMessage = messageFactory.fromMailboxMessage(newMailboxMessage, newMailboxMessage.getAttachments(), buildMessageIdFromUid);
        sendMessage(newMailboxMessage, jmapMessage, session);
        return new MessageWithId(creationId, jmapMessage);
    }
    
    private boolean isAppendToMailboxWithRole(Role role, CreationMessage entry, MailboxSession mailboxSession) {
        return getMailboxWithRole(mailboxSession, role)
                .map(box -> entry.isIn(box))
                .orElse(false);
    }

    private Optional<Mailbox> getMailboxWithRole(MailboxSession mailboxSession, Role role) {
        return systemMailboxesProvider.listMailboxes(role, mailboxSession).findFirst();
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

    private boolean isRequestForSending(CreationMessage creationMessage, MailboxSession session) {
        return isAppendToMailboxWithRole(Role.OUTBOX, creationMessage, session);
    }
    
    private MessageId generateMessageId(MailboxSession session, Mailbox outbox, long uid) {
        MailboxPath outboxPath = new MailboxPath(session.getPersonalSpace(), session.getUser().getUserName(), outbox.getName());
        return new MessageId(session.getUser(), outboxPath, uid);
    }

    private MailboxMessage buildMailboxMessage(MailboxSession session, MessageWithId.CreationMessageEntry createdEntry, Mailbox outbox) throws MailboxException {
        ImmutableList<MessageAttachment> messageAttachments = getMessageAttachments(session, createdEntry.getValue().getAttachments());
        byte[] messageContent = mimeMessageConverter.convert(createdEntry, messageAttachments);
        SharedInputStream content = new SharedByteArrayInputStream(messageContent);
        long size = messageContent.length;
        int bodyStartOctet = 0;

        Flags flags = getMessageFlags(createdEntry.getValue());
        PropertyBuilder propertyBuilder = buildPropertyBuilder();
        MailboxId mailboxId = outbox.getMailboxId();
        Date internalDate = Date.from(createdEntry.getValue().getDate().toInstant());

        return new SimpleMailboxMessage(internalDate, size,
                bodyStartOctet, content, flags, propertyBuilder, mailboxId, messageAttachments);
    }

    private ImmutableList<MessageAttachment> getMessageAttachments(MailboxSession session, ImmutableList<Attachment> attachments) throws MailboxException {
        AttachmentMapper attachmentMapper = attachmentMapperFactory.getAttachmentMapper(session);
        return attachments.stream()
            .map(att -> messageAttachment(attachmentMapper, att))
            .collect(ImmutableCollectors.toImmutableList());
    }

    private MessageAttachment messageAttachment(AttachmentMapper attachmentMapper, Attachment attachment) {
        try {
            return MessageAttachment.builder()
                    .attachment(attachmentMapper.getAttachment(AttachmentId.from(attachment.getBlobId().getRawValue())))
                    .name(attachment.getName().orElse(null))
                    .cid(attachment.getCid().map(Cid::from).orElse(null))
                    .isInline(attachment.isIsInline())
                    .build();
        } catch (AttachmentNotFoundException e) {
            // should not happen (checked before)
            LOG.error(String.format("Attachment %s not found", attachment.getBlobId()), e);
            return null;
        }
    }

    private PropertyBuilder buildPropertyBuilder() {
        return new PropertyBuilder();
    }

    private Flags getMessageFlags(CreationMessage message) {
        Flags result = new Flags();
        if (!message.isIsUnread()) {
            result.add(Flags.Flag.SEEN);
        }
        if (message.isIsFlagged()) {
            result.add(Flags.Flag.FLAGGED);
        }
        if (message.isIsAnswered() || message.getInReplyToMessageId().isPresent()) {
            result.add(Flags.Flag.ANSWERED);
        }
        if (message.isIsDraft()) {
            result.add(Flags.Flag.DRAFT);
        }
        return result;
    }

    private void sendMessage(MailboxMessage mailboxMessage, Message jmapMessage, MailboxSession session) throws MessagingException {
        Mail mail = buildMessage(mailboxMessage, jmapMessage);
        try {
            MailMetadata metadata = new MailMetadata(jmapMessage.getId(), session.getUser().getUserName());
            mailSpool.send(mail, metadata);
        } finally {
            LifecycleUtil.dispose(mail);
        }
    }

    private Mail buildMessage(MailboxMessage mailboxMessage, Message jmapMessage) throws MessagingException {
        try {
            return mailFactory.build(mailboxMessage, jmapMessage);
        } catch (IOException e) {
            throw new MessagingException("error building message to send", e);
        }
    }
}
