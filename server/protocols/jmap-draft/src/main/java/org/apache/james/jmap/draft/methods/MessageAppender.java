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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.draft.exceptions.SizeExceededException;
import org.apache.james.jmap.draft.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory.MetaDataWithContent;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MessageAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageAppender.class);

    private static class NewMessage {
        private final byte[] messageContent;
        private final Message message;
        private final Date date;

        private NewMessage(byte[] messageContent, Message message, Date date) {
            this.messageContent = messageContent;
            this.message = message;
            this.date = date;
        }
    }

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final AttachmentManager attachmentManager;
    private final MIMEMessageConverter mimeMessageConverter;
    private JMAPConfiguration configuration;

    @Inject
    public MessageAppender(MailboxManager mailboxManager, MessageIdManager messageIdManager, AttachmentManager attachmentManager, MIMEMessageConverter mimeMessageConverter, JMAPConfiguration configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.attachmentManager = attachmentManager;
        this.mimeMessageConverter = mimeMessageConverter;
        this.configuration = configuration;
    }

    public Mono<MetaDataWithContent> appendMessageInMailboxes(CreationMessageEntry createdEntry, List<MailboxId> targetMailboxes, MailboxSession session) {
        return Mono.fromCallable(() -> {
                Preconditions.checkArgument(!targetMailboxes.isEmpty());
                ImmutableList<MessageAttachmentMetadata> messageAttachments = getMessageAttachments(session, createdEntry.getValue().getAttachments());
                Message message = mimeMessageConverter.convertToMime(createdEntry, messageAttachments, session);

                byte[] messageContent = mimeMessageConverter.asBytes(message);

                if (maximumSizeExceeded(messageContent)) {
                    throw new SizeExceededException(messageContent.length, configuration.getMaximumSendSize().get());
                }

                Date internalDate = Date.from(createdEntry.getValue().getDate().toInstant());

                return new NewMessage(messageContent, message, internalDate);
            }).subscribeOn(Schedulers.elastic())
            .flatMap(newMessage -> Mono.from(mailboxManager.getMailboxReactive(targetMailboxes.get(0), session))
                .flatMap(mailbox -> Mono.from(mailbox.appendMessageReactive(
                    MessageManager.AppendCommand.builder()
                        .withInternalDate(newMessage.date)
                        .withFlags(getFlags(createdEntry.getValue()))
                        .notRecent()
                        .withParsedMessage(newMessage.message)
                        .build(new ByteContent(newMessage.messageContent)),
                    session))
                    .flatMap(appendResult -> {
                        ComposedMessageId ids = appendResult.getId();
                        if (targetMailboxes.size() > 1) {
                            return Mono.from(messageIdManager.setInMailboxesReactive(ids.getMessageId(), targetMailboxes, session))
                                .thenReturn(appendResult);
                        }
                        return Mono.just(appendResult);
                    })
                    .map(appendResult -> MetaDataWithContent.builder()
                        .uid(appendResult.getId().getUid())
                        .keywords(createdEntry.getValue().getKeywords())
                        .internalDate(newMessage.date.toInstant())
                        .sharedContent(new SharedByteArrayInputStream(newMessage.messageContent))
                        .size(newMessage.messageContent.length)
                        .attachments(appendResult.getMessageAttachments())
                        .mailboxId(mailbox.getId())
                        .message(newMessage.message)
                        .messageId(appendResult.getId().getMessageId())
                        .build())));
    }

    private Boolean maximumSizeExceeded(byte[] messageContent) {
        return configuration.getMaximumSendSize().map(limit -> messageContent.length > limit).orElse(false);
    }

    public MetaDataWithContent appendMessageInMailbox(org.apache.james.mime4j.dom.Message message,
                                                      MessageManager messageManager,
                                                      Flags flags,
                                                      MailboxSession session) throws MailboxException {


        byte[] messageContent = mimeMessageConverter.asBytes(message);
        Date internalDate = new Date();

        AppendResult appendResult = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(flags)
            .build(new ByteContent(messageContent)), session);
        ComposedMessageId ids = appendResult.getId();

        return MetaDataWithContent.builder()
            .uid(ids.getUid())
            .keywords(Keywords.lenientFactory().fromFlags(flags))
            .internalDate(internalDate.toInstant())
            .sharedContent(new SharedByteArrayInputStream(messageContent))
            .size(messageContent.length)
            .attachments(appendResult.getMessageAttachments())
            .mailboxId(messageManager.getId())
            .message(message)
            .messageId(ids.getMessageId())
            .build();
    }

    private Flags getFlags(CreationMessage message) {
        return message.getKeywords().asFlags();
    }

    private ImmutableList<MessageAttachmentMetadata> getMessageAttachments(MailboxSession session, ImmutableList<Attachment> attachments) throws MailboxException {
        Map<AttachmentId, AttachmentMetadata> attachmentsById = attachmentManager.getAttachments(attachments.stream()
            .map(attachment -> AttachmentId.from(attachment.getBlobId().getRawValue()))
            .collect(Guavate.toImmutableList()), session)
            .stream()
            .collect(Guavate.toImmutableMap(AttachmentMetadata::getAttachmentId));

        ThrowingFunction<Attachment, Optional<MessageAttachmentMetadata>> toMessageAttachment = att -> messageAttachment(att, attachmentsById);


        return attachments.stream()
            .map(Throwing.function(toMessageAttachment).sneakyThrow())
            .flatMap(Optional::stream)
            .collect(Guavate.toImmutableList());
    }

    private Optional<MessageAttachmentMetadata> messageAttachment(Attachment attachment, Map<AttachmentId, AttachmentMetadata> attachmentsById) throws MailboxException {
        try {
            AttachmentId attachmentId = AttachmentId.from(attachment.getBlobId().getRawValue());
            return OptionalUtils.executeIfEmpty(Optional.ofNullable(attachmentsById.get(attachmentId))
                .map(attachmentMetadata -> MessageAttachmentMetadata.builder()
                    .attachment(attachmentMetadata)
                .name(attachment.getName().orElse(null))
                .cid(attachment.getCid().map(Cid::from).orElse(null))
                .isInline(attachment.isIsInline())
                .build()),
                () -> LOGGER.error(String.format("Attachment %s not found", attachment.getBlobId())));
        } catch (IllegalStateException e) {
            LOGGER.error(String.format("Attachment %s is not well-formed", attachment.getBlobId()), e);
            return Optional.empty();
        }
    }
}
