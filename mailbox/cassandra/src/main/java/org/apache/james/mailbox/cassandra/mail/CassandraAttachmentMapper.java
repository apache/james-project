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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.io.CurrentPositionInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraAttachmentMapper implements AttachmentMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAttachmentMapper.class);

    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final BlobStore blobStore;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;
    private final CassandraAttachmentOwnerDAO ownerDAO;

    @Inject
    public CassandraAttachmentMapper(CassandraAttachmentDAOV2 attachmentDAOV2, BlobStore blobStore, CassandraAttachmentMessageIdDAO attachmentMessageIdDAO, CassandraAttachmentOwnerDAO ownerDAO) {
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobStore = blobStore;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
        this.ownerDAO = ownerDAO;
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return getAttachmentInternal(attachmentId)
            .blockOptional()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    @Override
    public List<AttachmentMetadata> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        return Flux.fromIterable(attachmentIds)
            .flatMap(this::getAttachmentsAsMono, DEFAULT_CONCURRENCY)
            .collect(Guavate.toImmutableList())
            .block();
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId) throws AttachmentNotFoundException, IOException {
        return attachmentDAOV2.getAttachment(attachmentId)
            .map(daoAttachment -> blobStore.read(blobStore.getDefaultBucketName(), daoAttachment.getBlobId(), LOW_COST))
            .blockOptional()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.toString()));
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
    public Mono<AttachmentMetadata> storeAttachmentForOwner(ContentType contentType, InputStream inputStream, Username owner) {
        CurrentPositionInputStream currentPositionInputStream = new CurrentPositionInputStream(inputStream);
        AttachmentId attachmentId = AttachmentId.random();
        return ownerDAO.addOwner(attachmentId, owner)
            .then(Mono.from(blobStore.save(blobStore.getDefaultBucketName(), currentPositionInputStream, LOW_COST)))
                .map(blobId -> new DAOAttachment(attachmentId, blobId, contentType, currentPositionInputStream.getPosition()))
            .flatMap(attachmentDAOV2::storeAttachment)
            .then(Mono.defer(() -> Mono.just(AttachmentMetadata.builder()
                .attachmentId(attachmentId)
                .type(contentType)
                .size(currentPositionInputStream.getPosition())
                .build())));
    }

    @Override
    public List<MessageAttachmentMetadata> storeAttachmentsForMessage(Collection<ParsedAttachment> parsedAttachments, MessageId ownerMessageId) throws MailboxException {
        return Flux.fromIterable(parsedAttachments)
            .concatMap(attachment -> storeAttachmentAsync(attachment, ownerMessageId))
            .collectList()
            .block();
    }

    @Override
    public Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId) throws MailboxException {
        return attachmentMessageIdDAO.getOwnerMessageIds(attachmentId)
            .collect(Guavate.toImmutableList())
            .block();
    }

    @Override
    public Collection<Username> getOwners(AttachmentId attachmentId) throws MailboxException {
        return ownerDAO.retrieveOwners(attachmentId).collect(Guavate.toImmutableList()).block();
    }

    private Mono<MessageAttachmentMetadata> storeAttachmentAsync(ParsedAttachment parsedAttachment, MessageId ownerMessageId) {
        AttachmentId attachmentId = AttachmentId.random();
        byte[] content = parsedAttachment.getContent();
        return Mono.from(blobStore.save(blobStore.getDefaultBucketName(), content, LOW_COST))
            .map(blobId -> new DAOAttachment(attachmentId, blobId, parsedAttachment.getContentType(), content.length))
            .flatMap(daoAttachment -> storeAttachmentWithIndex(daoAttachment, ownerMessageId))
            .then(Mono.defer(() -> Mono.just(parsedAttachment.asMessageAttachment(attachmentId, content.length))));
    }

    private Mono<Void> storeAttachmentWithIndex(DAOAttachment daoAttachment, MessageId ownerMessageId) {
        return attachmentDAOV2.storeAttachment(daoAttachment)
                .then(attachmentMessageIdDAO.storeAttachmentForMessageId(daoAttachment.getAttachmentId(), ownerMessageId));
    }

    private void logNotFound(AttachmentId attachmentId) {
        LOGGER.warn("Failed retrieving attachment {}", attachmentId);
    }
}
