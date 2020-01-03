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

import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2.DAOAttachment;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraAttachmentMapper implements AttachmentMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAttachmentMapper.class);

    private final CassandraAttachmentDAO attachmentDAO;
    private final CassandraAttachmentDAOV2 attachmentDAOV2;
    private final BlobStore blobStore;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;
    private final CassandraAttachmentOwnerDAO ownerDAO;

    @Inject
    public CassandraAttachmentMapper(CassandraAttachmentDAO attachmentDAO, CassandraAttachmentDAOV2 attachmentDAOV2, BlobStore blobStore, CassandraAttachmentMessageIdDAO attachmentMessageIdDAO, CassandraAttachmentOwnerDAO ownerDAO) {
        this.attachmentDAO = attachmentDAO;
        this.attachmentDAOV2 = attachmentDAOV2;
        this.blobStore = blobStore;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
        this.ownerDAO = ownerDAO;
    }

    @Override
    public void endRequest() {
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return getAttachmentInternal(attachmentId)
            .blockOptional()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    private Mono<Attachment> retrievePayload(DAOAttachment daoAttachment) {
        return blobStore.readBytes(blobStore.getDefaultBucketName(), daoAttachment.getBlobId())
            .map(daoAttachment::toAttachment);
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        return Flux.fromIterable(attachmentIds)
            .flatMap(this::getAttachmentsAsMono)
            .collect(Guavate.toImmutableList())
            .block();
    }

    public Mono<Attachment> getAttachmentsAsMono(AttachmentId attachmentId) {
        return getAttachmentInternal(attachmentId)
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> logNotFound((attachmentId))));
    }

    private Mono<Attachment> getAttachmentInternal(AttachmentId id) {
        return attachmentDAOV2.getAttachment(id)
            .flatMap(this::retrievePayload)
            .switchIfEmpty(fallbackToV1(id));
    }

    private Mono<Attachment> fallbackToV1(AttachmentId attachmentId) {
        return attachmentDAO.getAttachment(attachmentId);
    }

    @Override
    public void storeAttachmentForOwner(Attachment attachment, Username owner) throws MailboxException {
        ownerDAO.addOwner(attachment.getAttachmentId(), owner)
            .then(blobStore.save(blobStore.getDefaultBucketName(), attachment.getBytes(), LOW_COST))
            .map(blobId -> CassandraAttachmentDAOV2.from(attachment, blobId))
            .flatMap(attachmentDAOV2::storeAttachment)
            .block();
    }

    @Override
    public void storeAttachmentsForMessage(Collection<Attachment> attachments, MessageId ownerMessageId) throws MailboxException {
        Flux.fromIterable(attachments)
            .flatMap(attachment -> storeAttachmentAsync(attachment, ownerMessageId))
            .then()
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

    public Mono<Void> storeAttachmentAsync(Attachment attachment, MessageId ownerMessageId) {
        return blobStore.save(blobStore.getDefaultBucketName(), attachment.getBytes(), LOW_COST)
            .map(blobId -> CassandraAttachmentDAOV2.from(attachment, blobId))
            .flatMap(daoAttachment -> storeAttachmentWithIndex(daoAttachment, ownerMessageId));
    }

    private Mono<Void> storeAttachmentWithIndex(DAOAttachment daoAttachment, MessageId ownerMessageId) {
        return attachmentDAOV2.storeAttachment(daoAttachment)
                .then(attachmentMessageIdDAO.storeAttachmentForMessageId(daoAttachment.getAttachmentId(), ownerMessageId));
    }

    private void logNotFound(AttachmentId attachmentId) {
        LOGGER.warn("Failed retrieving attachment {}", attachmentId);
    }
}
