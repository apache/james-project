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

package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;
import static org.apache.james.util.ReactorUtils.LOW_CONCURRENCY;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraAttachmentMapper implements AttachmentMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAttachmentMapper.class);

    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final BlobStore blobStore;

    @Inject
    public CassandraAttachmentMapper(CassandraAttachmentDAOV2 attachmentDAOV2, BlobStore blobStore) {
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobStore = blobStore;
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return getAttachmentInternal(attachmentId)
            .blockOptional()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    @Override
    public Mono<AttachmentMetadata> getAttachmentReactive(AttachmentId attachmentId) {
        Preconditions.checkArgument(attachmentId != null);
        return getAttachmentInternal(attachmentId)
            .switchIfEmpty(Mono.error(() -> new AttachmentNotFoundException(attachmentId.getId())));
    }

    @Override
    public List<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        return Flux.fromIterable(attachmentIds)
            .flatMap(this::getAttachmentsAsMono, DEFAULT_CONCURRENCY)
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId) throws AttachmentNotFoundException {
        return attachmentDAOV2.getAttachment(attachmentId)
            .map(daoAttachment -> blobStore.read(blobStore.getDefaultBucketName(), daoAttachment.getBlobId(), LOW_COST))
            .blockOptional()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.toString()));
    }

    @Override
    public Mono<InputStream> loadAttachmentContentReactive(AttachmentId attachmentId) {
        return attachmentDAOV2.getAttachment(attachmentId)
            .flatMap(daoAttachment -> Mono.from(blobStore.readReactive(blobStore.getDefaultBucketName(), daoAttachment.getBlobId(), LOW_COST)))
            .switchIfEmpty(Mono.error(() -> new AttachmentNotFoundException(attachmentId.toString())));
    }


    public Mono<AttachmentMetadata> getAttachmentsAsMono(AttachmentId attachmentId) {
        return getAttachmentInternal(attachmentId)
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> logNotFound((attachmentId))));
    }

    private Mono<AttachmentMetadata> getAttachmentInternal(AttachmentId id) {
        return attachmentDAOV2.getAttachment(id)
            .map(DAOAttachment::toAttachment);
    }

    @Override
    public List<MessageAttachmentMetadata> storeAttachments(Collection<ParsedAttachment> parsedAttachments, MessageId ownerMessageId) {
        return storeAttachmentsReactive(parsedAttachments, ownerMessageId)
            .block();
    }

    @Override
    public Mono<List<MessageAttachmentMetadata>> storeAttachmentsReactive(Collection<ParsedAttachment> attachments, MessageId ownerMessageId) {
        return Flux.fromIterable(attachments)
            .flatMapSequential(attachment -> storeAttachmentAsync(attachment, ownerMessageId), LOW_CONCURRENCY)
            .collectList();
    }

    private Mono<MessageAttachmentMetadata> storeAttachmentAsync(ParsedAttachment parsedAttachment, MessageId ownerMessageId) {
        try {
            AttachmentId attachmentId = AttachmentId.random();
            ByteSource content = parsedAttachment.getContent();
            long size = content.size();
            return Mono.from(blobStore.save(blobStore.getDefaultBucketName(), content, LOW_COST))
                .map(blobId -> new DAOAttachment(ownerMessageId, attachmentId, blobId, parsedAttachment.getContentType(), size))
                .flatMap(this::storeAttachmentWithIndex)
                .thenReturn(parsedAttachment.asMessageAttachment(attachmentId, size, ownerMessageId));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<Void> storeAttachmentWithIndex(DAOAttachment daoAttachment) {
        return attachmentDAOV2.storeAttachment(daoAttachment);
    }

    private void logNotFound(AttachmentId attachmentId) {
        LOGGER.warn("Failed retrieving attachment {}", attachmentId);
    }
}
