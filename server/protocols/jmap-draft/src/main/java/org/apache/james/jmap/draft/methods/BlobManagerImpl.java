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
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jakarta.inject.Inject;

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
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
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
    public Publisher<Blob> retrieve(Collection<BlobId> blobIds, MailboxSession session) {
        Set<BlobId> supplied = ImmutableSet.copyOf(blobIds);
        Set<BlobId> encodingUploads = blobIds.stream()
            .filter(blobId -> blobId.asUploadId().isPresent())
            .collect(ImmutableSet.toImmutableSet());
        Set<BlobId> notEncodingUploads = Sets.difference(supplied, encodingUploads);

        Flux<Blob> uploads = Flux.fromIterable(encodingUploads)
            .flatMap(blobId -> getBlobFromUpload(blobId, session));


        List<AttachmentId> notEncodingUploadsAsAttachmentIds = notEncodingUploads.stream()
            .map(BlobId::asAttachmentId)
            .collect(ImmutableList.toImmutableList());

        Flux<Blob> attachmentOrMessage = Mono.fromCallable(() -> attachmentManager.getAttachments(notEncodingUploadsAsAttachmentIds, session))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .flatMapIterable(Function.identity())
            .flatMap(attachment -> Mono.fromCallable(() -> loadAttachmentContent(attachment, session))
                .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
            .collect(ImmutableList.toImmutableList())
            .flatMapMany(attachmentsBlobs -> {
                Set<BlobId> attachmentBlobIds = attachmentsBlobs
                    .stream()
                    .map(Blob::getBlobId)
                    .collect(ImmutableSet.toImmutableSet());
                Set<BlobId> messageBlobIds = Sets.difference(notEncodingUploads, attachmentBlobIds);

                return Flux.merge(Flux.fromIterable(attachmentsBlobs),
                    Flux.fromIterable(messageBlobIds)
                        .flatMap(blobId -> getBlobFromMessage(blobId, session)));
            });

        return Flux.merge(uploads, attachmentOrMessage);
    }

    @Override
    public Blob retrieve(BlobId blobId, MailboxSession mailboxSession) throws MailboxException, BlobNotFoundException {
        try {
            return getBlobFromUpload(blobId, mailboxSession)
                .switchIfEmpty(Mono.fromCallable(() -> getBlobFromAttachment(blobId, mailboxSession))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .handle(ReactorUtils.publishIfPresent()))
                .switchIfEmpty(getBlobFromMessage(blobId, mailboxSession))
                .switchIfEmpty(Mono.error(() -> new BlobNotFoundException(blobId)))
                .block();
        } catch (Exception e) {
            if (e.getCause() instanceof MailboxException) {
                throw (MailboxException) e.getCause();
            }
            throw e;
        }
    }

    private Mono<Blob> getBlobFromUpload(BlobId blobId, MailboxSession mailboxSession) {
        return blobId.asUploadId()
            .map(uploadId -> Mono.from(uploadRepository.retrieve(uploadId, mailboxSession.getUser()))
                .map(upload -> Blob.builder()
                    .id(blobId)
                    .contentType(upload.contentType())
                    .size(upload.sizeAsLong())
                    .payload(upload.content()::apply)
                    .build())
                .onErrorResume(UploadNotFoundException.class, e -> Mono.empty()))
            .orElse(Mono.empty());
    }

    private Optional<Blob> getBlobFromAttachment(BlobId blobId, MailboxSession mailboxSession) throws MailboxException {
        try {
            AttachmentId attachmentId = blobId.asAttachmentId();
            AttachmentMetadata attachment = attachmentManager.getAttachment(attachmentId, mailboxSession);

            return Optional.of(loadAttachmentContent(attachment, mailboxSession));
        } catch (AttachmentNotFoundException e) {
            return Optional.empty();
        }
    }

    private Blob loadAttachmentContent(AttachmentMetadata attachment, MailboxSession mailboxSession) {
        BlobId blobId = BlobId.of(attachment.getAttachmentId());
        return Blob.builder()
            .id(blobId)
            .payload(new Blob.InputStreamSupplier() {
                @Override
                public InputStream load() throws IOException, BlobNotFoundException {
                    try {
                        return loadReactive().block();
                    } catch (RuntimeException e) {
                        if (e.getCause() instanceof IOException) {
                            throw (IOException) e.getCause();
                        }
                        if (e.getCause() instanceof BlobNotFoundException) {
                            throw (BlobNotFoundException) e.getCause();
                        }
                        throw e;
                    }
                }

                @Override
                public Mono<InputStream> loadReactive() {
                    return attachmentManager.loadAttachmentContentReactive(attachment.getAttachmentId(), mailboxSession)
                        .onErrorResume(AttachmentNotFoundException.class, e -> Mono.error(new BlobNotFoundException(blobId, e)));
                }
            })
            .size(attachment.getSize())
            .contentType(attachment.getType())
            .build();
    }

    private Mono<Blob> getBlobFromMessage(BlobId blobId, MailboxSession mailboxSession) {
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

    private Mono<MessageId> retrieveMessageId(BlobId blobId) {
        try {
            return Mono.just(messageIdFactory.fromString(blobId.getRawValue()));
        } catch (IllegalArgumentException e) {
            return Mono.empty();
        }
    }

    private Mono<Content> loadMessageAsInputStream(MessageId messageId, MailboxSession mailboxSession)  {
            return Flux.from(messageIdManager.getMessagesReactive(ImmutableSet.of(messageId), FetchGroup.FULL_CONTENT, mailboxSession))
                .map(Throwing.function(MessageResult::getFullContent))
                .next();
    }
}
