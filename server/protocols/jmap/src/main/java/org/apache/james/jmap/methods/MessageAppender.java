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

import java.util.Date;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.Keywords;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class MessageAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageAppender.class);

    private final MailboxManager mailboxManager;
    private final AttachmentManager attachmentManager;
    private final MIMEMessageConverter mimeMessageConverter;

    @Inject
    public MessageAppender(MailboxManager mailboxManager, AttachmentManager attachmentManager, MIMEMessageConverter mimeMessageConverter) {
        this.mailboxManager = mailboxManager;
        this.attachmentManager = attachmentManager;
        this.mimeMessageConverter = mimeMessageConverter;
    }

    public MessageFactory.MetaDataWithContent appendMessageInMailbox(CreationMessageEntry createdEntry,
                                                                     MessageManager mailbox,
                                                                     MailboxSession session) throws MailboxException {
        ImmutableList<MessageAttachment> messageAttachments = getMessageAttachments(session, createdEntry.getValue().getAttachments());
        byte[] messageContent = mimeMessageConverter.convert(createdEntry, messageAttachments);
        SharedByteArrayInputStream content = new SharedByteArrayInputStream(messageContent);
        Date internalDate = Date.from(createdEntry.getValue().getDate().toInstant());

        Keywords keywords = createdEntry.getValue()
            .getKeywords()
            .orElse(Keywords.DEFAULT_VALUE);
        boolean notRecent = false;

        ComposedMessageId message = mailbox.appendMessage(content, internalDate, session, notRecent, keywords.asFlags());

        return MessageFactory.MetaDataWithContent.builder()
            .uid(message.getUid())
            .keywords(keywords)
            .internalDate(internalDate.toInstant())
            .sharedContent(content)
            .size(messageContent.length)
            .attachments(messageAttachments)
            .mailboxId(mailbox.getId())
            .messageId(message.getMessageId())
            .build();
    }

    public MessageFactory.MetaDataWithContent appendMessageInMailbox(CreationMessageEntry createdEntry,
                                                                     MailboxId mailboxId,
                                                                     MailboxSession session) throws MailboxException {
        return appendMessageInMailbox(createdEntry,
            mailboxManager.getMailbox(mailboxId, session),
            session);
    }

    private ImmutableList<MessageAttachment> getMessageAttachments(MailboxSession session, ImmutableList<Attachment> attachments) throws MailboxException {
        ThrowingFunction<Attachment, Optional<MessageAttachment>> toMessageAttachment = att -> messageAttachment(session, att);
        return attachments.stream()
            .map(Throwing.function(toMessageAttachment).sneakyThrow())
            .flatMap(OptionalUtils::toStream)
            .collect(Guavate.toImmutableList());
    }

    private Optional<MessageAttachment> messageAttachment(MailboxSession session, Attachment attachment) throws MailboxException {
        try {
            return Optional.of(MessageAttachment.builder()
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
