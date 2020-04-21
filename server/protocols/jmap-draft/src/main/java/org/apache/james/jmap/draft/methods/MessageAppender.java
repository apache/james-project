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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

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
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class MessageAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageAppender.class);

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final AttachmentManager attachmentManager;
    private final MIMEMessageConverter mimeMessageConverter;

    @Inject
    public MessageAppender(MailboxManager mailboxManager, MessageIdManager messageIdManager, AttachmentManager attachmentManager, MIMEMessageConverter mimeMessageConverter) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.attachmentManager = attachmentManager;
        this.mimeMessageConverter = mimeMessageConverter;
    }

    public MetaDataWithContent appendMessageInMailboxes(CreationMessageEntry createdEntry,
                                                        List<MailboxId> targetMailboxes,
                                                        MailboxSession session) throws MailboxException {
        Preconditions.checkArgument(!targetMailboxes.isEmpty());
        ImmutableList<MessageAttachmentMetadata> messageAttachments = getMessageAttachments(session, createdEntry.getValue().getAttachments());
        byte[] messageContent = mimeMessageConverter.convert(createdEntry, messageAttachments, session);
        SharedByteArrayInputStream content = new SharedByteArrayInputStream(messageContent);
        Date internalDate = Date.from(createdEntry.getValue().getDate().toInstant());

        MessageManager mailbox = mailboxManager.getMailbox(targetMailboxes.get(0), session);
        AppendResult appendResult = mailbox.appendMessage(
            MessageManager.AppendCommand.builder()
                .withInternalDate(internalDate)
                .withFlags(getFlags(createdEntry.getValue()))
                .notRecent()
                .build(content),
            session);
        ComposedMessageId ids = appendResult.getId();
        if (targetMailboxes.size() > 1) {
            messageIdManager.setInMailboxes(ids.getMessageId(), targetMailboxes, session);
        }

        return MetaDataWithContent.builder()
            .uid(ids.getUid())
            .keywords(createdEntry.getValue().getKeywords())
            .internalDate(internalDate.toInstant())
            .sharedContent(content)
            .size(messageContent.length)
            .attachments(appendResult.getMessageAttachments())
            .mailboxId(mailbox.getId())
            .messageId(ids.getMessageId())
            .build();
    }

    public MetaDataWithContent appendMessageInMailbox(org.apache.james.mime4j.dom.Message message,
                                                      MessageManager messageManager,
                                                      Flags flags,
                                                      MailboxSession session) throws MailboxException {


        byte[] messageContent = asBytes(message);
        SharedByteArrayInputStream content = new SharedByteArrayInputStream(messageContent);
        Date internalDate = new Date();

        AppendResult appendResult = messageManager.appendMessage(MessageManager.AppendCommand.builder()
            .withFlags(flags)
            .build(content), session);
        ComposedMessageId ids = appendResult.getId();

        return MetaDataWithContent.builder()
            .uid(ids.getUid())
            .keywords(Keywords.lenientFactory().fromFlags(flags))
            .internalDate(internalDate.toInstant())
            .sharedContent(content)
            .size(messageContent.length)
            .attachments(appendResult.getMessageAttachments())
            .mailboxId(messageManager.getId())
            .messageId(ids.getMessageId())
            .build();
    }

    public byte[] asBytes(Message message) throws MailboxException {
        try {
            return DefaultMessageWriter.asBytes(message);
        } catch (IOException e) {
            throw new MailboxException("Could not write message as bytes", e);
        }
    }

    private Flags getFlags(CreationMessage message) {
        return message.getKeywords().asFlags();
    }

    private ImmutableList<MessageAttachmentMetadata> getMessageAttachments(MailboxSession session, ImmutableList<Attachment> attachments) throws MailboxException {
        ThrowingFunction<Attachment, Optional<MessageAttachmentMetadata>> toMessageAttachment = att -> messageAttachment(session, att);
        return attachments.stream()
            .map(Throwing.function(toMessageAttachment).sneakyThrow())
            .flatMap(Optional::stream)
            .collect(Guavate.toImmutableList());
    }

    private Optional<MessageAttachmentMetadata> messageAttachment(MailboxSession session, Attachment attachment) throws MailboxException {
        try {
            return Optional.of(MessageAttachmentMetadata.builder()
                .attachment(attachmentManager.getAttachment(AttachmentId.from(attachment.getBlobId().getRawValue()), session))
                .name(attachment.getName().orElse(null))
                .cid(attachment.getCid().map(Cid::from).orElse(null))
                .isInline(attachment.isIsInline())
                .build());
        } catch (AttachmentNotFoundException e) {
            LOGGER.error(String.format("Attachment %s not found", attachment.getBlobId()), e);
            return Optional.empty();
        } catch (IllegalStateException e) {
            LOGGER.error(String.format("Attachment %s is not well-formed", attachment.getBlobId()), e);
            return Optional.empty();
        }
    }
}
