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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import jakarta.mail.Flags;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.draft.exceptions.AttachmentsNotFoundException;
import org.apache.james.jmap.draft.exceptions.SizeExceededException;
import org.apache.james.jmap.draft.methods.ValueWithId.CreationMessageEntry;
import org.apache.james.jmap.draft.model.Attachment;
import org.apache.james.jmap.draft.model.Blob;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.Keywords;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory.MetaDataWithContent;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendResult;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final MIMEMessageConverter mimeMessageConverter;
    private final BlobManager blobManager;
    private final JMAPConfiguration configuration;

    @Inject
    public MessageAppender(MailboxManager mailboxManager, MessageIdManager messageIdManager, MIMEMessageConverter mimeMessageConverter, BlobManager blobManager, JMAPConfiguration configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.mimeMessageConverter = mimeMessageConverter;
        this.blobManager = blobManager;
        this.configuration = configuration;
    }

    public Mono<MetaDataWithContent> appendMessageInMailboxes(CreationMessageEntry createdEntry, List<MailboxId> targetMailboxes, MailboxSession session) {
        return Mono.fromCallable(() -> {
                Preconditions.checkArgument(!targetMailboxes.isEmpty());
                ImmutableList<Attachment.WithBlob> attachmentsWithBlobs = getMessageAttachments(session, createdEntry.getValue().getAttachments());
                Message message = mimeMessageConverter.convertToMime(createdEntry, attachmentsWithBlobs);

                byte[] messageContent = mimeMessageConverter.asBytes(message);

                if (maximumSizeExceeded(messageContent)) {
                    throw new SizeExceededException(messageContent.length, configuration.getMaximumSendSize().get());
                }

                Date internalDate = Date.from(createdEntry.getValue().getDate().toInstant());

                return new NewMessage(messageContent, message, internalDate);
            }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
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
                    .doFinally(any -> newMessage.message.dispose())
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

    private ImmutableList<Attachment.WithBlob> getMessageAttachments(MailboxSession session, ImmutableList<Attachment> attachments) {
        ImmutableMap<BlobId, Blob> blobs = Flux.from(blobManager.retrieve(attachments.stream()
                .map(Attachment::getBlobId)
                .collect(ImmutableList.toImmutableList()),
            session))
            .collect(ImmutableMap.toImmutableMap(Blob::getBlobId, Function.identity()))
            .block();

        ImmutableList<Attachment.WithBlob> result = attachments
            .stream()
            .flatMap(attachment -> Optional.ofNullable(blobs.get(attachment.getBlobId()))
                .map(blob -> new Attachment.WithBlob(attachment, blob))
                .stream())
            .collect(ImmutableList.toImmutableList());

        if (result.size() != attachments.size()) {
            Sets.SetView<BlobId> notFound = Sets.difference(attachments.stream().map(Attachment::getBlobId).collect(Collectors.toSet()),
                result.stream().map(att -> att.getAttachment().getBlobId()).collect(Collectors.toSet()));
            throw new AttachmentsNotFoundException(ImmutableList.copyOf(notFound));
        }
        return result;
    }
}
