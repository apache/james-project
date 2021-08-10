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

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.api.model.UploadNotFoundException;
import org.apache.james.jmap.api.upload.UploadRepository;
import org.apache.james.jmap.draft.exceptions.BlobNotFoundException;
import org.apache.james.jmap.draft.model.Blob;
import org.apache.james.jmap.draft.model.BlobId;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

public class BlobManagerImpl implements BlobManager {
    public static final ContentType MESSAGE_RFC822_CONTENT_TYPE = ContentType.of("message/rfc822");
    public static final String UPLOAD_PREFIX = "upload-";

    private final AttachmentManager attachmentManager;
    private final MessageIdManager messageIdManager;
    private final MessageId.Factory messageIdFactory;
    private final UploadRepository uploadRepository;

    @Inject
    public BlobManagerImpl(AttachmentManager attachmentManager, MessageIdManager messageIdManager,
                           MessageId.Factory messageIdFactory, UploadRepository uploadRepository) {
        this.attachmentManager = attachmentManager;
        this.messageIdManager = messageIdManager;
        this.messageIdFactory = messageIdFactory;
        this.uploadRepository = uploadRepository;
    }

    @Override
    public Blob retrieve(BlobId blobId, MailboxSession mailboxSession) throws MailboxException, BlobNotFoundException {
        return getBlobFromUpload(blobId, mailboxSession)
            .or(Throwing.supplier(() -> getBlobFromAttachment(blobId, mailboxSession)).sneakyThrow())
            .orElseGet(() -> getBlobFromMessage(blobId, mailboxSession)
                .orElseThrow(() -> new BlobNotFoundException(blobId)));
    }

    private Optional<Blob> getBlobFromUpload(BlobId blobId, MailboxSession mailboxSession) {
        return blobId.asUploadId()
            .flatMap(uploadId -> Mono.from(uploadRepository.retrieve(uploadId, mailboxSession.getUser()))
                .map(upload -> Blob.builder()
                    .id(blobId)
                    .contentType(upload.contentType())
                    .size(upload.sizeAsLong())
                    .payload(upload.content()::apply)
                    .build())
                .onErrorResume(UploadNotFoundException.class, e -> Mono.empty())
                .blockOptional());
    }

    private Optional<Blob> getBlobFromAttachment(BlobId blobId, MailboxSession mailboxSession) throws MailboxException {
        try {
            AttachmentId attachmentId = blobId.asAttachmentId();
            AttachmentMetadata attachment = attachmentManager.getAttachment(attachmentId, mailboxSession);

            Blob blob = Blob.builder()
                .id(blobId)
                .payload(() -> {
                    try {
                        return attachmentManager.loadAttachmentContent(attachmentId, mailboxSession);
                    } catch (AttachmentNotFoundException e) {
                        throw new BlobNotFoundException(blobId, e);
                    }
                })
                .size(attachment.getSize())
                .contentType(attachment.getType())
                .build();
            return Optional.of(blob);
        } catch (AttachmentNotFoundException e) {
            return Optional.empty();
        }
    }

    private Optional<Blob> getBlobFromMessage(BlobId blobId, MailboxSession mailboxSession) {
        return retrieveMessageId(blobId)
                .flatMap(messageId -> loadMessageAsInputStream(messageId, mailboxSession))
                .map(Throwing.function(
                    content -> Blob.builder()
                        .id(blobId)
                        .contentType(MESSAGE_RFC822_CONTENT_TYPE)
                        .size(content.size())
                        .payload(content::getInputStream)
                        .build()));
    }

    private Optional<MessageId> retrieveMessageId(BlobId blobId) {
        try {
            return Optional.of(messageIdFactory.fromString(blobId.getRawValue()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Content> loadMessageAsInputStream(MessageId messageId, MailboxSession mailboxSession)  {
        try {
            return messageIdManager.getMessage(messageId, FetchGroup.FULL_CONTENT, mailboxSession)
                .stream()
                .map(Throwing.function(MessageResult::getFullContent))
                .findFirst();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

}
