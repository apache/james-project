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

package org.apache.james.mailbox.postgres.mail;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.postgres.mail.dao.PostgresAttachmentDAO;
import org.apache.james.mailbox.store.mail.AttachmentMapper;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresAttachmentMapper implements AttachmentMapper {

    private final PostgresAttachmentDAO postgresAttachmentDAO;
    private final BlobStore blobStore;

    public PostgresAttachmentMapper(PostgresAttachmentDAO postgresAttachmentDAO, BlobStore blobStore) {
        this.postgresAttachmentDAO = postgresAttachmentDAO;
        this.blobStore = blobStore;
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId) {
        return loadAttachmentContentReactive(attachmentId)
            .block();
    }

    @Override
    public Mono<InputStream> loadAttachmentContentReactive(AttachmentId attachmentId) {
        return postgresAttachmentDAO.getAttachment(attachmentId)
            .flatMap(pair -> Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), pair.getRight(), LOW_COST)))
            .switchIfEmpty(Mono.error(() -> new AttachmentNotFoundException(attachmentId.toString())));
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return postgresAttachmentDAO.getAttachment(attachmentId)
            .map(Pair::getLeft)
            .blockOptional()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    @Override
    public Mono<AttachmentMetadata> getAttachmentReactive(AttachmentId attachmentId) {
        Preconditions.checkArgument(attachmentId != null);
        return postgresAttachmentDAO.getAttachment(attachmentId)
            .map(Pair::getLeft)
            .switchIfEmpty(Mono.error(() -> new AttachmentNotFoundException(attachmentId.getId())));
    }

    public Flux<AttachmentMetadata> getAttachmentsReactive(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        return postgresAttachmentDAO.getAttachments(attachmentIds);
    }

    @Override
    public List<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        return getAttachmentsReactive(attachmentIds)
            .collectList()
            .block();
    }

    @Override
    public List<MessageAttachmentMetadata> storeAttachments(Collection<ParsedAttachment> attachments, MessageId ownerMessageId) {
        return storeAttachmentsReactive(attachments, ownerMessageId)
            .block();
    }

    @Override
    public Mono<List<MessageAttachmentMetadata>> storeAttachmentsReactive(Collection<ParsedAttachment> attachments, MessageId ownerMessageId) {
        return Flux.fromIterable(attachments)
            .concatMap(attachment -> storeAttachmentAsync(attachment, ownerMessageId))
            .collectList();
    }

    private Mono<MessageAttachmentMetadata> storeAttachmentAsync(ParsedAttachment parsedAttachment, MessageId ownerMessageId) {
        return Mono.fromCallable(parsedAttachment::getContent)
            .flatMap(content -> Mono.from(blobStore.save(blobStore.getDefaultBucketName(), parsedAttachment.getContent(), BlobStore.StoragePolicy.LOW_COST))
                .flatMap(blobId -> {
                    AttachmentId attachmentId = AttachmentId.random();
                    return postgresAttachmentDAO.storeAttachment(AttachmentMetadata.builder()
                            .attachmentId(attachmentId)
                            .type(parsedAttachment.getContentType())
                            .size(Throwing.supplier(content::size).get())
                            .messageId(ownerMessageId)
                            .build(), blobId)
                        .thenReturn(Throwing.supplier(() -> parsedAttachment.asMessageAttachment(attachmentId, ownerMessageId)).get());
                }));
    }
}
